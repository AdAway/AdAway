package org.adaway.vpn.dns;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Field;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Map;

public class DnsServerMapperTest {
    @Test
    @SuppressWarnings("unchecked")
    public void aliasesMustMatchTheWholeAddress() throws Exception {
        DnsServerMapper mapper = new DnsServerMapper();
        Field field = DnsServerMapper.class.getDeclaredField("dnsAliases");
        field.setAccessible(true);
        Map<InetAddress, InetAddress> aliases =
                (Map<InetAddress, InetAddress>) field.get(mapper);
        InetAddress upstream = InetAddress.getByName("8.8.8.8");
        InetAddress alias = InetAddress.getByName("192.0.2.2");
        aliases.put(alias, upstream);
        assertEquals(upstream, mapper.getDnsServerFromFakeAddress(alias).get());
        assertFalse(mapper.getDnsServerFromFakeAddress(
                InetAddress.getByName("198.51.100.2")).isPresent());
        assertFalse(mapper.getDnsServerFromFakeAddress(
                InetAddress.getByName("2001:db8::2")).isPresent());
    }

    @Test
    public void multipleIpv6OnlyResolversRemainUsableWithIpv6PreferenceOff() throws Exception {
        assertTrue(DnsServerMapper.shouldUseIpv6(Arrays.asList(
                InetAddress.getByName("2001:db8::53"),
                InetAddress.getByName("2001:db8::54")), false));
        assertFalse(DnsServerMapper.shouldUseIpv6(Arrays.asList(
                InetAddress.getByName("192.0.2.53"),
                InetAddress.getByName("2001:db8::53")), false));
    }
}
