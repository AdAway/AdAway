package org.adaway.model.git;

import org.junit.Test;

import java.net.MalformedURLException;
import java.util.Date;

import static org.junit.Assert.*;

public class GitHostsSourceTest {

    private static final String GITHUB_HOST = "https://raw.githubusercontent.com/AdAway/AdAway/master/README.md";
    private static final String GIST_HOST = "https://gist.githubusercontent.com/PerfectSlayer/a552900539d10271542063d67424b467/raw/56aabad791fbd085f4b9c5051a1dfa76b9a9d748/hosts";
    private static final String GITLAB_HOST = "https://gitlab.com/quidsup/notrack-blocklists/raw/master/notrack-blocklist.txt";

    @Test
    public void testIsHostedOnGit() {
        assertTrue(GitHostsSource.isHostedOnGit(GITHUB_HOST));
        assertTrue(GitHostsSource.isHostedOnGit(GIST_HOST));
        assertTrue(GitHostsSource.isHostedOnGit(GITLAB_HOST));
    }

    @Test
    public void testGitHubSource() {
        try {
            GitHostsSource source = GitHostsSource.getSource(GITHUB_HOST);
            assertTrue(source instanceof GitHubHostsSource);
            Date lastUpdate = source.getLastUpdate();
            assertNotNull("Failed to get last modified date of GitHub host file", lastUpdate);
        } catch (MalformedURLException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testGistSource() {
        try {
            GitHostsSource source = GitHostsSource.getSource(GIST_HOST);
            assertTrue(source instanceof GistHostsSource);
            Date lastUpdate = source.getLastUpdate();
            assertNotNull("Failed to get last modified date of Gist host file", lastUpdate);
        } catch (MalformedURLException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testGitLabSource() {
        try {
            GitHostsSource source = GitHostsSource.getSource(GITLAB_HOST);
            assertTrue(source instanceof GitLabHostsSource);
            Date lastUpdate = source.getLastUpdate();
            assertNotNull("Failed to get last modified date of GitLab host file", lastUpdate);
        } catch (MalformedURLException e) {
            fail(e.getMessage());
        }
    }
}
