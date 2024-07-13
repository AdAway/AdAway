package org.adaway.model.git;

import androidx.annotation.Nullable;

import org.json.JSONException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.ZonedDateTime;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import timber.log.Timber;

/**
 * This class is an utility class to get information from Git hosted hosts sources from JSON API.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public abstract class GitHostsJsonApiSource extends GitHostsSource {
    @Override
    @Nullable
    public ZonedDateTime getLastUpdate() {
        return getLastUpdateFromApi(getCommitApiUrl());
    }

    protected abstract String getCommitApiUrl();

    @Nullable
    protected ZonedDateTime getLastUpdateFromApi(String commitApiUrl) {
        // Create client and request
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(commitApiUrl).build();
        try (Response response = client.newCall(request).execute();
             ResponseBody body = response.body()) {
            if (response.isSuccessful()) {
                return parseJsonBody(body.string());
            }
        } catch (UnknownHostException | SocketTimeoutException exception) {
            Timber.i(exception, "Unable to reach API backend.");
        } catch (IOException | JSONException exception) {
            Timber.e(exception, "Unable to get commits from API.");
        }
        // Return failed
        return null;
    }

    @Nullable
    protected abstract ZonedDateTime parseJsonBody(String body) throws JSONException;
}
