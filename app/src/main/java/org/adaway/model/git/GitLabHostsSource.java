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
 * This class is an utility class to get information from GitLab hosts source hosting.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class GitLabHostsSource extends GitHostsJsonApiSource {
    /**
     * The GitHub owner name.
     */
    private final String owner;
    /**
     * The GitHub repository name.
     */
    private final String repo;
    /**
     * The GitLab reference name.
     */
    private final String ref;
    /**
     * The GitLab (hosts) file path.
     */
    private final String path;

    GitLabHostsSource(String url) throws MalformedURLException {
        // Check URL path
        URL parsedUrl = new URL(url);
        String path = parsedUrl.getPath();
        String[] pathParts = path.split("/");
        if (pathParts.length < 5) {
            throw new MalformedURLException("The GitLab user content URL " + url + " is not valid.");
        }
        // Extract components from path
        this.owner = pathParts[1];
        this.repo = pathParts[2];
        this.ref = pathParts[4];
        this.path = Arrays.stream(pathParts)
                .skip(5)
                .collect(joining("/"));
    }

    @Override
    protected String getCommitApiUrl() {
        return "https://gitlab.com/api/v4/projects/" + this.owner + "%2F" + this.repo
                + "/repository/commits?path=" + this.path + "&ref_name=" + this.ref;
    }

    @Nullable
    protected ZonedDateTime parseJsonBody(String body) throws JSONException {
        JSONArray commitArray = new JSONArray(body);
        int nbrOfCommits = commitArray.length();
        ZonedDateTime date = null;
        for (int i = 0; i < nbrOfCommits && date == null; i++) {
            JSONObject commitItemObject = commitArray.getJSONObject(i);
            String dateString = commitItemObject.getString("committed_date");
            try {
                date = ZonedDateTime.parse(dateString);
            } catch (DateTimeParseException exception) {
                Timber.w(exception, "Failed to parse commit date: " + dateString + ".");
            }
        }
        return date;
    }
}
