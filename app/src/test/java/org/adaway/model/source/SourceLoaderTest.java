package org.adaway.model.source;

import org.adaway.util.RegexUtils;
import org.junit.Test;

import java.util.HashSet;
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
