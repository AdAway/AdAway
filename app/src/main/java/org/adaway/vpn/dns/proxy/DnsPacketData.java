package org.adaway.vpn.dns.proxy;

import androidx.annotation.Nullable;

import org.xbill.DNS.Message;
import org.xbill.DNS.Name;

import java.io.IOException;

import timber.log.Timber;

/**
 * This class is a data store for the parsed DNS packet data.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
class DnsPacketData {
    /**
     * The packet raw data.
     */
    final byte[] rawData;
    /**
     * The parsed DNS message.
     */
    final Message message;
    /**
     * The DNS question name.
     */
    final Name name;
    /**
     * The string representation of the DNS question name.
     */
    final String queryName;

    private DnsPacketData(byte[] rawData, Message message, Name name, String queryName) {
        this.rawData = rawData;
        this.message = message;
        this.name = name;
        this.queryName = queryName;
    }

    /**
     * Try to parse an UDP payload data as a DNS message.
     *
     * @param data The UDP payload data.
     * @return The parsed DNS packet data, <code>null</code> if the data could not be parsed.
     */
    @Nullable // Do not use exception to save performance as it is expected to occasionally fail
    static DnsPacketData tryToParse(byte[] data) {
        try {
            Message dnsMsg = new Message(data);
            if (dnsMsg.getQuestion() == null) {
                Timber.i("handleDnsRequest: Discarding DNS packet with no query %s", dnsMsg);
                return null;
            } else {
                Name name = dnsMsg.getQuestion().getName();
                String dnsQueryName = name.toString(true);
                return new DnsPacketData(data, dnsMsg, name, dnsQueryName);
            }
        } catch (IOException e) {
            Timber.i(e, "handleDnsRequest: Discarding non-DNS or invalid packet");
            return null;
        }
    }
}
