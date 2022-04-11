package org.adaway.vpn.dns.proxy;

import static java.util.Objects.requireNonNull;

import android.content.Context;

import org.adaway.AdAwayApplication;
import org.adaway.db.entity.HostEntry;
import org.adaway.db.entity.ListType;
import org.adaway.model.vpn.VpnModel;
import org.adaway.util.AppExecutors;
import org.adaway.vpn.dns.DnsServerMapper;
import org.pcap4j.packet.IpPacket;
import org.pcap4j.packet.IpV4Packet;
import org.pcap4j.packet.IpV6Packet;
import org.pcap4j.packet.UdpPacket;
import org.pcap4j.packet.UnknownPacket;
import org.xbill.DNS.AAAARecord;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Name;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.SOARecord;
import org.xbill.DNS.Section;
import org.xbill.DNS.TextParseException;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executor;

import timber.log.Timber;

public abstract class AbstractDnsPacketProxy implements PacketProxy {
    // Choose a value that is smaller than the time needed to unblock a host.
    protected static final int NEGATIVE_CACHE_TTL_SECONDS = 5;
    protected static final SOARecord NEGATIVE_CACHE_SOA_RECORD;

    protected final Executor executor;
    protected final EventLoop eventLoop;
    protected final DnsServerMapper dnsServerMapper;
    private VpnModel vpnModel;

    static {
        try {
            // Let's use a guaranteed invalid hostname here, clients are not supposed to use
            // our fake values, the whole thing just exists for negative caching.
            Name name = new Name("adaway.vpn.invalid.");
            NEGATIVE_CACHE_SOA_RECORD = new SOARecord(name, DClass.IN, NEGATIVE_CACHE_TTL_SECONDS,
                    name, name, 0, 0, 0, 0, NEGATIVE_CACHE_TTL_SECONDS);
        } catch (TextParseException e) {
            throw new RuntimeException(e);
        }
    }

    public AbstractDnsPacketProxy(EventLoop eventLoop, DnsServerMapper dnsServerMapper) {
        this.executor = AppExecutors.getInstance().networkIO();
        this.eventLoop = eventLoop;
        this.dnsServerMapper = dnsServerMapper;
    }

    @Override
    public void initialize(Context context) {
        this.vpnModel = (VpnModel) ((AdAwayApplication) context.getApplicationContext()).getAdBlockModel();
    }

    @Override
    public void handleDnsRequest(byte[] packetData) throws IOException {
        // Parse packet
        UdpPacketData data = UdpPacketData.tryToParse(packetData);
        if (data == null) {
            return;
        }
        // Get original dns address
        Optional<InetAddress> dnsAddressOptional = this.dnsServerMapper.getDnsServerFromFakeAddress(data.packetAddress);
        if (!dnsAddressOptional.isPresent()) {
            Timber.w("Cannot find mapped DNS for %s.", data.packetAddress.getHostAddress());
            return;
        }
        InetAddress dnsAddress = dnsAddressOptional.get();
        // Firefox workaround
        if (data.dns == null) {
            handleEmptyUdpPacket(data, dnsAddress);
            return;
        }
        // Handle request according to host entry
        HostEntry entry = getHostEntry(data.dns.queryName);
        switch (entry.getType()) {
            case BLOCKED:
                handleBlockedResponse(data);
                break;
            case ALLOWED:
                handleAllowedResponse(data, dnsAddress);
                break;
            case REDIRECTED:
                handleRedirectedResponse(data, entry.getRedirection());
                break;
        }
    }

    private void handleEmptyUdpPacket(UdpPacketData data, InetAddress dnsAddress) throws IOException {
        Timber.i("handleDnsRequest: Sending UDP packet without payload: %s", data.ipPacket.getPayload());
        // Let's be nice to Firefox. Firefox uses an empty UDP packet to
        // the gateway to reduce the RTT. For further details, please see
        // https://bugzilla.mozilla.org/show_bug.cgi?id=888268
        DatagramPacket outPacket = new DatagramPacket(new byte[0], 0, dnsAddress, data.packetPort);
        this.eventLoop.forwardPacket(outPacket);
    }

