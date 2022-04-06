package org.adaway.model.git;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.net.MalformedURLException;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;

/**
 * This class tests the git hosted source behavior.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
@RunWith(Parameterized.class)
public class GitHostsSourceTest {

    private static final String GITHUB_HOST = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts";
    private static final String GIST_HOST = "https://gist.githubusercontent.com/PerfectSlayer/a552900539d10271542063d67424b467/raw/56aabad791fbd085f4b9c5051a1dfa76b9a9d748/hosts";
    private static final String GITLAB_HOST = "https://gitlab.com/quidsup/notrack-blocklists/raw/master/notrack-blocklist.txt";

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {"GitHub", GITHUB_HOST, GitHubHostsSource.class},
                {"Gist", GIST_HOST, GistHostsSource.class},
                {"GitLab", GITLAB_HOST, GitLabHostsSource.class}
        });
    }

    private final String label;
    private final String url;
    private final Class<?> expectedClass;

    /**
     * Constructor.
     *
     * @param label         The Git hosting label.
     * @param url           A git hosted file URL.
     * @param expectedClass The expected git hosting strategy.
     */
    public GitHostsSourceTest(String label, String url, Class<?> expectedClass) {
        this.label = label;
        this.url = url;
        this.expectedClass = expectedClass;
    }

    /**
     * Check the source is well detected as git hasted.
     */
    @Test
    public void testIsHostedOnGit() {
        assertTrue(
                "Git hosting for " + this.label + " was not detected",
                GitHostsSource.isHostedOnGit(this.url)
        );
    }

    /**
     * Check to retrieve the last update date.
     * Requires a network connection to the source.
     */
    @Test
    public void testLastUpdateFetch() {
        try {
            GitHostsSource source = GitHostsSource.getSource(this.url);
            assertTrue(
                    "Invalid git hosting strategy for " + this.label,
                    this.expectedClass.isInstance(source)
            );
            ZonedDateTime lastUpdate = source.getLastUpdate();
            assertNotNull(
                    "Failed to get last modified date of " + this.label + " host file",
                    lastUpdate
            );
        } catch (MalformedURLException e) {
            fail(e.getMessage());
        }
    }
}
