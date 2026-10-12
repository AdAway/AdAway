package org.adaway.model.update;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ReleaseManifestSourceTest {
    @Test
    public void stableFeedUsesForkLatestReleaseAsset() {
        assertEquals("https://github.com/stevesolun/AdAway/releases/latest/download/manifest.json",
                ReleaseManifestSource.STABLE_URL);
    }

    @Test
    public void betaOptInSelectsNewestPublishedManifestWithoutTrustingArrayOrder() throws Exception {
        JSONArray releases = new JSONArray()
                .put(release("v13.5.1", false, "2026-10-01T00:00:00Z"))
                .put(release("v13.5.2b", true, "2026-10-02T00:00:00Z"));
        ReleaseManifestSource source = ReleaseManifestSource.latest(releases, true);
        assertEquals("beta", source.channel);
        assertEquals(url("v13.5.2b"), source.url);
        assertEquals(url("v13.5.1"), ReleaseManifestSource.latest(releases, false).url);
    }

    @Test
    public void betaOptInAlsoAcceptsNewerStableRelease() throws Exception {
        JSONArray releases = new JSONArray()
                .put(release("v13.5.2", false, "2026-10-03T00:00:00Z"))
                .put(release("v13.5.2b", true, "2026-10-02T00:00:00Z"));
        assertEquals("stable", ReleaseManifestSource.latest(releases, true).channel);
    }

    @Test
    public void missingManifestDraftAndForeignAssetFailClosed() throws Exception {
        JSONObject foreign = release("v13.5.2b", true, "2026-10-02T00:00:00Z");
        foreign.getJSONArray("assets").getJSONObject(0).put("browser_download_url",
                "https://github.com/other/repo/releases/download/v13.5.2b/manifest.json");
        JSONArray releases = new JSONArray().put(foreign)
                .put(release("v13.5.3b", true, "2026-10-03T00:00:00Z").put("draft", true))
                .put(release("v13.5.1", false, "2026-10-01T00:00:00Z")
                        .put("assets", new JSONArray()));
        assertNull(ReleaseManifestSource.latest(releases, true));
    }

    @Test
    public void malformedTagOrPublicationTimeIsIgnored() throws Exception {
        JSONArray releases = new JSONArray()
                .put(release("v13.5.1/../../elsewhere", false, "2026-10-01T00:00:00Z"))
                .put(release("v13.5.1", false, "not-a-time"));
        assertNull(ReleaseManifestSource.latest(releases, true));
    }

    private static JSONObject release(String tag, boolean beta, String published) throws Exception {
        return new JSONObject().put("tag_name", tag).put("draft", false)
                .put("prerelease", beta).put("published_at", published)
                .put("assets", new JSONArray().put(new JSONObject()
                        .put("name", "manifest.json").put("browser_download_url", url(tag))));
    }

    private static String url(String tag) {
        return "https://github.com/stevesolun/AdAway/releases/download/" + tag + "/manifest.json";
    }
}
