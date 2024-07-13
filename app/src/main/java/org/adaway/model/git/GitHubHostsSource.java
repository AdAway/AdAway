package org.adaway.model.git;

import static java.util.stream.Collectors.joining;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;

import timber.log.Timber;

/**
 * This class is an utility class to get information from GitHub repository hosting.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
class GitHubHostsSource extends GitHostsJsonApiSource {
    /**
     * The GitHub owner name.
     */
    private final String owner;
    /**
     * The GitHub repository name.
     */
    private final String repo;
    /**
     * The GitHub blob (hosts file) path.
     */
    private final String blobPath;

    /**
     * Constructor.
     *
     * @param url The hosts file URL hosted on GitHub.
     * @throws MalformedURLException If the URl is not a GitHub URL.
     */
    GitHubHostsSource(String url) throws MalformedURLException {
        // Check URL path
        URL parsedUrl = new URL(url);
        String path = parsedUrl.getPath();
        String[] pathParts = path.split("/");
        if (pathParts.length < 5) {
            throw new MalformedURLException("The GitHub user content URL " + url + " is not valid.");
        }
        // Extract components from path
        this.owner = pathParts[1];
        this.repo = pathParts[2];
        this.blobPath = Arrays.stream(pathParts)
                .skip(4)
                .collect(joining("/"));
    }

    @Override
    protected String getCommitApiUrl() {
        return "https://api.github.com/repos/" + this.owner + "/" + this.repo +
                "/commits?per_page=1&path=" + this.blobPath;
    }

    @Nullable
    protected ZonedDateTime parseJsonBody(String body) throws JSONException {
        JSONArray commitArray = new JSONArray(body);
        int nbrOfCommits = commitArray.length();
        ZonedDateTime date = null;
        for (int i = 0; i < nbrOfCommits && date == null; i++) {
            JSONObject commitItemObject = commitArray.getJSONObject(i);
            JSONObject commitObject = commitItemObject.getJSONObject("commit");
            JSONObject committerObject = commitObject.getJSONObject("committer");
            String dateString = committerObject.getString("date");
            try {
                date = ZonedDateTime.parse(dateString);
            } catch (DateTimeParseException exception) {
                Timber.w(exception, "Failed to parse commit date: " + dateString + ".");
            }
        }
        return date;
    }
}
