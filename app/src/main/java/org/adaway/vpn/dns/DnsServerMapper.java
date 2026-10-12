package org.adaway.vpn.dns;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_VPN;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static java.util.Collections.emptyList;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.VpnService;

import org.adaway.helper.PreferenceHelper;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import timber.log.Timber;

/**
 * This class is in charge of mapping DNS server addresses between network DNS and fake DNS.
 * <p>
 * Fake DNS addresses are registered as VPN interface DNS to capture DNS traffic.
 * Each original DNS server is directly mapped to one fake address.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class DnsServerMapper {
    /**
     * Known DNS-over-HTTPS (DoH) provider IPv4 addresses.
     * Routing these through the VPN forces TCP/443 connections to them to enter the tunnel
     * where they are dropped (DnsPacketProxy only handles UDP/53).
     * Chrome (and other DoH-capable apps) then falls back to system UDP/53 DNS, which IS
     * captured by AdAway's fake-DNS mapping and properly filtered.
     */
    private static final String[] DOH_PROVIDER_IPV4 = {
            "1.1.1.1", "1.0.0.1",              // Cloudflare
            "8.8.8.8", "8.8.4.4",              // Google
            "9.9.9.9", "149.112.112.112",       // Quad9
            "208.67.222.222", "208.67.220.220"  // OpenDNS
    };
    /**
     * Known DNS-over-HTTPS provider IPv6 addresses matching {@link #DOH_PROVIDER_IPV4}.
     */
    private static final String[] DOH_PROVIDER_IPV6 = {
            "2606:4700:4700::1111", "2606:4700:4700::1001", // Cloudflare
            "2001:4860:4860::8888", "2001:4860:4860::8844", // Google
            "2620:fe::fe", "2620:fe::9",                    // Quad9
            "2620:119:35::35", "2620:119:53::53"             // OpenDNS
    };
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
    private final Map<InetAddress, InetAddress> dnsAliases;

    /**
     * Constructor.
     */
    public DnsServerMapper() {
        this.dnsServers = new ArrayList<>();
        this.dnsAliases = new HashMap<>();
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
        List<InetAddress> dnsServers = getNetworkDnsServers(context);
        // Configure tunnel network address
        Subnet ipv4Subnet = addIpv4Address(builder);
        Subnet ipv6Subnet = hasIpV6DnsServers(context, dnsServers) ? addIpv6Address(builder) : null;
        // Configure DNS mapping
        this.dnsServers.clear();
        this.dnsAliases.clear();
        for (InetAddress dnsServer : dnsServers) {
            Subnet subnetForDnsServer = dnsServer instanceof Inet4Address ? ipv4Subnet : ipv6Subnet;
            if (subnetForDnsServer == null) {
                continue;
            }
            this.dnsServers.add(dnsServer);
            int serverIndex = this.dnsServers.size();
            InetAddress dnsAddressAlias = subnetForDnsServer.getAddress(serverIndex);
            this.dnsAliases.put(dnsAddressAlias, dnsServer);
            Timber.i("Mapping DNS server %s as %s.", dnsServer, dnsAddressAlias);
            builder.addDnsServer(dnsAddressAlias);
            builder.addRoute(dnsAddressAlias, dnsServer instanceof Inet4Address ? 32 : 128);
        }
        if (this.dnsServers.isEmpty()) {
            throw new IllegalStateException("No usable upstream DNS servers found.");
        }
        // Block DoH providers so Chrome falls back to system UDP/53 DNS (captured by AdAway)
        addDohBlockRoutes(builder, ipv6Subnet != null);
    }

    /**
     * Add /32 routes for known DoH provider IPs through the VPN tunnel.
     * Since DnsPacketProxy only handles UDP/53, TCP/443 DoH connections entering the tunnel
     * will be silently dropped, forcing browsers to fall back to system UDP/53 DNS.
     */
    private void addDohBlockRoutes(VpnService.Builder builder, boolean includeIpv6) {
        for (DohRoute route : commonDohBlockRoutes(includeIpv6)) {
            builder.addRoute(route.address, route.prefixLength);
            Timber.d("Added DoH block route for %s/%d.",
                    route.address.getHostAddress(), route.prefixLength);
        }
    }

    static List<DohRoute> commonDohBlockRoutes(boolean includeIpv6) {
        List<DohRoute> routes = new ArrayList<>(DOH_PROVIDER_IPV4.length
                + (includeIpv6 ? DOH_PROVIDER_IPV6.length : 0));
        addDohRoutes(routes, DOH_PROVIDER_IPV4, 32);
        if (includeIpv6) {
            addDohRoutes(routes, DOH_PROVIDER_IPV6, 128);
        }
        return routes;
    }

    private static void addDohRoutes(List<DohRoute> routes, String[] addresses, int prefixLength) {
        for (String address : addresses) {
            routes.add(DohRoute.from(address, prefixLength));
        }
    }

    static final class DohRoute {
        final InetAddress address;
        final int prefixLength;

        private DohRoute(InetAddress address, int prefixLength) {
            this.address = address;
            this.prefixLength = prefixLength;
        }

        static DohRoute from(String address, int prefixLength) {
            try {
                return new DohRoute(InetAddress.getByName(address), prefixLength);
            } catch (UnknownHostException exception) {
                throw new IllegalStateException("Invalid hardcoded DoH route: " + address,
                        exception);
            }
        }
    }

    public InetAddress getDefaultDnsServerAddress() {
        if (this.dnsServers.isEmpty()) {
            try {
                return InetAddress.getByName("1.1.1.1");
            } catch (UnknownHostException e) {
                throw new IllegalStateException("Failed to parse hardcoded DNS IP address.", e);
            }
        }
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
        return Optional.ofNullable(this.dnsAliases.get(fakeDnsAddress));
    }

    /**
     * Get the DNS server addresses from the device networks.
     *
     * @param context The application context.
     * @return The DNS server addresses, an empty collection if no network.
     */
    private List<InetAddress> getNetworkDnsServers(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(CONNECTIVITY_SERVICE);
        dumpNetworkInfo(connectivityManager);
        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
            return getAnyNonVpnNetworkDns(connectivityManager);
        } else if (isNotVpnNetwork(connectivityManager, activeNetwork)) {
            Timber.d("Get DNS servers from active network %s", activeNetwork);
            return getNetworkDnsServers(connectivityManager, activeNetwork);
        } else {
            return getDnsFromNonVpnNetworkWithMatchingTransportType(connectivityManager, activeNetwork);
        }
    }

    /**
     * Dump all network properties to logs.
     *
     * @param connectivityManager The connectivity manager.
     */
    private void dumpNetworkInfo(ConnectivityManager connectivityManager) {
        Network activeNetwork = connectivityManager.getActiveNetwork();
        Timber.d("Dumping network and dns configuration:");
        for (Network network : connectivityManager.getAllNetworks()) {
            NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(network);
            boolean cellular = networkCapabilities != null && networkCapabilities.hasTransport(TRANSPORT_CELLULAR);
            boolean wifi = networkCapabilities != null && networkCapabilities.hasTransport(TRANSPORT_WIFI);
            boolean vpn = networkCapabilities != null && networkCapabilities.hasTransport(TRANSPORT_VPN);
            LinkProperties linkProperties = connectivityManager.getLinkProperties(network);
            String dnsList = linkProperties == null ? "none" : linkProperties.getDnsServers()
                    .stream()
                    .map(InetAddress::toString)
                    .collect(Collectors.joining(", "));
            Timber.d(
                    "Network %s %s: %s%s%s with dns %s",
                    network,
                    network.equals(activeNetwork) ? "[default]" : "[other]",
                    cellular ? "cellular" : "",
                    wifi ? "WiFi" : "",
                    vpn ? " VPN" : "",
                    dnsList);
        }
    }

    /**
     * Get the DNS server addresses of any network without VPN capability.
     *
     * @param connectivityManager The connectivity manager.
     * @return The DNS server addresses, an empty collection if no applicable DNS server found.
     */
    private List<InetAddress> getAnyNonVpnNetworkDns(ConnectivityManager connectivityManager) {
        for (Network network : connectivityManager.getAllNetworks()) {
            if (isNotVpnNetwork(connectivityManager, network)) {
                List<InetAddress> dnsServers = getNetworkDnsServers(connectivityManager, network);
                if (!dnsServers.isEmpty()) {
                    Timber.d("Get DNS servers from non VPN network %s", network);
                    return dnsServers;
                }
            }
        }
        return emptyList();
    }

    /**
     * Get the DNS server addresses of a network with the same transport type as the active network except VPN.
     *
     * @param connectivityManager The connectivity manager.
     * @param activeNetwork       The active network to filter similar transport type.
     * @return The DNS server addresses, an empty collection if no applicable DNS server found.
     */
    private List<InetAddress> getDnsFromNonVpnNetworkWithMatchingTransportType(
            ConnectivityManager connectivityManager,
            Network activeNetwork
    ) {
        // Get active network transport
        NetworkCapabilities activeNetworkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
        if (activeNetworkCapabilities == null) {
            return emptyList();
        }
        int activeNetworkTransport = -1;
        if (activeNetworkCapabilities.hasTransport(TRANSPORT_CELLULAR)) {
            activeNetworkTransport = TRANSPORT_CELLULAR;
        } else if (activeNetworkCapabilities.hasTransport(TRANSPORT_WIFI)) {
            activeNetworkTransport = TRANSPORT_WIFI;
        }
        // Check all network to find one without VPN and matching transport
        for (Network network : connectivityManager.getAllNetworks()) {
            NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(network);
            if (networkCapabilities == null) {
                continue;
            }
            if (networkCapabilities.hasTransport(activeNetworkTransport) && !networkCapabilities.hasTransport(TRANSPORT_VPN)) {
                List<InetAddress> dns = getNetworkDnsServers(connectivityManager, network);
                if (!dns.isEmpty()) {
                    Timber.d("Get DNS servers from non VPN matching type network %s", network);
                    return dns;
                }
            }
        }
        return getAnyNonVpnNetworkDns(connectivityManager);
    }

    /**
     * Get the DNS server addresses of a network.
     *
     * @param connectivityManager The connectivity manager.
     * @param network             The network to get DNS server addresses.
     * @return The DNS server addresses, an empty collection if no network.
     */
    private List<InetAddress> getNetworkDnsServers(ConnectivityManager connectivityManager, Network network) {
        LinkProperties linkProperties = connectivityManager.getLinkProperties(network);
        if (linkProperties == null) {
            return emptyList();
        }
        return linkProperties.getDnsServers();
    }

    /**
     * Check a network does not have VPN transport.
     *
     * @param connectivityManager The connectivity manager.
     * @param network             The network to check.
     * @return <code>true</code> if a network is not a VPN, <code>false</code> otherwise.
     */
    private boolean isNotVpnNetwork(ConnectivityManager connectivityManager, Network network) {
        if (network == null) {
            return false;
        }
        NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(network);
        return networkCapabilities != null && !networkCapabilities.hasTransport(TRANSPORT_VPN);
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
                Timber.d("Set %s as IPv4 network address to tunnel interface.", address);
                return subnet;
            } catch (IllegalArgumentException e) {
                Timber.w(e, "Failed to add %s network address to tunnel interface.", addressBlock);
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
        Timber.d("Set %s as IPv6 network address to tunnel interface.", subnet.address);
        return subnet;
    }


    private boolean hasIpV6DnsServers(Context context, Collection<InetAddress> dnsServers) {
        boolean hasIpv6Server = dnsServers.stream()
                .anyMatch(server -> server instanceof Inet6Address);
        boolean isIpv6Enabled = PreferenceHelper.getEnableIpv6(context);
        return shouldUseIpv6(dnsServers, isIpv6Enabled) && hasIpv6Server;
    }

    static boolean shouldUseIpv6(Collection<InetAddress> dnsServers, boolean isIpv6Enabled) {
        boolean hasIpv4Server = dnsServers.stream().anyMatch(Inet4Address.class::isInstance);
        return isIpv6Enabled || !hasIpv4Server;
    }
}
