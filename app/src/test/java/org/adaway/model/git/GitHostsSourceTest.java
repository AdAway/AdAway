package org.adaway.model.git;

import org.junit.Test;

import java.net.MalformedURLException;
import java.time.ZonedDateTime;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class GitHostsSourceTest {

    private static final String GITHUB_HOST = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts";
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
            ZonedDateTime lastUpdate = source.getLastUpdate();
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
            ZonedDateTime lastUpdate = source.getLastUpdate();
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
            ZonedDateTime lastUpdate = source.getLastUpdate();
            assertNotNull("Failed to get last modified date of GitLab host file", lastUpdate);
        } catch (MalformedURLException e) {
            fail(e.getMessage());
        }
    }
}
