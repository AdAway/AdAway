package org.adaway.model.git;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import timber.log.Timber;

import static java.util.stream.Collectors.joining;

/**
 * This class is an utility class to get information from GitLab hosts source hosting.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class GitLabHostsSource extends GitHostsSource {
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

    /**
     * Get last update of the hosts file.
     *
     * @return The last update date, {@code null} if the date could not be retrieved.
     */
    @Nullable
    public ZonedDateTime getLastUpdate() {
        // Create commit API request URL
        String commitApiUrl = "https://gitlab.com/api/v4/projects/" + this.owner + "%2F" + this.repo
                + "/repository/commits?path=" + this.path + "&ref_name=" + this.ref;
        // Create client and request
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(commitApiUrl).build();
        try (Response execute = client.newCall(request).execute();
             ResponseBody body = execute.body()) {
            return parseJsonBody(body.string());
        } catch (IOException | JSONException exception) {
            Timber.e(exception, "Unable to get commits from API.");
            // Return failed
            return null;
        }
    }

    @Nullable
    private ZonedDateTime parseJsonBody(String body) throws JSONException {
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
