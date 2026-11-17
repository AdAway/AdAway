package org.adaway.model.source;

import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.ListType;
import org.adaway.util.RegexUtils;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

import static org.adaway.model.source.SourceLoader.HOSTS_PARSER_PATTERN;
import static org.junit.Assert.*;

public class SourceLoaderTest {

    // Test data comes from Guava InternetDomainName unit test
    // https://github.com/google/guava/blob/master/android/guava-tests/test/com/google/common/net/InternetDomainNameTest.java
    private static final String ALMOST_TOO_MANY_LEVELS = "a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a";
    private static final String ALMOST_TOO_LONG = "aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.a1234567890.c";
    private static final Set<String> VALID_NAMES = new HashSet<>();
    private static final Set<String> INVALID_NAMES = new HashSet<>();
    private static final Set<String> VALID_WILDCARD_NAMES = new HashSet<>();
    private static final Set<String> INVALID_WILDCARD_NAMES = new HashSet<>();

    static {
        VALID_NAMES.add("foo.com");
        VALID_NAMES.add("f-_-o.cOM");
        VALID_NAMES.add("f--1.com");
        VALID_NAMES.add("f11-1.com");
        VALID_NAMES.add("www");
        VALID_NAMES.add("abc.a23");
        VALID_NAMES.add("biz.com.ua");
        VALID_NAMES.add("x");
        VALID_NAMES.add("fOo");
        VALID_NAMES.add("f--o");
        VALID_NAMES.add("f_a");
        VALID_NAMES.add(ALMOST_TOO_MANY_LEVELS);
        VALID_NAMES.add(ALMOST_TOO_LONG);

        INVALID_NAMES.add("");
        INVALID_NAMES.add(" ");
        INVALID_NAMES.add("127.0.0.1");
        INVALID_NAMES.add("::1");
        INVALID_NAMES.add("13");
        INVALID_NAMES.add("abc.12c");
        INVALID_NAMES.add("foo-.com");
        INVALID_NAMES.add("_bar.quux");
        INVALID_NAMES.add("foo+bar.com");
        INVALID_NAMES.add("foo!bar.com");
        INVALID_NAMES.add(".foo.com");
        INVALID_NAMES.add("..bar.com");
        INVALID_NAMES.add("baz..com");
        INVALID_NAMES.add("..quiffle.com");
        INVALID_NAMES.add("fleeb.com..");
        INVALID_NAMES.add(".");
        INVALID_NAMES.add("..");
        INVALID_NAMES.add("...");
        INVALID_NAMES.add("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.com");
        INVALID_NAMES.add(ALMOST_TOO_MANY_LEVELS + ".com");
        INVALID_NAMES.add(ALMOST_TOO_LONG + ".d");

        // The following are generally valid if the wildcards match at least against a single [a-zA-Z],
        // or even [a-zA-Z0-9] in some circumstances.
        // However, keep in mind that they can also match against invalid host names, like when matching a
        // single [-_.] or empty string. But, because this is a white list, this is not an issue for us.
        VALID_WILDCARD_NAMES.add("*.example.com");
        VALID_WILDCARD_NAMES.add("?.example.com");
        VALID_WILDCARD_NAMES.add("*-example.com");
        VALID_WILDCARD_NAMES.add("?-example.com");
        VALID_WILDCARD_NAMES.add("*_example.com");
        VALID_WILDCARD_NAMES.add("?_example.com");
        VALID_WILDCARD_NAMES.add("example.*");
        VALID_WILDCARD_NAMES.add("example.?");
        VALID_WILDCARD_NAMES.add("example-*");
        VALID_WILDCARD_NAMES.add("example-?");
        VALID_WILDCARD_NAMES.add("example_*");
        VALID_WILDCARD_NAMES.add("example_?");
        VALID_WILDCARD_NAMES.add("sub.*.example.com");
        VALID_WILDCARD_NAMES.add("sub.?.example.com");
        VALID_WILDCARD_NAMES.add("f--?.com");
        VALID_WILDCARD_NAMES.add("*");
        VALID_WILDCARD_NAMES.add("?");

        // The following cannot be valid in any circumstance, because they can never match against a valid host name.
        INVALID_WILDCARD_NAMES.add("abc.12*");
        INVALID_WILDCARD_NAMES.add("abc.12?");
        INVALID_WILDCARD_NAMES.add("foo*-.com");
        INVALID_WILDCARD_NAMES.add("foo?-.com");
        INVALID_WILDCARD_NAMES.add("_*bar.quux");
        INVALID_WILDCARD_NAMES.add("_?bar.quux");
        INVALID_WILDCARD_NAMES.add("foo?+*bar.com");
        INVALID_WILDCARD_NAMES.add("foo?!*bar.com");
        INVALID_WILDCARD_NAMES.add(".*foo.com");
        INVALID_WILDCARD_NAMES.add(".?foo.com");
        INVALID_WILDCARD_NAMES.add("..*bar.com");
        INVALID_WILDCARD_NAMES.add("..?bar.com");
        INVALID_WILDCARD_NAMES.add("*..bar.com");
        INVALID_WILDCARD_NAMES.add("?..bar.com");
        INVALID_WILDCARD_NAMES.add("baz*..com");
        INVALID_WILDCARD_NAMES.add("baz?..com");
        INVALID_WILDCARD_NAMES.add("fleeb.com*..");
        INVALID_WILDCARD_NAMES.add("fleeb.com?..");
        INVALID_WILDCARD_NAMES.add("fleeb.com..*");
        INVALID_WILDCARD_NAMES.add("fleeb.com..?");
    }

