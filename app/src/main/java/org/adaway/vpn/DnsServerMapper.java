package org.adaway.vpn;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.VpnService;
import android.util.Log;

import org.adaway.helper.PreferenceHelper;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static android.content.Context.CONNECTIVITY_SERVICE;

class DnsServerMapper {
    private static final String TAG = "DnsMapper";

    private final Context context;
    private final List<InetAddress> upstreamDnsServers;

    DnsServerMapper(Context context) {
        this.context = context;
        this.upstreamDnsServers = new ArrayList<>();
    }

    InetAddress configure(VpnService.Builder builder) throws VpnWorker.VpnNetworkException {
        // Get the current DNS servers before starting the VPN
        List<InetAddress> dnsServers = getNetworkDnsServers();
        if (dnsServers.isEmpty()) {
            throw new VpnWorker.VpnNetworkException("No DNS Server");
        }
        Log.i(TAG, "Got DNS servers = " + dnsServers);

        String ipv4Format = getIpv4Format(builder);
        byte[] ipv6Template = hasIpV6Servers(dnsServers) ? getIpv6Format(builder) : null;

        if (ipv4Format == null) {
            Log.w(TAG, "configure: Could not find a prefix to use, directly using DNS servers");
            builder.addAddress("192.168.50.1", 24);
        }

        // Add configured DNS servers
        this.upstreamDnsServers.clear();
        // TODO Custom DNS servers
//        if (config.dnsServers.enabled) {
//            for (Configuration.Item item : config.dnsServers.items) {
//                if (item.state == item.STATE_ALLOW) {
//                    try {
//                        newDNSServer(builder, ipv4Format, ipv6Template, InetAddress.getByName(item.location));
//                    } catch (Exception e) {
//                        Log.e(TAG, "configure: Cannot add custom DNS server", e);
//                    }
//                }
//            }
//        }
        // Add all knows DNS servers
        for (InetAddress addr : dnsServers) {
            try {
                if (addr instanceof Inet4Address && ipv4Format != null) {
                    addIpv4DnsServer(builder, ipv4Format, addr);
                } else if (addr instanceof Inet6Address && ipv6Template != null) {
                    addIpv6DnsServer(builder, ipv6Template, addr);
                }
            } catch (Exception e) {
                Log.e(TAG, "configure: Cannot add server:", e);
            }
        }

        // Return last DNS server added
        return this.upstreamDnsServers.get(this.upstreamDnsServers.size() - 1);
    }

    InetAddress translate(InetAddress fakeDnsAddress) {
        byte[] addr = fakeDnsAddress.getAddress();
        int index = addr[addr.length - 1] - 2;

        if (index >= this.upstreamDnsServers.size()) {
            Log.e(TAG, "handleDnsRequest: Cannot handle packets to " + fakeDnsAddress.getHostAddress() + " - not a valid address for this network");
            return null;
        }

        InetAddress dnsAddress = this.upstreamDnsServers.get(index);
        Log.d(TAG, String.format("handleDnsRequest: Incoming packet to %s AKA %d AKA %s", fakeDnsAddress.getHostAddress(), index, dnsAddress.getHostAddress()));
        return dnsAddress;
    }

    private List<InetAddress> getNetworkDnsServers() {
        ConnectivityManager cm = (ConnectivityManager) this.context.getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) {
            return Collections.emptyList();
        }
        LinkProperties linkProperties = cm.getLinkProperties(cm.getActiveNetwork());
        if (linkProperties == null) {
            return Collections.emptyList();
        }
        return linkProperties.getDnsServers();
    }

    private String getIpv4Format(VpnService.Builder builder) {
        // Determine a prefix we can use. These are all reserved prefixes for example
        // use, so it's possible they might be blocked.
        for (String prefix : new String[]{"192.0.2", "198.51.100", "203.0.113"}) {
            try {
                builder.addAddress(prefix + ".1", 24);
            } catch (IllegalArgumentException e) {
                continue;
            }

            return prefix + ".%d";
        }
        return null;
    }

    private byte[] getIpv6Format(VpnService.Builder builder) {
        // For fancy reasons, this is the 2001:db8::/120 subnet of the /32 subnet reserved for
        // documentation purposes. We should do this differently. Anyone have a free /120 subnet
        // for us to use?
        try {
            byte[] ipv6Template = new byte[]{32, 1, 13, (byte) (184 & 0xFF), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
            InetAddress addr = Inet6Address.getByAddress(ipv6Template);
            Log.d(TAG, "configure: Adding IPv6 address" + addr);
            builder.addAddress(addr, 120);
            return ipv6Template;
        } catch (Exception e) {
            Log.d(TAG, "Failed to add IPv6 address to the VPN interface.", e);
            return null;
        }
    }

    private boolean hasIpV6Servers(Collection<InetAddress> dnsServers) {
        boolean hasIpv6Server = dnsServers.stream()
                .anyMatch(server -> server instanceof Inet6Address);
        boolean hasOnlyOnServer = dnsServers.size() == 1;
        boolean isIpv6Enabled = PreferenceHelper.getEnableIpv6(this.context);
        return (isIpv6Enabled || hasOnlyOnServer) && hasIpv6Server;

        // TODO Custom DNS servers
//        if (config.dnsServers.enabled) {
//            for (Configuration.Item item : config.dnsServers.items) {
//                if (item.state == Configuration.Item.STATE_ALLOW && item.location.contains(":"))
//                    return true;
//            }
//        }
    }

    private void addIpv4DnsServer(android.net.VpnService.Builder builder, String format, InetAddress addr) {
        // Optimally we'd allow either one, but the forwarder checks if upstream size is empty, so
        // we really need to acquire both an ipv6 and an ipv4 subnet.
        this.upstreamDnsServers.add(addr);
        String alias = String.format(format, this.upstreamDnsServers.size() + 1);
        Log.i(TAG, "configure: Adding DNS Server " + addr + " as " + alias);
        builder.addDnsServer(alias);
        builder.addRoute(alias, 32);
    }

    private void addIpv6DnsServer(android.net.VpnService.Builder builder, byte[] ipv6Template, InetAddress addr) throws UnknownHostException {
        // Optimally we'd allow either one, but the forwarder checks if upstream size is empty, so
        // we really need to acquire both an ipv6 and an ipv4 subnet.
        this.upstreamDnsServers.add(addr);
        ipv6Template[ipv6Template.length - 1] = (byte) (this.upstreamDnsServers.size() + 1);
        InetAddress i6addr = Inet6Address.getByAddress(ipv6Template);
        Log.i(TAG, "configure: Adding DNS Server " + addr + " as " + i6addr);
        builder.addDnsServer(i6addr);
    }
}
