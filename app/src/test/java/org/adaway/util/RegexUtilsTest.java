package org.adaway.util;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class RegexUtilsTest {

    // Test data comes from Guava InternetDomainName unit test
    // https://github.com/google/guava/blob/master/android/guava-tests/test/com/google/common/net/InternetDomainNameTest.java
    private static final String ALMOST_TOO_MANY_LEVELS = "a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a";
    private static final String ALMOST_TOO_LONG = "aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.aaaaa.a1234567890.c";
    private static final Set<String> VALID_NAME = new HashSet<>();
    private static final Set<String> INVALID_NAME = new HashSet<>();

    static {
        VALID_NAME.add("foo.com");
        VALID_NAME.add("f-_-o.cOM");
        VALID_NAME.add("f--1.com");
        VALID_NAME.add("f11-1.com");
        VALID_NAME.add("www");
        VALID_NAME.add("abc.a23");
        VALID_NAME.add("biz.com.ua");
        VALID_NAME.add("x");
        VALID_NAME.add("fOo");
        VALID_NAME.add("f--o");
        VALID_NAME.add("f_a");
        VALID_NAME.add(ALMOST_TOO_MANY_LEVELS);
        VALID_NAME.add(ALMOST_TOO_LONG);

        INVALID_NAME.add("");
        INVALID_NAME.add(" ");
        INVALID_NAME.add("127.0.0.1");
        INVALID_NAME.add("::1");
        INVALID_NAME.add("13");
        INVALID_NAME.add("abc.12c");
        INVALID_NAME.add("foo-.com");
        INVALID_NAME.add("_bar.quux");
        INVALID_NAME.add("foo+bar.com");
        INVALID_NAME.add("foo!bar.com");
        INVALID_NAME.add(".foo.com");
        INVALID_NAME.add("..bar.com");
        INVALID_NAME.add("baz..com");
        INVALID_NAME.add("..quiffle.com");
        INVALID_NAME.add("fleeb.com..");
        INVALID_NAME.add(".");
        INVALID_NAME.add("..");
        INVALID_NAME.add("...");
        INVALID_NAME.add("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.com");
        INVALID_NAME.add(ALMOST_TOO_MANY_LEVELS + ".com");
        INVALID_NAME.add(ALMOST_TOO_LONG + ".c");
    }

    @Test
    public void isValidHostname() {
        for (String validName : VALID_NAME) {
            assertTrue(
                    "The hostname '" + validName + "' should be valid.",
                    RegexUtils.isValidHostname(validName)
            );
        }
    }

    @Test
    public void isInvalidHostname() {
        for (String invalidName : INVALID_NAME) {
            assertFalse(
                    "The hostname '" + invalidName + "' should not be valid.",
                    RegexUtils.isValidHostname(invalidName)
            );
        }
    }
}