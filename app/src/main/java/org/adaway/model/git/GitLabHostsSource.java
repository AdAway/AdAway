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
 * This class is an utility class to get information from GitLab hosts source hosting.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class GitLabHostsSource extends GitHostsJsonApiSource {
    /**
     * The GitLab project path, including any subgroups.
     */
    private final String project;
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
        HttpUrl parsedUrl = HttpUrl.parse(url);
        List<String> pathParts = parsedUrl == null ? null : parsedUrl.pathSegments();
        int rawIndex = pathParts == null ? -1 : pathParts.indexOf("-") + 1;
        if (rawIndex == 0) rawIndex = pathParts.indexOf("raw");
        int projectEnd = rawIndex > 0 && pathParts.get(rawIndex - 1).equals("-")
                ? rawIndex - 1 : rawIndex;
        if (pathParts == null || projectEnd < 2 || rawIndex + 2 >= pathParts.size()
                || !pathParts.get(rawIndex).equals("raw") || pathParts.contains("")) {
            throw new MalformedURLException("The GitLab user content URL " + url + " is not valid.");
        }
        // Extract components from path
        this.project = String.join("/", pathParts.subList(0, projectEnd));
        this.ref = pathParts.get(rawIndex + 1);
        this.path = String.join("/", pathParts.subList(rawIndex + 2, pathParts.size()));
    }

    @Override
    protected String getCommitApiUrl() {
        return new HttpUrl.Builder().scheme("https").host("gitlab.com")
                .addPathSegments("api/v4/projects").addPathSegment(this.project)
                .addPathSegments("repository/commits").addQueryParameter("path", this.path)
                .addQueryParameter("ref_name", this.ref).build().toString();
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
