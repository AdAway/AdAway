package org.adaway.model.git;

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
import java.text.SimpleDateFormat;
import java.util.Date;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static java.util.Locale.US;

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
        // Use custom date format
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", US);
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
        this.path = Stream.of(pathParts)
                .skip(5)
                .collect(Collectors.joining("/"));
    }

    /**
     * Get last update of the hosts file.
     *
     * @return The last update date, {@code null} if the date could not be retrieved.
     */
    @Nullable
    public Date getLastUpdate() {
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
            String dateString = commitItemObject.getString("committed_date");
            try {
                date = this.dateFormat.parse(dateString);
            } catch (ParseException exception) {
                Log.w(Constants.TAG, "Failed to parse commit date: " + dateString + ".", exception);
            }
        }
        return date;
    }
}