    protected void handleBlockedResponse(UdpPacketData data) {
        DnsPacketData dnsData = requireNonNull(data.dns);
        Timber.i("handleDnsRequest: DNS Name %s blocked!", dnsData.queryName);
        dnsData.message.getHeader().setFlag(Flags.QR);
        dnsData.message.getHeader().setRcode(Rcode.NOERROR);
        dnsData.message.addRecord(NEGATIVE_CACHE_SOA_RECORD, Section.AUTHORITY);
        handleDnsResponse(data.ipPacket, dnsData.message.toWire());
    }

    protected abstract void handleAllowedResponse(UdpPacketData data, InetAddress dnsAddress) throws IOException;

    protected void handleRedirectedResponse(UdpPacketData data, String redirection) {
        DnsPacketData dnsData = requireNonNull(data.dns);
        Timber.i("handleDnsRequest: DNS Name %s redirected to %s.", dnsData.queryName, redirection);
        dnsData.message.getHeader().setFlag(Flags.QR);
        dnsData.message.getHeader().setFlag(Flags.AA);
        dnsData.message.getHeader().unsetFlag(Flags.RD);
        dnsData.message.getHeader().setRcode(Rcode.NOERROR);
        try {
            Name name = dnsData.message.getQuestion().getName();
            InetAddress address = InetAddress.getByName(redirection);
            Record record;
            if (address instanceof Inet6Address) {
                record = new AAAARecord(name, DClass.IN, NEGATIVE_CACHE_TTL_SECONDS, address);
            } else {
                record = new ARecord(name, DClass.IN, NEGATIVE_CACHE_TTL_SECONDS, address);
            }
            dnsData.message.addRecord(record, Section.ANSWER);
        } catch (UnknownHostException e) {
            Timber.w(e, "Failed to get inet address for host %s.", dnsData.queryName);
        }
        handleDnsResponse(data.ipPacket, dnsData.message.toWire());
    }

    /**
     * Handles a responsePayload from an upstream DNS server
     *
     * @param requestPacket   The original request packet
     * @param responsePayload The payload of the response
     */
    protected void handleDnsResponse(IpPacket requestPacket, byte[] responsePayload) {
        UdpPacket udpOutPacket = (UdpPacket) requestPacket.getPayload();
        UdpPacket.Builder payLoadBuilder = new UdpPacket.Builder(udpOutPacket)
                .srcPort(udpOutPacket.getHeader().getDstPort())
                .dstPort(udpOutPacket.getHeader().getSrcPort())
                .srcAddr(requestPacket.getHeader().getDstAddr())
                .dstAddr(requestPacket.getHeader().getSrcAddr())
                .correctChecksumAtBuild(true)
                .correctLengthAtBuild(true)
                .payloadBuilder(
                        new UnknownPacket.Builder().rawData(responsePayload)
                );

        IpPacket ipOutPacket;
        if (requestPacket instanceof IpV4Packet) {
            ipOutPacket = new IpV4Packet.Builder((IpV4Packet) requestPacket)
                    .srcAddr((Inet4Address) requestPacket.getHeader().getDstAddr())
                    .dstAddr((Inet4Address) requestPacket.getHeader().getSrcAddr())
                    .correctChecksumAtBuild(true)
                    .correctLengthAtBuild(true)
                    .payloadBuilder(payLoadBuilder)
                    .build();

        } else {
            ipOutPacket = new IpV6Packet.Builder((IpV6Packet) requestPacket)
                    .srcAddr((Inet6Address) requestPacket.getHeader().getDstAddr())
                    .dstAddr((Inet6Address) requestPacket.getHeader().getSrcAddr())
                    .correctLengthAtBuild(true)
                    .payloadBuilder(payLoadBuilder)
                    .build();
        }

        this.eventLoop.queueDeviceWrite(ipOutPacket);
    }

    protected HostEntry getHostEntry(String dnsQueryName) {
        String hostname = dnsQueryName.toLowerCase(Locale.ENGLISH);
        HostEntry entry = null;
        if (this.vpnModel != null) {
            entry = this.vpnModel.getEntry(hostname);
        }
        if (entry == null) {
            entry = new HostEntry();
            entry.setHost(hostname);
            entry.setType(ListType.ALLOWED);
        }
        return entry;
    }
}
