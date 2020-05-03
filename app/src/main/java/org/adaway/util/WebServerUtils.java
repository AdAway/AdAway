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
import android.content.res.AssetManager;

import org.adaway.helper.PreferenceHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.adaway.util.ShellUtils.isBundledExecutableRunning;
import static org.adaway.util.ShellUtils.killBundledExecutable;
import static org.adaway.util.ShellUtils.runBundledExecutable;

/**
 * This class is an utility class to control web server execution.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class WebServerUtils {
    private static final String TAG = "WebServer";
    private static final String WEBSERVER_EXECUTABLE = "blank_webserver";

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
        runBundledExecutable(context, WEBSERVER_EXECUTABLE, parameters);
    }

    /**
     * Stop the web server.
     */
    public static void stopWebServer() {
        killBundledExecutable(WEBSERVER_EXECUTABLE);
    }

    /**
     * Checks if web server is running
     *
     * @return <code>true</code> if webs server is running, <code>false</code> otherwise.
     */
    public static boolean isWebServerRunning() {
        return isBundledExecutableRunning(WEBSERVER_EXECUTABLE);
    }


    private static void inflateResources(Context context, Path target) {
        AssetManager assetManager = context.getAssets();
        try {
            inflateResource(assetManager, "localhost.crt", target);
            inflateResource(assetManager, "localhost.key", target);
            inflateResource(assetManager, "icon.svg", target);
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
