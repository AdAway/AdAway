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

package org.adaway.ui.tcpdump;

import android.content.Context;
import android.support.v4.content.AsyncTaskLoader;

import org.adaway.util.Constants;
import org.adaway.util.Log;
import org.adaway.util.RegexUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A custom Loader that parses log files from tcpdump
 */
class TcpdumpLogLoader extends AsyncTaskLoader<List<String>> {
    /**
     * The application specific cache directory.
     */
    private final File cacheDir;

    /**
     * Constructor.
     *
     * @param context The application context.
     */
    TcpdumpLogLoader(Context context) {
        super(context);
        // Get application cache directory
        this.cacheDir = context.getCacheDir();
    }

    @Override
    public List<String> loadInBackground() {
        // hashset, because every hostname should be contained only once
        Set<String> set = new HashSet<>();

        try {
            File file = new File(this.cacheDir, Constants.TCPDUMP_LOG);
            // open the file for reading
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
                // read every line of the file into the line-variable, one line at a time
                String nextLine;
                String hostname;
                while ((nextLine = reader.readLine()) != null) {
                    Log.d(Constants.TAG, "nextLine: " + nextLine);

                    hostname = RegexUtils.getTcpdumpHostname(nextLine);
                    if (hostname != null) {
                        set.add(hostname);
                    }
                }
            } catch (java.io.FileNotFoundException e) {
                Log.e(Constants.TAG, "Tcpdump log is not existing!", e);
            }
        } catch (IOException e) {
            Log.e(Constants.TAG, "Can not get cache dir", e);
        }

        List<String> list = new ArrayList<>(set);

        // Sort the list.
        Collections.sort(list);

        return list;
    }

    @Override
    protected void onReset() {
        super.onReset();

        // Ensure the loader is stopped
        onStopLoading();
    }

    @Override
    protected void onStartLoading() {
        forceLoad();
    }

    @Override
    protected void onStopLoading() {
        cancelLoad();
    }

    @Override
    public void deliverResult(List<String> data) {
        super.deliverResult(data);
    }
}
