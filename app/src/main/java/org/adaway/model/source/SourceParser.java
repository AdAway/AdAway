package org.adaway.model.source;

import org.adaway.util.Log;
import org.adaway.util.RegexUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import gnu.trove.map.hash.THashMap;
import gnu.trove.set.hash.THashSet;

import static org.adaway.util.Constants.BOGUS_IPv4;
import static org.adaway.util.Constants.LOCALHOST_HOSTNAME;
import static org.adaway.util.Constants.LOCALHOST_IPv4;
import static org.adaway.util.Constants.LOCALHOST_IPv6;
import static org.adaway.util.RegexUtils.HOSTS_PARSER_PATTERN;

/**
 * This class is an {@link org.adaway.db.entity.HostsSource} parser.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
class SourceParser {
    private static final String TAG = "SourceParser";

    private final Set<String> blockedHosts;
    private final Map<String, String> redirectedHosts;
    private final boolean parseRedirectedHosts;

    SourceParser(
            InputStream inputStream,
            boolean parseRedirectedHosts
    ) throws IOException {
        this.blockedHosts = new THashSet<>();
        this.redirectedHosts = new THashMap<>();
        this.parseRedirectedHosts = parseRedirectedHosts;
        parse(inputStream);
    }

    Set<String> getBlockedHosts() {
        return this.blockedHosts;
    }

    Map<String, String> getRedirectedHosts() {
        return this.redirectedHosts;
    }

    /**
     * Parse hosts source from input stream.
     *
     * @param inputStream The stream to parse hosts from.
     * @throws IOException If stream can't be read.
     */
    private void parse(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            String nextLine;
            // use whitelist import pattern
            while ((nextLine = reader.readLine()) != null) {
                Matcher mHostsParserMatcher = HOSTS_PARSER_PATTERN.matcher(nextLine);
                if (!mHostsParserMatcher.matches()) {
                    Log.d(TAG, "Does not match: " + nextLine);
                    continue;
                }
                // Check IP address validity or while list entry (if allowed)
                String ip = mHostsParserMatcher.group(1);
                if (!RegexUtils.isValidIP(ip)) {
                    Log.d(TAG, "IP address is not valid: " + ip);
                    continue;
                }
                // Check hostname
                String hostname = mHostsParserMatcher.group(2);
                if (!RegexUtils.isValidWildcardHostname(hostname)) {
                    Log.d(TAG, "hostname is not valid: " + hostname);
                    continue;
                }
                // Add valid ip and hostname to the right list
                addToList(ip, hostname);
            }
        }

        // strip localhost entry from blacklist and redirection list
        this.blockedHosts.remove(LOCALHOST_HOSTNAME);
        this.redirectedHosts.remove(LOCALHOST_HOSTNAME);
    }

    private void addToList(String ip, String hostname) {
        // check if ip is 127.0.0.1 or 0.0.0.0
        if (ip.equals(LOCALHOST_IPv4)
                || ip.equals(BOGUS_IPv4)
                || ip.equals(LOCALHOST_IPv6)) {
            this.blockedHosts.add(hostname);
        } else if (this.parseRedirectedHosts) {
            this.redirectedHosts.put(hostname, ip);
        }
    }
}
