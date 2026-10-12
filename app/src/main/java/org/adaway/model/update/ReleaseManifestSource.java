package org.adaway.model.update;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/** Locates signed manifests; release metadata itself is not trusted update authorization. */
final class ReleaseManifestSource {
    static final String STABLE_URL =
            "https://github.com/stevesolun/AdAway/releases/latest/download/manifest.json";
    static final String RELEASES_URL =
            "https://api.github.com/repos/stevesolun/AdAway/releases?per_page=20";
    private static final String DOWNLOAD_PREFIX =
            "https://github.com/stevesolun/AdAway/releases/download/";

    final String url;
    final String channel;

    ReleaseManifestSource(String url, String channel) {
        this.url = url;
        this.channel = channel;
    }

    static ReleaseManifestSource latest(JSONArray releases, boolean includeBeta) {
        ReleaseManifestSource latest = null;
        Instant latestPublished = Instant.MIN;
        for (int i = 0; i < releases.length(); i++) {
            JSONObject release = releases.optJSONObject(i);
            if (release == null || release.optBoolean("draft", true)) {
                continue;
            }
            boolean beta = release.optBoolean("prerelease", true);
            if (beta && !includeBeta) {
                continue;
            }
            String tag = release.optString("tag_name", "");
            if (!tag.matches("v[0-9][0-9A-Za-z.+_-]*")) {
                continue;
            }
            Instant published;
            try {
                published = Instant.parse(release.optString("published_at", ""));
            } catch (DateTimeParseException exception) {
                continue;
            }
            JSONArray assets = release.optJSONArray("assets");
            if (assets == null || !published.isAfter(latestPublished)) {
                continue;
            }
            String expectedUrl = DOWNLOAD_PREFIX + tag + "/manifest.json";
            for (int j = 0; j < assets.length(); j++) {
                JSONObject asset = assets.optJSONObject(j);
                if (asset != null && "manifest.json".equals(asset.optString("name"))
                        && expectedUrl.equals(asset.optString("browser_download_url"))) {
                    latest = new ReleaseManifestSource(expectedUrl, beta ? "beta" : "stable");
                    latestPublished = published;
                    break;
                }
            }
        }
        return latest;
    }
}
