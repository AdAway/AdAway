package org.adaway.model.github;

import androidx.annotation.Nullable;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.adaway.util.Constants;
import org.adaway.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.Date;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * This class is an utility class to get information from GitHub repository hosting.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
class RepoHostsSource extends GithubHostsSource {
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
    RepoHostsSource(String url) throws MalformedURLException {
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
        this.blobPath = Stream.of(pathParts)
                .skip(4)
                .collect(Collectors.joining("/"));
    }

    @Override
    @Nullable
    public Date getLastUpdate() {
        // Create commit API request URL
        String commitApiUrl = "https://api.github.com/repos/" + this.owner + "/" + this.repo + "/commits?path=" + this.blobPath;
        // Create client and request
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(commitApiUrl).build();
        try (Response execute = client.newCall(request).execute()) {
            ResponseBody body = execute.body();
            if (body == null) {
                throw new IOException("Empty body content for URL:" + commitApiUrl);
            }
            return parseJsonBody(body.string());
        } catch (IOException | JSONException exception) {
            Log.e(Constants.TAG, "Unable to get commits from API.", exception);
            // Return failed
            return null;
        }
    }

    @Nullable
    private Date parseJsonBody(String body) throws JSONException {
        JSONArray commitArray = new JSONArray(body);
        int nbrOfCommits = commitArray.length();
        Date date = null;
        for (int i = 0; i < nbrOfCommits && date == null; i++) {
            JSONObject commitItemObject = commitArray.getJSONObject(i);
            JSONObject commitObject = commitItemObject.getJSONObject("commit");
            JSONObject committerObject = commitObject.getJSONObject("committer");
            String dateString = committerObject.getString("date");
            try {
                date = this.dateFormat.parse(dateString);
            } catch (ParseException exception) {
                Log.w(Constants.TAG, "Failed to parse commit date: " + dateString + ".", exception);
            }
        }
        return date;
    }
}