    @Test(timeout = 5000)
    public void importsEveryAliasForSupportedBlockAddresses() {
        RecordingDao recording = parse(source(false, false),
                "127.0.0.1 first.example second.example\n"
                        + "0.0.0.0 third.example fourth.example\n"
                        + "::1 fifth.example sixth.example");

        assertHosts(recording, "first.example", "second.example", "third.example",
                "fourth.example", "fifth.example", "sixth.example");
        assertItems(recording, ListType.BLOCKED, null);
    }

    @Test(timeout = 5000)
    public void importsAliasesAcrossWhitespaceAndStopsAtFirstComment() {
        RecordingDao recording = parse(source(false, false),
                " \t0.0.0.0\tFirst.example  second.example\tthird.example# ignored.example\n"
                        + "127.0.0.1 fourth.example\t # fifth.example\n"
                        + "  # comment.example\n");

        assertHosts(recording, "First.example", "second.example", "third.example",
                "fourth.example");
        assertItems(recording, ListType.BLOCKED, null);
    }

    @Test(timeout = 5000)
    public void invalidAliasesDoNotDiscardOtherAliases() {
        RecordingDao recording = parse(source(false, false),
                "0.0.0.0 bad!first.example valid.example *.invalid.example final.example\n"
                        + "0.0.0.0 another.example bad!last.example");

        assertHosts(recording, "valid.example", "final.example", "another.example");
        assertItems(recording, ListType.BLOCKED, null);
    }

    @Test(timeout = 5000)
    public void skipsLocalhostWithoutDiscardingAliases() {
        RecordingDao recording = parse(source(false, false),
                "127.0.0.1 localhost ads.example localhost more-ads.example");

        assertHosts(recording, "ads.example", "more-ads.example");
        assertItems(recording, ListType.BLOCKED, null);
    }

    @Test(timeout = 5000)
    public void redirectsEveryValidAliasWhenEnabled() {
        RecordingDao recording = parse(source(false, true),
                "192.0.2.1 first.example *.second.example bad!alias.example third.example\n"
                        + "not-an-ip ignored.example ignored-alias.example");

        assertHosts(recording, "first.example", "*.second.example", "third.example");
        assertItems(recording, ListType.REDIRECTED, "192.0.2.1");
    }

    @Test(timeout = 5000)
    public void ignoresRedirectAliasesWhenDisabled() {
        RecordingDao recording = parse(source(false, false),
                "192.0.2.1 ignored.example ignored-alias.example\n"
                        + "0.0.0.0 blocked.example blocked-alias.example");

        assertHosts(recording, "blocked.example", "blocked-alias.example");
        assertItems(recording, ListType.BLOCKED, null);
    }

    @Test(timeout = 5000)
    public void insertsAliasesAcrossBatchBoundary() {
        StringBuilder line = new StringBuilder("0.0.0.0");
        Set<String> expected = new HashSet<>();
        for (int i = 0; i < 205; i++) {
            String hostname = "alias" + i + ".example";
            line.append(' ').append(hostname);
            expected.add(hostname);
        }
        RecordingDao recording = parse(source(false, false), line.toString());

        assertEquals(expected, hosts(recording));
        assertEquals(205, recording.items.size());
        assertEquals(Arrays.asList(100, 100, 5), recording.batchSizes);
        assertItems(recording, ListType.BLOCKED, null);
    }

    @Test(timeout = 5000)
    public void ordinaryHostsAndDomainOnlyLinesKeepExistingBehavior() {
        RecordingDao recording = parse(source(false, false),
                "127.0.0.1 MixedCase.example\n"
                        + "domain-only.example\n"
                        + "127.0.0.2 unsupported.example\n"
                        + "# comment.example\n");

        assertHosts(recording, "MixedCase.example");
        assertItems(recording, ListType.BLOCKED, null);
    }

