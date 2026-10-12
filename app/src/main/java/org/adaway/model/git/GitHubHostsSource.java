package org.adaway.model.git;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

import okhttp3.HttpUrl;

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
    private final String ref;
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
        HttpUrl parsedUrl = HttpUrl.parse(url);
        List<String> pathParts = parsedUrl == null ? null : parsedUrl.pathSegments();
        if (pathParts == null || pathParts.size() < 4 || pathParts.contains("")) {
            throw new MalformedURLException("The GitHub user content URL " + url + " is not valid.");
        }
        // Extract components from path
        this.owner = pathParts.get(0);
        this.repo = pathParts.get(1);
        int pathStart = 3;
        if (pathParts.size() >= 6 && pathParts.get(2).equals("refs")
                && (pathParts.get(3).equals("heads") || pathParts.get(3).equals("tags"))) {
            pathStart = 5;
        }
        this.ref = String.join("/", pathParts.subList(2, pathStart));
        this.blobPath = String.join("/", pathParts.subList(pathStart, pathParts.size()));
    }

    @Override
    protected String getCommitApiUrl() {
        return new HttpUrl.Builder().scheme("https").host("api.github.com")
                .addPathSegment("repos").addPathSegment(this.owner).addPathSegment(this.repo)
                .addPathSegment("commits").addQueryParameter("per_page", "1")
                .addQueryParameter("sha", this.ref).addQueryParameter("path", this.blobPath)
                .build().toString();
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
