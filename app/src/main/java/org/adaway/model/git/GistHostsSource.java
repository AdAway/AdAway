package org.adaway.model.git;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

import timber.log.Timber;

/**
 * This class is an utility class to get information from GitHub gist hosting.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
class GistHostsSource extends GitHostsJsonApiSource {
    /**
     * The gist identifier.
     */
    private final String gistIdentifier;

    /**
     * Constructor.
     *
     * @param url The hosts file URL hosted on GitHub gist.
     * @throws MalformedURLException If the URl is not a gist URL.
     */
    GistHostsSource(String url) throws MalformedURLException {
        // Check URL path
        URL parsedUrl = new URL(url);
        String path = parsedUrl.getPath();
        String[] pathParts = path.split("/");
        if (pathParts.length < 2) {
            throw new MalformedURLException("The GitHub gist URL " + url + " is not valid.");
        }
        // Extract gist identifier from path
        this.gistIdentifier = pathParts[2];
    }

    @Override
    protected String getCommitApiUrl() {
        return "https://api.github.com/gists/" + this.gistIdentifier;
    }

    @Nullable
    protected ZonedDateTime parseJsonBody(String body) throws JSONException {
        JSONObject gistObject = new JSONObject(body);
        String dateString = gistObject.getString("updated_at");
        ZonedDateTime date = null;
        try {
            date = ZonedDateTime.parse(dateString);
        } catch (DateTimeParseException exception) {
            Timber.w(exception, "Failed to parse commit date: " + dateString + ".");
        }
        return date;
    }
}