    @Test(timeout = 5000)
    public void allowSourcesKeepExistingSingleHostnameBehavior() {
        RecordingDao recording = parse(source(true, false),
                "  Allowed.example  \n"
                        + "*.wildcard.example\n"
                        + "0.0.0.0 first.example second.example\n"
                        + "first.example second.example\n"
                        + "# comment.example\n");

        assertHosts(recording, "Allowed.example", "*.wildcard.example");
        assertItems(recording, ListType.ALLOWED, null);
    }

    private static HostsSource source(boolean allowEnabled, boolean redirectEnabled) {
        HostsSource source = new HostsSource();
        source.setId(42);
        source.setAllowEnabled(allowEnabled);
        source.setRedirectEnabled(redirectEnabled);
        return source;
    }

    private static RecordingDao parse(HostsSource source, String text) {
        RecordingDao recording = new RecordingDao();
        new SourceLoader(source).parse(new BufferedReader(new StringReader(text)), recording.dao);
        assertEquals(Arrays.asList(source.getId()), recording.clearedSources);
        return recording;
    }

    private static Set<String> hosts(RecordingDao recording) {
        Set<String> hosts = new HashSet<>();
        for (HostListItem item : recording.items) {
            hosts.add(item.getHost());
        }
        return hosts;
    }

    private static void assertHosts(RecordingDao recording, String... expected) {
        assertEquals(new HashSet<>(Arrays.asList(expected)), hosts(recording));
        assertEquals(expected.length, recording.items.size());
    }

    private static void assertItems(RecordingDao recording, ListType type, String redirection) {
        for (HostListItem item : recording.items) {
            assertEquals(type, item.getType());
            assertEquals(redirection, item.getRedirection());
            assertEquals(42, item.getSourceId());
            assertTrue(item.isEnabled());
        }
    }

    private static class RecordingDao {
        private final List<HostListItem> items = new ArrayList<>();
        private final List<Integer> batchSizes = new ArrayList<>();
        private final List<Integer> clearedSources = new ArrayList<>();
        private final HostListItemDao dao = (HostListItemDao) Proxy.newProxyInstance(
                HostListItemDao.class.getClassLoader(),
                new Class<?>[]{HostListItemDao.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("clearSourceHosts")) {
                        this.clearedSources.add((Integer) args[0]);
                        return null;
                    }
                    if (method.getName().equals("insert") && args[0] instanceof HostListItem[]) {
                        HostListItem[] batch = (HostListItem[]) args[0];
                        this.batchSizes.add(batch.length);
                        this.items.addAll(Arrays.asList(batch));
                        return null;
                    }
                    throw new AssertionError("Unexpected DAO call: " + method.getName());
                }
        );
    }

    @Test
    public void testHostParser() {
        Matcher matcher = HOSTS_PARSER_PATTERN.matcher("# [mocean.mobi]");
        assertFalse(matcher.matches());
        matcher = HOSTS_PARSER_PATTERN.matcher("127.0.0.1 www.domain.com ## some comments #");
        assertTrue(matcher.matches());
        assertEquals("127.0.0.1", matcher.group(1));
        assertEquals("www.domain.com", matcher.group(2));
        matcher = HOSTS_PARSER_PATTERN.matcher("127.0.0.1 ad.domain.net ## some comments");
        assertTrue(matcher.matches());
        assertEquals("127.0.0.1", matcher.group(1));
        assertEquals("ad.domain.net", matcher.group(2));
    }

    @Test
    public void isValidHostname() {
        for (String validName : VALID_NAMES) {
            assertTrue(
                    "The hostname '" + validName + "' should be valid.",
                    RegexUtils.isValidHostname(validName)
            );
        }
    }

    @Test
    public void isValidWhitelistHostname() {
        for (String validName : VALID_NAMES) {
            assertTrue(
                    "The hostname '" + validName + "' should be a valid white list host name.",
                    RegexUtils.isValidWildcardHostname(validName)
            );
        }
        for (String wildcardName : VALID_WILDCARD_NAMES) {
            assertTrue(
                    "The wildcard hostname '" + wildcardName + "' should be valid.",
                    RegexUtils.isValidWildcardHostname(wildcardName)
            );
        }
    }

    @Test
    public void isInvalidHostname() {
        for (String invalidName : INVALID_NAMES) {
            assertFalse(
                    "The hostname '" + invalidName + "' should not be valid.",
                    RegexUtils.isValidHostname(invalidName)
            );
        }
    }

    @Test
    public void isInvalidWhitelistHostname() {
        for (String invalidName : INVALID_NAMES) {
            assertFalse(
                    "The hostname '" + invalidName + "' should not be valid.",
                    RegexUtils.isValidWildcardHostname(invalidName)
            );
        }
        for (String invalidWildcardName : INVALID_WILDCARD_NAMES) {
            assertFalse(
                    "The wildcard hostname '" + invalidWildcardName + "' should not be valid.",
                    RegexUtils.isValidWildcardHostname(invalidWildcardName)
            );
        }
    }
}
