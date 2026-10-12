package org.adaway.model.backup;

import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.ListType;
import org.adaway.db.entity.RuleKind;
import org.adaway.util.RegexUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;

/**
 * This class defines user lists and hosts sources file format.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
final class BackupFormat {
    /*
     * Source backup format.
     */
    static final String SOURCES_KEY = "sources";
    static final String SOURCE_LABEL_ATTRIBUTE = "label";
    static final String SOURCE_URL_ATTRIBUTE = "url";
    static final String SOURCE_ENABLED_ATTRIBUTE = "enabled";
    static final String SOURCE_ALLOW_ATTRIBUTE = "allow";
    static final String SOURCE_REDIRECT_ATTRIBUTE = "redirect";
    /*
     * User source backup format.
     */
    static final String BLOCKED_KEY = "blocked";
    static final String ALLOWED_KEY = "allowed";
    static final String REDIRECTED_KEY = "redirected";
    static final String ENABLED_ATTRIBUTE = "enabled";
    static final String HOST_ATTRIBUTE = "host";
    static final String KIND_ATTRIBUTE = "kind";
    static final String REDIRECT_ATTRIBUTE = "redirect";

    BackupFormat() {

    }

    static JSONObject sourceToJson(HostsSource source) throws JSONException {
        JSONObject sourceObject = new JSONObject();
        sourceObject.put(SOURCE_LABEL_ATTRIBUTE, source.getLabel());
        sourceObject.put(SOURCE_URL_ATTRIBUTE, source.getUrl());
        sourceObject.put(SOURCE_ENABLED_ATTRIBUTE, source.isEnabled());
        sourceObject.put(SOURCE_ALLOW_ATTRIBUTE, source.isAllowEnabled());
        sourceObject.put(SOURCE_REDIRECT_ATTRIBUTE, source.isRedirectEnabled());
        return sourceObject;
    }

    static HostsSource sourceFromJson(JSONObject sourceObject) throws JSONException {
        HostsSource source = new HostsSource();
        source.setLabel(sourceObject.getString(SOURCE_LABEL_ATTRIBUTE));
        String url = sourceObject.getString(SOURCE_URL_ATTRIBUTE);
        if (!HostsSource.isValidUrl(url)) {
            throw new JSONException("Invalid source URL: "+url);
        }
        source.setUrl(url);
        source.setEnabled(sourceObject.getBoolean(SOURCE_ENABLED_ATTRIBUTE));
        source.setAllowEnabled(sourceObject.getBoolean(SOURCE_ALLOW_ATTRIBUTE));
        source.setRedirectEnabled(sourceObject.getBoolean(SOURCE_REDIRECT_ATTRIBUTE));
        return source;
    }

    static JSONObject hostToJson(HostListItem host) throws JSONException {
        JSONObject hostObject = new JSONObject();
        hostObject.put(HOST_ATTRIBUTE, host.getHost());
        String redirection = host.getRedirection();
        if (redirection != null && !redirection.isEmpty()) {
            hostObject.put(REDIRECT_ATTRIBUTE, redirection);
        }
        hostObject.put(KIND_ATTRIBUTE, host.getKind().name().toLowerCase(Locale.ROOT));
        hostObject.put(ENABLED_ATTRIBUTE, host.isEnabled());
        return hostObject;
    }

    static HostListItem hostFromJson(JSONObject hostObject, ListType type) throws JSONException {
        HostListItem host = new HostListItem();
        host.setHost(hostObject.getString(HOST_ATTRIBUTE));
        host.setType(type);
        if (hostObject.has(KIND_ATTRIBUTE)) {
            try {
                host.setKind(RuleKind.valueOf(hostObject.getString(KIND_ATTRIBUTE)
                        .trim()
                        .toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                throw new JSONException("Invalid rule kind in backup: " +
                        hostObject.getString(KIND_ATTRIBUTE));
            }
        }
        if (hostObject.has(REDIRECT_ATTRIBUTE)) {
            String redirection = hostObject.getString(REDIRECT_ATTRIBUTE);
            // ATK-23: reject private/reserved IPs as redirect targets (same as SourceLoader check).
            if (!RegexUtils.isValidRedirectIp(redirection)) {
                throw new JSONException("Invalid or private redirect IP in backup: " + redirection);
            }
            host.setRedirection(redirection);
        }
        boolean validHost = type == ListType.ALLOWED && host.getKind() == RuleKind.EXACT
                ? RegexUtils.isValidWildcardHostname(host.getHost())
                : RegexUtils.isValidHostname(host.getHost());
        if (!validHost || host.getHost().length() > 253) {
            throw new JSONException("Invalid hostname in backup: " + host.getHost());
        }
        if (type == ListType.REDIRECTED && host.getRedirection() == null) {
            throw new JSONException("Missing redirect IP in backup.");
        }
        host.setEnabled(hostObject.getBoolean(ENABLED_ATTRIBUTE));
        host.setSourceId(USER_SOURCE_ID);
        return host;
    }
}
