package org.adaway.vpn.dns;

import static android.content.Context.CONNECTIVITY_SERVICE;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.VpnService;
import android.util.Log;

import org.adaway.helper.PreferenceHelper;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * This class is in charge of mapping DNS server addresses between network DNS and fake DNS.
 * <p>
 * Fake DNS addresses are registered as VPN interface DNS to capture DNS traffic.
 * Each original DNS server is directly mapped to one fake address.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class DnsServerMapper {
    private static final String TAG = "DnsServerMapper";
    /**
     * The TEST NET addresses blocks, defined in RFC5735.
     */
    private static final String[] TEST_NET_ADDRESS_BLOCKS = {
            "192.0.2.0/24", // TEST-NET-1
            "198.51.100.0/24", // TEST-NET-2
            "203.0.113.0/24" // TEST-NET-3
    };
    /**
     * This IPv6 address prefix for documentation, defined in RFC3849.
     */
    private static final String IPV6_ADDRESS_PREFIX_RESERVED_FOR_DOCUMENTATION = "2001:db8::/32";
    /**
     * VPN network IPv6 interface prefix length.
     */
    private static final int IPV6_PREFIX_LENGTH = 120;
    /**
     * The original DNS servers.
     */
    private final List<InetAddress> dnsServers;

    /**
     * Constructor.
     */
    public DnsServerMapper() {
        this.dnsServers = new ArrayList<>();
    }

    /**
     * Configure the VPN.
     * <p>
     * Add interface address per IP family and fake DNS server per system DNS server.
     *
     * @param context The application context.
     * @param builder The builder of the VPN to configure.
     */
    public void configureVpn(Context context, VpnService.Builder builder) {
        // Get DNS servers
        List<InetAddress> dnsServers = getActiveNetworkDnsServers(context);
        // Configure tunnel network address
        Subnet ipv4Subnet = addIpv4Address(builder);
        Subnet ipv6Subnet = hasIpV6DnsServers(context, dnsServers) ? addIpv6Address(builder) : null;
        // Configure DNS mapping
        this.dnsServers.clear();
        for (InetAddress dnsServer : dnsServers) {
            Subnet subnetForDnsServer = dnsServer instanceof Inet4Address ? ipv4Subnet : ipv6Subnet;
            if (subnetForDnsServer == null) {
                continue;
            }
            this.dnsServers.add(dnsServer);
            int serverIndex = this.dnsServers.size();
            InetAddress dnsAddressAlias = subnetForDnsServer.getAddress(serverIndex);
            Log.i(TAG, "Mapping DNS server " + dnsServer + " as " + dnsAddressAlias);
            builder.addDnsServer(dnsAddressAlias);
            if (dnsServer instanceof Inet4Address) {
                builder.addRoute(dnsAddressAlias, 32);
            }
        }
    }

    public InetAddress getDefaultDnsServerAddress() {
        // Return last DNS server added
        return this.dnsServers.get(this.dnsServers.size() - 1);
    }

    /**
     * Get the original DNS server address from fake DNS server address.
     *
     * @param fakeDnsAddress The fake DNS address to get the original DNS server address.
     * @return The original DNS server address, wrapped into an {@link Optional} or {@link Optional#empty()} if it does not exists.
     */
    Optional<InetAddress> getDnsServerFromFakeAddress(InetAddress fakeDnsAddress) {
        byte[] address = fakeDnsAddress.getAddress();
        int index = address[address.length - 1] - 2;
        if (index < 0 || index >= this.dnsServers.size()) {
            return Optional.empty();
        }
        InetAddress dnsAddress = this.dnsServers.get(index);
        Log.d(TAG, String.format("handleDnsRequest: Incoming packet to %s AKA %d AKA %s", fakeDnsAddress.getHostAddress(), index, dnsAddress.getHostAddress()));
        return Optional.of(dnsAddress);
    }

    /**
     * Get the DNS server addresses of the active network.
     *
     * @param context The application context.
     * @return The DNS server addresses.
     */
    private List<InetAddress> getActiveNetworkDnsServers(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(CONNECTIVITY_SERVICE);
        Network activeNetwork = connectivityManager.getActiveNetwork();
        LinkProperties linkProperties = connectivityManager.getLinkProperties(activeNetwork);
        if (linkProperties == null) {
            throw new IllegalStateException("Active network was unknown.");
        }
        return linkProperties.getDnsServers();
    }

    /**
     * Add IPv4 network address to the VPN.
     *
     * @param builder The build of the VPN to configure.
     * @return The IPv4 address of the VPN network.
     */
    private Subnet addIpv4Address(VpnService.Builder builder) {
        for (String addressBlock : TEST_NET_ADDRESS_BLOCKS) {
            try {
                Subnet subnet = Subnet.parse(addressBlock);
                InetAddress address = subnet.getAddress(0);
                builder.addAddress(address, subnet.prefixLength);
                Log.d(TAG, "Set " + address + " as network address to tunnel interface.");
                return subnet;
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Failed to add " + addressBlock + " network address to tunnel interface.", e);
            }
        }
        throw new IllegalStateException("Failed to add any IPv4 address for TEST-NET to tunnel interface.");
    }

    /**
     * Add IPv6 network address to the VPN.
     *
     * @param builder The build of the VPN to configure.
     * @return The IPv4 address of the VPN network.
     */
    private Subnet addIpv6Address(VpnService.Builder builder) {
        Subnet subnet = Subnet.parse(IPV6_ADDRESS_PREFIX_RESERVED_FOR_DOCUMENTATION);
        builder.addAddress(subnet.address, IPV6_PREFIX_LENGTH);
        return subnet;
    }


    private boolean hasIpV6DnsServers(Context context, Collection<InetAddress> dnsServers) {
        boolean hasIpv6Server = dnsServers.stream()
                .anyMatch(server -> server instanceof Inet6Address);
        boolean hasOnlyOnServer = dnsServers.size() == 1;
        boolean isIpv6Enabled = PreferenceHelper.getEnableIpv6(context);
        return (isIpv6Enabled || hasOnlyOnServer) && hasIpv6Server;
    }
}
