package org.adaway.vpn.dns

import android.content.Context
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.adaway.AdAwayApplication
import org.adaway.db.entity.HostEntry
import org.adaway.db.entity.ListType
import org.adaway.model.vpn.VpnModel
import org.adaway.vpn.dns.DnsPacketProxy.EventLoop
import org.pcap4j.packet.IpPacket
import org.pcap4j.packet.IpSelector
import org.pcap4j.packet.IpV4Packet
import org.pcap4j.packet.IpV6Packet
import org.pcap4j.packet.UdpPacket
import org.pcap4j.packet.UnknownPacket
import org.pcap4j.packet.namednumber.IpNumber
import org.xbill.DNS.AAAARecord
import org.xbill.DNS.ARecord
import org.xbill.DNS.DClass
import org.xbill.DNS.Flags
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.Rcode
import org.xbill.DNS.Record
import org.xbill.DNS.SOARecord
import org.xbill.DNS.Section
import org.xbill.DNS.TextParseException
import timber.log.Timber
import java.io.IOException
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.Locale

class DohPacketProxy(
    private val eventLoop: EventLoop,
    private val dnsServerMapper: DnsServerMapper
) {
    private var vpnModel: VpnModel? = null
    private lateinit var dnsOverHttps: DnsOverHttps

    fun initialize(context: Context) {
        vpnModel = (context.applicationContext as AdAwayApplication).adBlockModel as VpnModel
        dnsOverHttps = createDnsOverHttps(context)
    }

    private fun createDnsOverHttps(context: Context): DnsOverHttps {
        val dnsClientCache = Cache(context.cacheDir, 10 * 1024 * 1024L)
        val dnsClient = OkHttpClient.Builder().cache(dnsClientCache).build()
        return DnsOverHttps.Builder()
            .client(dnsClient)
            .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
            .bootstrapDnsHosts(getByIp("1.1.1.1"), getByIp("1.0.0.1"))
            .includeIPv6(false)
            .post(true)
            .build()
    }

    fun handleDnsResponse(requestPacket: IpPacket, responsePayload: ByteArray) {
        val udpOutPacket = requestPacket.payload as UdpPacket
        val payloadBuilder = UdpPacket.Builder(udpOutPacket)
            .srcPort(udpOutPacket.header.dstPort)
            .dstPort(udpOutPacket.header.srcPort)
            .srcAddr(requestPacket.header.dstAddr)
            .dstAddr(requestPacket.header.srcAddr)
            .correctChecksumAtBuild(true)
            .correctLengthAtBuild(true)
            .payloadBuilder(UnknownPacket.Builder().rawData(responsePayload))

        val ipOutPacket: IpPacket = if (requestPacket is IpV4Packet) {
            IpV4Packet.Builder(requestPacket)
                .srcAddr(requestPacket.header.dstAddr as Inet4Address)
                .dstAddr(requestPacket.header.srcAddr as Inet4Address)
                .correctChecksumAtBuild(true)
                .correctLengthAtBuild(true)
                .payloadBuilder(payloadBuilder)
                .build()
        } else {
            IpV6Packet.Builder(requestPacket as IpV6Packet)
                .srcAddr(requestPacket.header.dstAddr as Inet6Address)
                .dstAddr(requestPacket.header.srcAddr as Inet6Address)
                .correctLengthAtBuild(true)
                .payloadBuilder(payloadBuilder)
                .build()
        }

        eventLoop.queueDeviceWrite(ipOutPacket)
    }

    @Throws(IOException::class)
    fun handleDnsRequest(packetData: ByteArray) {
        val ipPacket = try {
            IpSelector.newPacket(packetData, 0, packetData.size) as IpPacket
        } catch (exception: Exception) {
            Timber.i(exception, "handleDnsRequest: Discarding invalid IP packet")
            return
        }

        if (ipPacket.header.protocol != IpNumber.UDP) {
            return
        }

        val udpPacket: UdpPacket
        val udpPayload: org.pcap4j.packet.Packet?
        try {
            udpPacket = ipPacket.payload as UdpPacket
            udpPayload = udpPacket.payload
        } catch (exception: Exception) {
            Timber.i(exception, "handleDnsRequest: Discarding unknown packet type %s", ipPacket.header)
            return
        }

        val packetAddress = ipPacket.header.dstAddr
        val packetPort = udpPacket.header.dstPort.valueAsInt()
        val dnsAddressOptional = dnsServerMapper.getDnsServerFromFakeAddress(packetAddress)
        if (!dnsAddressOptional.isPresent) {
            Timber.w("Cannot find mapped DNS for %s.", packetAddress.hostAddress)
            return
        }
        val dnsAddress = dnsAddressOptional.get()

        if (udpPayload == null) {
            Timber.i("handleDnsRequest: Sending UDP packet without payload: %s", udpPacket)
            val outPacket = DatagramPacket(ByteArray(0), 0, 0, dnsAddress, packetPort)
            eventLoop.forwardPacket(outPacket)
            return
        }

        val dnsRawData = udpPayload.rawData
        val dnsMsg = try {
            Message(dnsRawData)
        } catch (exception: IOException) {
            Timber.i(exception, "handleDnsRequest: Discarding non-DNS or invalid packet")
            return
        }
        val question = dnsMsg.question ?: run {
            Timber.i("handleDnsRequest: Discarding DNS packet with no query %s", dnsMsg)
            return
        }
        val name = question.name
        val dnsQueryName = name.toString(true)
        val entry = getHostEntry(dnsQueryName)
        when (entry.type) {
            ListType.BLOCKED -> {
                Timber.i("handleDnsRequest: DNS Name %s blocked!", dnsQueryName)
                dnsMsg.header.setFlag(Flags.QR.toInt())
                dnsMsg.header.rcode = Rcode.NOERROR
                dnsMsg.addRecord(NEGATIVE_CACHE_SOA_RECORD, Section.AUTHORITY)
                handleDnsResponse(ipPacket, dnsMsg.toWire())
            }

            ListType.ALLOWED -> {
                Timber.i("handleDnsRequest: DNS Name %s allowed, sending to %s.", dnsQueryName, dnsAddress)
                SCOPE.launch { queryDohServer(ipPacket, dnsMsg, name) }
            }

            ListType.REDIRECTED -> {
                Timber.i("handleDnsRequest: DNS Name %s redirected to %s.", dnsQueryName, entry.redirection)
                dnsMsg.header.setFlag(Flags.QR.toInt())
                dnsMsg.header.setFlag(Flags.AA.toInt())
                dnsMsg.header.unsetFlag(Flags.RD.toInt())
                dnsMsg.header.rcode = Rcode.NOERROR
                try {
                    val address = InetAddress.getByName(entry.redirection)
                    val dnsRecord: Record = if (address is Inet6Address) {
                        AAAARecord(name, DClass.IN, NEGATIVE_CACHE_TTL_SECONDS.toLong(), address)
                    } else {
                        ARecord(name, DClass.IN, NEGATIVE_CACHE_TTL_SECONDS.toLong(), address)
                    }
                    dnsMsg.addRecord(dnsRecord, Section.ANSWER)
                } catch (exception: UnknownHostException) {
                    Timber.w(exception, "Failed to get inet address for host %s.", dnsQueryName)
                }
                handleDnsResponse(ipPacket, dnsMsg.toWire())
            }
        }
    }

    private fun queryDohServer(ipPacket: IpPacket, dnsMsg: Message, name: Name) {
        val dnsQueryName = name.toString(true)
        val address = try {
            dnsOverHttps.lookup(dnsQueryName).firstOrNull()
        } catch (exception: UnknownHostException) {
            Timber.i(exception, "Failed to query DNS Name %s.", dnsQueryName)
            null
        }

        if (address == null) {
            Timber.i("No address was found for DNS Name %s.", dnsQueryName)
            return
        }

        Timber.i("handleDnsRequest: DNS Name %s redirected to %s.", dnsQueryName, address)
        dnsMsg.header.setFlag(Flags.QR.toInt())
        dnsMsg.header.setFlag(Flags.AA.toInt())
        dnsMsg.header.unsetFlag(Flags.RD.toInt())
        dnsMsg.header.rcode = Rcode.NOERROR
        val dnsRecord: Record = if (address is Inet6Address) {
            AAAARecord(name, DClass.IN, NEGATIVE_CACHE_TTL_SECONDS.toLong(), address)
        } else {
            ARecord(name, DClass.IN, NEGATIVE_CACHE_TTL_SECONDS.toLong(), address)
        }
        dnsMsg.addRecord(dnsRecord, Section.ANSWER)
        handleDnsResponse(ipPacket, dnsMsg.toWire())
    }

    private fun getHostEntry(dnsQueryName: String): HostEntry {
        val hostname = dnsQueryName.lowercase(Locale.ENGLISH)
        val entry = vpnModel?.getEntry(hostname)
        return entry ?: HostEntry().apply {
            host = hostname
            type = ListType.ALLOWED
        }
    }

    companion object {
        private const val NEGATIVE_CACHE_TTL_SECONDS = 5
        private val SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val NEGATIVE_CACHE_SOA_RECORD: SOARecord = try {
            val name = Name("adaway.vpn.invalid.")
            SOARecord(
                name,
                DClass.IN,
                NEGATIVE_CACHE_TTL_SECONDS.toLong(),
                name,
                name,
                0L,
                0L,
                0L,
                0L,
                NEGATIVE_CACHE_TTL_SECONDS.toLong()
            )
        } catch (exception: TextParseException) {
            throw RuntimeException(exception)
        }

        private fun getByIp(host: String): InetAddress {
            return try {
                InetAddress.getByName(host)
            } catch (exception: UnknownHostException) {
                throw RuntimeException(exception)
            }
        }
    }
}
