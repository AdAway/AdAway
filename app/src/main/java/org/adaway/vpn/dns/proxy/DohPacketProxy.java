
package org.adaway.vpn.dns.proxy;

import static java.util.Objects.requireNonNull;

import android.content.Context;

import org.adaway.vpn.dns.DnsServerMapper;
import org.pcap4j.packet.IpPacket;
import org.xbill.DNS.AAAARecord;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.Section;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import okhttp3.Cache;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.dnsoverhttps.DnsOverHttps;
import timber.log.Timber;

/**
 * This class is a DNS packet proxy based on a DNS over HTTPS client.
 * Experimental feature:
 * <ul>
 * <li>Comes with an hard-coded client (Cloud Flare).</li>
 * <li>Can only be enable at compile time.</li>
 * </ul>
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class DohPacketProxy extends AbstractDnsPacketProxy {
    private DnsOverHttps dnsOverHttps;

    public DohPacketProxy(EventLoop eventLoop, DnsServerMapper dnsServerMapper) {
        super(eventLoop, dnsServerMapper);
    }

    private static InetAddress getByIp(String host) {
        try {
            return InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            // unlikely
            throw new RuntimeException(e);
        }
    }

    @Override
    public void initialize(Context context) {
        super.initialize(context);
        this.dnsOverHttps = createDnsOverHttps(context);
    }

    private DnsOverHttps createDnsOverHttps(Context context) {
        Cache dnsClientCache = new Cache(context.getCacheDir(), 10 * 1024 * 1024L);
        OkHttpClient dnsClient = new OkHttpClient.Builder().cache(dnsClientCache).build();
        return new DnsOverHttps.Builder()
                .client(dnsClient)
                .url(HttpUrl.get("https://cloudflare-dns.com/dns-query"))
                .bootstrapDnsHosts(getByIp("1.1.1.1"), getByIp("1.0.0.1"))
                .includeIPv6(false)
                .post(true)
                .build();
    }

    @Override
    protected void handleAllowedResponse(UdpPacketData data, InetAddress dnsAddress) {
        requireNonNull(data.dns);
        Timber.i("handleDnsRequest: DNS Name %s allowed, sending to %s.", data.dns.queryName, dnsAddress);
        EXECUTOR.execute(() -> queryDohServer(data.ipPacket, data.dns.message, data.dns.name));
    }

    private void queryDohServer(IpPacket ipPacket, Message dnsMsg, Name name) {
        String dnsQueryName = name.toString(true);
        InetAddress address = null;
        try {
            List<InetAddress> addresses = this.dnsOverHttps.lookup(dnsQueryName);
            if (!addresses.isEmpty()) {
                address = addresses.get(0);
            }
        } catch (UnknownHostException e) {
            Timber.i(e, "Failed to query DNS Name %s.", dnsQueryName);
        }

        if (address == null) {
            Timber.i("No address was found for DNS Name %s.", dnsQueryName);
            return;
        }

        Timber.i("handleDnsRequest: DNS Name %s redirected to %s.", dnsQueryName, address);
        dnsMsg.getHeader().setFlag(Flags.QR);
        dnsMsg.getHeader().setFlag(Flags.AA);
        dnsMsg.getHeader().unsetFlag(Flags.RD);
        dnsMsg.getHeader().setRcode(Rcode.NOERROR);
        Record dnsRecord;
        if (address instanceof Inet6Address) {
            dnsRecord = new AAAARecord(name, DClass.IN, NEGATIVE_CACHE_TTL_SECONDS, address);
        } else {
            dnsRecord = new ARecord(name, DClass.IN, NEGATIVE_CACHE_TTL_SECONDS, address);
        }
        dnsMsg.addRecord(dnsRecord, Section.ANSWER);
        handleDnsResponse(ipPacket, dnsMsg.toWire());
    }
}
