package org.adaway.model.source;

import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.ListType;
import org.adaway.util.Log;
import org.adaway.util.RegexUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.db.entity.ListType.REDIRECTED;
import static org.adaway.util.Constants.BOGUS_IPv4;
import static org.adaway.util.Constants.LOCALHOST_HOSTNAME;
import static org.adaway.util.Constants.LOCALHOST_IPv4;
import static org.adaway.util.Constants.LOCALHOST_IPv6;

/**
 * This class is an {@link org.adaway.db.entity.HostsSource} parser.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
class SourceParser {
    private static final String TAG = "SourceParser";
    private static final String HOSTS_PARSER = "^\\s*([^#\\s]+)\\s+([^#\\s]+)\\s*(?:#.*)*$";
    static final Pattern HOSTS_PARSER_PATTERN = Pattern.compile(HOSTS_PARSER);

    private final int sourceId;
    private final boolean parseRedirectedHosts;
    private final List<HostListItem> items;

    SourceParser(HostsSource hostsSource, InputStream inputStream, boolean parseRedirectedHosts) throws IOException {
        this.sourceId = hostsSource.getId();
        this.parseRedirectedHosts = parseRedirectedHosts;
        this.items = parse(inputStream);
    }

    Collection<HostListItem> getItems() {
        return this.items;
    }

    /**
     * Parse hosts source from input stream.
     *
     * @param inputStream The stream to parse hosts from.
     * @return The parsed host list items.
     * @throws IOException If stream can't be read.
     */
    private List<HostListItem> parse(InputStream inputStream) throws IOException {
        List<HostListItem> parsedItems = new LinkedList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String nextLine;
            // use whitelist import pattern
            while ((nextLine = reader.readLine()) != null) {
                Matcher matcher = HOSTS_PARSER_PATTERN.matcher(nextLine);
                if (!matcher.matches()) {
                    Log.d(TAG, "Does not match: " + nextLine);
                    continue;
                }
                // Check IP address validity or while list entry (if allowed)
                String ip = matcher.group(1);
                String hostname = matcher.group(2);
                // Skip localhost name
                if (LOCALHOST_HOSTNAME.equals(hostname)) {
                    continue;
                }
                // check if ip is 127.0.0.1 or 0.0.0.0
                ListType type;
                if (LOCALHOST_IPv4.equals(ip)
                        || BOGUS_IPv4.equals(ip)
                        || LOCALHOST_IPv6.equals(ip)) {
                    type = BLOCKED;
                } else if (this.parseRedirectedHosts) {
                    type = REDIRECTED;
                } else {
                    continue;
                }
                HostListItem item = new HostListItem();
                item.setType(type);
                item.setHost(hostname);
                item.setEnabled(true);
                if (type == REDIRECTED) {
                    item.setRedirection(ip);
                }
                item.setSourceId(this.sourceId);
                parsedItems.add(item);
            }
        }
        return parsedItems.parallelStream()
                .filter(this::isRedirectionValid)
                .filter(this::isHostValid)
                .collect(Collectors.toList());
    }

    private boolean isRedirectionValid(HostListItem item) {
        return item.getType() != REDIRECTED || RegexUtils.isValidIP(item.getRedirection());
    }

    private boolean isHostValid(HostListItem item) {
        return RegexUtils.isValidWildcardHostname(item.getHost());
    }
}
