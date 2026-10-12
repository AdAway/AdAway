package org.adaway.model.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.net.MalformedURLException;

import okhttp3.HttpUrl;

public class GitHostsSourceUrlTest {
    @Test
    public void githubCommitLookupUsesSelectedBranchAndEncodedFilePath() throws Exception {
        GitHostsJsonApiSource source = (GitHostsJsonApiSource) GitHostsSource.getSource(
                "https://raw.githubusercontent.com/owner/project/release/filters/a%26b.txt");
        HttpUrl api = HttpUrl.get(source.getCommitApiUrl());
        assertEquals("release", api.queryParameter("sha"));
        assertEquals("filters/a&b.txt", api.queryParameter("path"));
        assertEquals(3, api.querySize());
    }

    @Test
    public void githubExplicitRefDoesNotBecomePartOfFilePath() throws Exception {
        GitHostsJsonApiSource source = (GitHostsJsonApiSource) GitHostsSource.getSource(
                "https://raw.githubusercontent.com/owner/project/refs/heads/main/hosts");
        HttpUrl api = HttpUrl.get(source.getCommitApiUrl());
        assertEquals("refs/heads/main", api.queryParameter("sha"));
        assertEquals("hosts", api.queryParameter("path"));
    }

    @Test
    public void gitlabModernRawUrlSupportsSubgroups() throws Exception {
        GitHostsJsonApiSource source = (GitHostsJsonApiSource) GitHostsSource.getSource(
                "https://gitlab.com/group/subgroup/project/-/raw/release/filters/a%26b.txt");
        HttpUrl api = HttpUrl.get(source.getCommitApiUrl());
        assertEquals("group/subgroup/project", api.pathSegments().get(3));
        assertEquals("release", api.queryParameter("ref_name"));
        assertEquals("filters/a&b.txt", api.queryParameter("path"));
    }

    @Test
    public void gitlabLegacyRawUrlIsStillSupported() throws Exception {
        GitHostsJsonApiSource source = (GitHostsJsonApiSource) GitHostsSource.getSource(
                "https://gitlab.com/group/project/raw/main/hosts");
        HttpUrl api = HttpUrl.get(source.getCommitApiUrl());
        assertEquals("group/project", api.pathSegments().get(3));
        assertEquals("main", api.queryParameter("ref_name"));
        assertEquals("hosts", api.queryParameter("path"));
    }

    @Test
    public void gistOwnerWithoutIdentifierIsRejectedAsMalformedUrl() {
        try {
            GitHostsSource.getSource("https://gist.githubusercontent.com/owner");
            fail("Missing gist identifier must be rejected");
        } catch (MalformedURLException expected) {
            // Invalid custom URLs must not escape as ArrayIndexOutOfBoundsException.
        }
    }

    @Test
    public void gistLookalikeDomainIsNotClassifiedAsGitHub() {
        assertFalse(GitHostsSource.isHostedOnGit(
                "https://gist.githubusercontent.com.example.test/owner/id/raw/hosts"));
        assertFalse(GitHostsSource.isHostedOnGit(
                "https://gist.githubusercontent.com@example.test/owner/id/raw/hosts"));
    }

    @Test
    public void validGistUrlKeepsItsIdentifier() throws Exception {
        GitHostsJsonApiSource source = (GitHostsJsonApiSource) GitHostsSource.getSource(
                "https://gist.githubusercontent.com/owner/abc123/raw/revision/hosts");
        assertEquals("https://api.github.com/gists/abc123", source.getCommitApiUrl());
    }
}
