package org.adaway.model.github;

import androidx.annotation.Nullable;

import java.net.MalformedURLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * This class is an utility class to get information from GitHub hosts source hosting.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public abstract class GithubHostsSource {
    /**
     * The GitHub repository URL.
     */
    private static final String GITHUB_REPO_URL = "https://raw.githubusercontent.com/";
    /**
     * The GitHub gist URL.
     */
    private static final String GITHUB_GIST_URL = "https://gist.githubusercontent.com";
    /**
     * The date format to parse date from API.
     */
    final SimpleDateFormat dateFormat;

    GithubHostsSource() {
        // Define commit date format
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
    }

    /**
     * Check if a hosts file url is hosted on GitHub.
     *
     * @param url The url to check.
     * @return {@code true} if the hosts file is hosted on GitHub, {@code false} otherwise.
     */
    public static boolean isHostedOnGithub(String url) {
        return url.startsWith(GITHUB_REPO_URL) ||
                url.startsWith(GITHUB_GIST_URL);
    }

    /**
     * Get the GitHub hosts source.
     *
     * @param url The URL to get source from.
     * @return The GitHub hosts source.
     * @throws MalformedURLException If the URL is not a GitHub URL or not a supported GitHub URL.
     */
    public static GithubHostsSource getSource(String url) throws MalformedURLException {
        if (url.startsWith(GITHUB_REPO_URL)) {
            return new RepoHostsSource(url);
        } else if (url.startsWith(GITHUB_GIST_URL)) {
            return new GistHostsSource(url);
        } else {
            throw new MalformedURLException("URL is not a supported GitHub URL");
        }
    }

    /**
     * Get last update of the hosts file.
     *
     * @return The last update date, {@code null} if the date could not be retrieved.
     */
    @Nullable
    public abstract Date getLastUpdate();
}
