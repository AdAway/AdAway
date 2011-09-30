/*
 * Copyright (C) 2011 Dominik Schürmann <dominik@dominikschuermann.de>
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

import java.io.IOException;
import java.util.List;

import org.adaway.R;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.stericson.RootTools.RootTools;
import com.stericson.RootTools.RootToolsException;

public class WebserverUtils {

    /**
     * Install Webserver in /data/data/org.adaway/files if not already there
     * 
     * @param context
     * @throws RemountException
     */
    public static void installWebserver(Context context) {
        if (RootTools
                .installBinary(context, R.raw.mongoose, Constants.WEBSERVER_EXECUTEABLE, "777")) {
            Log.i(Constants.TAG, "Installed webserver if not already existing.");
        } else {
            Log.e(Constants.TAG, "Webserver could not be installed.");
        }
    }

    /**
     * Start Webserver in AsyncTask, because it is blocking with ouput
     * 
     * @param context
     * @throws CommandException
     */
    public static void startWebserver(final Context context) {
        AsyncTask<Void, String, Void> mWebserverTask = new AsyncTask<Void, String, Void>() {
            private String mCommandStartWebserver;

            @Override
            protected Void doInBackground(Void... unused) {
                List<String> output = null;
                try {
                    output = RootTools.sendShell(new String[] { mCommandStartWebserver }, 1);

                    Log.d(Constants.TAG, "output of sendShell commands: " + output.toString());
                } catch (IOException e) {
                    Log.e(Constants.TAG, "Exception: " + e);
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    Log.e(Constants.TAG, "Exception: " + e);
                    e.printStackTrace();
                } catch (RootToolsException e) {
                    Log.e(Constants.TAG, "Exception: " + e);
                    e.printStackTrace();
                }

                return null;
            }

            @Override
            protected void onPreExecute() {
                super.onPreExecute();

                String privateFilesPath = null;
                String privateCachePath = null;
                try {
                    // /data/data/org.adaway/files
                    privateFilesPath = context.getFilesDir().getCanonicalPath();
                    // /data/data/org.adaway/cache
                    privateCachePath = context.getCacheDir().getCanonicalPath();
                } catch (IOException e) {
                    Log.e(Constants.TAG,
                            "Problem occured while trying to locate private files and cache directories!");
                    e.printStackTrace();
                }

                // bind on loopback with ports 80 and 443, without log and webserver root to
                // /data/data/org.adaway/cache
                mCommandStartWebserver = privateFilesPath + Constants.FILE_SEPERATOR
                        + Constants.WEBSERVER_EXECUTEABLE + " -r " + privateCachePath
                        + Constants.FILE_SEPERATOR + " -p 127.0.0.1:80,443";
            }

            @Override
            protected void onPostExecute(Void unused) {
                super.onPostExecute(unused);

            }
        };

        mWebserverTask.execute();

        Toast.makeText(context, context.getString(R.string.button_webserver_toggle_checked), 3)
                .show();
    }

    /**
     * Stop Webserver
     * 
     * @param context
     * @throws CommandException
     */
    public static void stopWebserver(Context context) {

        String commandPidOf = Constants.COMMAND_PIDOF + " " + Constants.WEBSERVER_EXECUTEABLE;

        String pid = null;

        List<String> output = null;
        try {
            output = RootTools.sendShell(new String[] { commandPidOf }, 1);

            Log.d(Constants.TAG, "output of sendShell commands: " + output.toString());

            if (output.size() > 0) {
                pid = output.get(0);
            }
        } catch (IOException e) {
            Log.e(Constants.TAG, "Exception: " + e);
            e.printStackTrace();
        } catch (InterruptedException e) {
            Log.e(Constants.TAG, "Exception: " + e);
            e.printStackTrace();
        } catch (RootToolsException e) {
            Log.e(Constants.TAG, "Exception: " + e);
            e.printStackTrace();
        }

        if (pid != null) {
            String commandKill = Constants.COMMAND_KILL + " " + pid;

            try {
                output = RootTools.sendShell(new String[] { commandKill }, 1);

            } catch (IOException e) {
                Log.e(Constants.TAG, "Exception: " + e);
                e.printStackTrace();
            } catch (InterruptedException e) {
                Log.e(Constants.TAG, "Exception: " + e);
                e.printStackTrace();
            } catch (RootToolsException e) {
                Log.e(Constants.TAG, "Exception: " + e);
                e.printStackTrace();
            }
        }

        Toast.makeText(context, context.getString(R.string.button_webserver_toggle_unchecked), 3)
                .show();
    }

    /**
     * Checks if webserver is running by checking if process mongoose has pid
     * 
     * @return true if webserver is running
     */
    public static boolean isWebserverRunning() {
        boolean isRunning = false;

        String commandPidOf = Constants.COMMAND_PIDOF + " " + Constants.WEBSERVER_EXECUTEABLE;

        List<String> output = null;
        try {
            output = RootTools.sendShell(new String[] { commandPidOf }, 1);

            Log.d(Constants.TAG, "output of sendShell commands: " + output.toString());

            if (output.size() > 0) {
                isRunning = true;
            }
        } catch (IOException e) {
            Log.e(Constants.TAG, "Exception: " + e);
            e.printStackTrace();
        } catch (InterruptedException e) {
            Log.e(Constants.TAG, "Exception: " + e);
            e.printStackTrace();
        } catch (RootToolsException e) {
            Log.e(Constants.TAG, "Exception: " + e);
            e.printStackTrace();
        }

        return isRunning;
    }

    /**
     * Set ToggleButton checked if webserver is running
     * 
     * @param webserverToggle
     */
    public static void setWebserverToggle(ToggleButton webserverToggle) {
        if (isWebserverRunning()) {
            webserverToggle.setChecked(true);
        } else {
            webserverToggle.setChecked(false);
        }
    }
}
