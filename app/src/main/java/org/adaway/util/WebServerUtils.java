/*
 * Copyright (C) 2011-2012 Dominik Schürmann <dominik@dominikschuermann.de>
 *
 * This file is part of AdAway.
 *
 * AdAway is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AdAway is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AdAway.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.adaway.util;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.security.KeyChain;

import androidx.annotation.StringRes;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.net.ssl.SSLHandshakeException;
import javax.security.cert.CertificateException;
import javax.security.cert.X509Certificate;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static org.adaway.util.ShellUtils.isBundledExecutableRunning;
import static org.adaway.util.ShellUtils.killBundledExecutable;
import static org.adaway.util.ShellUtils.runBundledExecutable;

/**
 * This class is an utility class to control web server execution.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class WebServerUtils {
    public static final String TEST_URL = "https://localhost/internal-test";
    private static final String TAG = "WebServer";
    private static final String WEB_SERVER_EXECUTABLE = "webserver";

    /**
     * Start the web server in new thread with RootTools
     *
     * @param context The application context.
     */
    public static void startWebServer(Context context) {
        Log.d(TAG, "Starting web server...");

        Path resourcePath = context.getFilesDir().toPath().resolve("webserver");
        inflateResources(context, resourcePath);

        String parameters = "--resources " + resourcePath.toAbsolutePath() +
                (PreferenceHelper.getWebServerIcon(context) ? " --icon" : "") +
                " > /dev/null 2>&1";
        runBundledExecutable(context, WEB_SERVER_EXECUTABLE, parameters);
    }

    /**
     * Stop the web server.
     */
    public static void stopWebServer() {
        killBundledExecutable(WEB_SERVER_EXECUTABLE);
    }

    /**
     * Checks if web server is running
     *
     * @return <code>true</code> if webs server is running, <code>false</code> otherwise.
     */
    public static boolean isWebServerRunning() {
        return isBundledExecutableRunning(WEB_SERVER_EXECUTABLE);
    }

    /**
     * Get the web server state description.
     *
     * @return The web server state description.
     */
    @StringRes
    public static int getWebServerState() {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(TEST_URL)
                .build();
        try (Response response = client.newCall(request).execute()) {
            return response.isSuccessful() ?
                    R.string.pref_webserver_state_running_and_installed :
                    R.string.pref_webserver_state_not_running;
        } catch (SSLHandshakeException e) {
            return R.string.pref_webserver_state_running_not_installed;
        } catch (ConnectException e) {
            return R.string.pref_webserver_state_not_running;
        } catch (IOException e) {
            Log.w(TAG, "Failed to test web server.", e);
            return R.string.pref_webserver_state_not_running;
        }
    }

    /**
     * Prompt user to install web server certificate.
     *
     * @param context The application context.
     */
    public static void installCertificate(Context context) {
        AssetManager assetManager = context.getAssets();
        try (InputStream inputStream = assetManager.open("localhost.crt");
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            byte[] bytes = outputStream.toByteArray();
            X509Certificate x509 = X509Certificate.getInstance(bytes);
            Intent intent = KeyChain.createInstallIntent();
            intent.putExtra(KeyChain.EXTRA_CERTIFICATE, x509.getEncoded());
            intent.putExtra(KeyChain.EXTRA_NAME, "AdAway");
            context.startActivity(intent);
        } catch (IOException e) {
            Log.w(TAG, "Failed to read certificate.", e);
        } catch (CertificateException e) {
            Log.w(TAG, "Failed to parse certificate.", e);
        }
    }

    private static void inflateResources(Context context, Path target) {
        AssetManager assetManager = context.getAssets();
        try {
            inflateResource(assetManager, "localhost.crt", target);
            inflateResource(assetManager, "localhost.key", target);
            inflateResource(assetManager, "icon.svg", target);
            inflateResource(assetManager, "test.html", target);
        } catch (IOException e) {
            Log.w(TAG, "Failed to inflate web server resources.", e);
        }
    }

    private static void inflateResource(AssetManager assetManager, String resource, Path target) throws IOException {
        if (!Files.isDirectory(target)) {
            Files.createDirectories(target);
        }
        Path targetFile = target.resolve(resource);
        if (!Files.isRegularFile(targetFile)) {
            Files.copy(assetManager.open(resource), targetFile);
        }
    }
}
