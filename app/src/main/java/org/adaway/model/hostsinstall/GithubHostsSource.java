package org.adaway.model.hostsinstall;

import android.support.annotation.Nullable;
import android.util.JsonReader;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.adaway.util.Constants;
import org.adaway.util.Log;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * This class is an utility class to get information from GitHub hosting.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
class GithubHostsSource {
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
    GithubHostsSource(String url) throws MalformedURLException {
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

    /**
     * Check if a hosts file url is hosted on GitHub.
     *
     * @param url The url to check.
     * @return {@code true} if the hosts file is hosted on GitHub, {@code false} otherwise.
     */
    static boolean isHostedOnGithub(String url) {
        return url.startsWith("https://raw.githubusercontent.com/");
    }


    /**
     * Get last update of the hosts file.
     *
     * @return The last update date, {@code null} if the date could not be retrieved.
     */
    @Nullable
    Date getLastUpdate() {
        // Create commit API request URL
        String commitApiUrl = "https://api.github.com/repos/" + this.owner + "/" + this.repo + "/commits?path=" + this.blobPath;
        // Create connection
        HttpURLConnection connection;
        try {
            URL mURL = new URL(commitApiUrl);
            connection = (HttpURLConnection) mURL.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
        } catch (IOException exception) {
            Log.e(Constants.TAG, "Unable to get commits from API.", exception);
            // Return failed
            return null;
        }
        // Read API response
        try (JsonReader reader = new JsonReader(new InputStreamReader(connection.getInputStream()))) {
            Date date = null;
            reader.beginArray();
            if (reader.hasNext()) {
                date = this.parseCommitStruct(reader);
            }
            reader.endArray();
            return date;
        } catch (IOException exception) {
            Log.w(Constants.TAG, "Failed to read GitHub API response: " + commitApiUrl + ".", exception);
            return null;
        } finally {
            connection.disconnect();
        }
    }

    private Date parseCommitStruct(JsonReader reader) throws IOException {
        Date date = null;
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (name.equals("commit")) {
                date = this.parseCommitObject(reader);
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        return date;
    }

    private Date parseCommitObject(JsonReader reader) throws IOException {
        Date date = null;
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (name.equals("committer")) {
                date = this.parseCommitter(reader);
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        return date;
    }

    private Date parseCommitter(JsonReader reader) throws IOException {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        Date date = null;
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (name.equals("date")) {
                String dateString = reader.nextString();
                try {
                    date = simpleDateFormat.parse(dateString);
                } catch (ParseException exception) {
                    Log.w(Constants.TAG, "Failed to parse commit date: " + dateString + ".", exception);
                }
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        return date;
    }
}
