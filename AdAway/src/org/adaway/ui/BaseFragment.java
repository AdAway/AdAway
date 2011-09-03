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

package org.adaway.ui;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import org.adaway.R;
import org.adaway.helper.ApplyHelper;
import org.adaway.helper.DatabaseHelper;
import org.adaway.helper.Helper;
import org.adaway.helper.PreferencesHelper;
import org.adaway.util.CommandException;
import org.adaway.util.Constants;
import org.adaway.util.HostsParser;
import org.adaway.util.NotEnoughSpaceException;
import org.adaway.util.RemountException;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.Menu;
import android.support.v4.view.MenuItem;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.graphics.Bitmap;

import com.stericson.RootTools.RootTools;

public class BaseFragment extends Fragment {
    private Activity mActivity;
    private DatabaseHelper mDatabaseHelper;

    private ReturnCode mStatus;
    private TextView mStatusText;
    private TextView mStatusSubtitle;
    private ProgressBar mStatusProgress;
    private ImageView mStatusIcon;
    AsyncTask<String, Integer, Enum<ReturnCode>> mStatusTask;

    // donation loading
    private FrameLayout mLoadingFrame;
    private WebView mFlattrWebview;

    // return codes of AsycTasks
    public enum ReturnCode {
        CHECKING, SUCCESS, PRIVATE_FILE_FAIL, UPDATE_AVAILABLE, ENABLED, DISABLED, DOWNLOAD_FAIL, NO_CONNECTION, APPLY_FAIL, SYMLINK_MISSING, NOT_ENOUGH_SPACE, REMOUNT_FAIL, COPY_FAIL
    }

    private void setStatusUpdateAvailable() {
        mStatus = ReturnCode.UPDATE_AVAILABLE;

        mStatusIcon.setVisibility(View.VISIBLE);
        mStatusIcon.setImageResource(R.drawable.status_update);
        mStatusText.setText(R.string.status_update_available);
        mStatusSubtitle.setText(R.string.status_update_available_subtitle);
    }

    private void setStatusEnabled() {
        mStatus = ReturnCode.ENABLED;

        mStatusIcon.setVisibility(View.VISIBLE);
        mStatusIcon.setImageResource(R.drawable.status_enabled);
        mStatusText.setText(R.string.status_enabled);
        mStatusSubtitle.setText(R.string.status_enabled_subtitle);
    }

    private void setStatusDisabled() {
        mStatus = ReturnCode.DISABLED;

        mStatusIcon.setVisibility(View.VISIBLE);
        mStatusIcon.setImageResource(R.drawable.status_disabled);
        mStatusText.setText(R.string.status_disabled);
        mStatusSubtitle.setText(R.string.status_disabled_subtitle);
    }

    private void setStatusDownloadFail(String currentURL) {
        mStatus = ReturnCode.DOWNLOAD_FAIL;

        mStatusIcon.setImageResource(R.drawable.status_fail);
        mStatusIcon.setVisibility(View.VISIBLE);
        mStatusText.setText(R.string.status_download_fail);
        mStatusSubtitle.setText(getString(R.string.status_download_fail_subtitle) + " "
                + currentURL);
    }

    private void setStatusNoConnection() {
        mStatus = ReturnCode.NO_CONNECTION;

        mStatusIcon.setImageResource(R.drawable.status_fail);
        mStatusIcon.setVisibility(View.VISIBLE);
        mStatusText.setText(R.string.status_no_connection);
        mStatusSubtitle.setText(R.string.status_no_connection_subtitle);
    }

    private void setStatusChecking() {
        mStatus = ReturnCode.CHECKING;

        mStatusProgress.setVisibility(View.VISIBLE);
        mStatusText.setText(R.string.status_checking);
        mStatusSubtitle.setText(R.string.status_checking_subtitle);
    }

    /**
     * Override onDestroy to cancel AsyncTask that checks for updates
     */
    @Override
    public void onDestroy() {
        super.onDestroy();

        // cancel task
        if (mStatusTask != null) {
            mStatusTask.cancel(true);
        }
    }

    /**
     * Don't recreate activity on orientation change, it will break AsyncTask. Using possibility 4
     * from http://blog.doityourselfandroid
     * .com/2010/11/14/handling-progress-dialogs-and-screen-orientation-changes/
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mActivity.setContentView(R.layout.base);

        mStatusText = (TextView) mActivity.findViewById(R.id.status_text);
        mStatusSubtitle = (TextView) mActivity.findViewById(R.id.status_subtitle);
        mStatusProgress = (ProgressBar) mActivity.findViewById(R.id.status_progress);
        mStatusIcon = (ImageView) mActivity.findViewById(R.id.status_icon);

        // build old status
        switch (mStatus) {
        case UPDATE_AVAILABLE:
            setStatusUpdateAvailable();
            break;
        case ENABLED:
            setStatusEnabled();
            break;
        case DISABLED:
            setStatusDisabled();
            break;
        case DOWNLOAD_FAIL:
            setStatusDownloadFail("");
            break;
        case NO_CONNECTION:
            setStatusNoConnection();
            break;
        case CHECKING:
            setStatusChecking();
            break;
        default:
            break;
        }
    }

    /**
     * Inflate Menu
     */
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.base, menu);
    }

    /**
     * Menu Options
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
        case R.id.menu_hosts_sources:
            startActivity(new Intent(mActivity, HostsSourcesActivity.class));
            return true;

        case R.id.menu_blacklist:
            startActivity(new Intent(mActivity, BlacklistActivity.class));
            return true;

        case R.id.menu_whitelist:
            startActivity(new Intent(mActivity, WhitelistActivity.class));
            return true;

        case R.id.menu_redirection_list:
            startActivity(new Intent(mActivity, RedirectionListActivity.class));
            return true;

        case R.id.menu_preferences:
            // choose preference screen for android 2.x or 3.x (honeycomb)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
                startActivity(new Intent(mActivity, PrefsActivity.class));
            } else {
                startActivity(new Intent(mActivity, PrefsActivityHC.class));
            }
            return true;

        case R.id.menu_donations:
            showDonationsDialog();
            return true;

        case R.id.menu_about:
            showAboutDialog();
            return true;

        default:
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Inflate the layout for this fragment
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.base, container, false);
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mActivity = getActivity();

        mStatusText = (TextView) mActivity.findViewById(R.id.status_text);
        mStatusSubtitle = (TextView) mActivity.findViewById(R.id.status_subtitle);
        mStatusProgress = (ProgressBar) mActivity.findViewById(R.id.status_progress);
        mStatusIcon = (ImageView) mActivity.findViewById(R.id.status_icon);

        RootTools.debugMode = Constants.debugMode;

        // check for root
        if (Helper.isAndroidRooted(mActivity)) {
            // do background update check
            checkOnCreate();
        }
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true); // enable options menu for this fragment
    }

    /**
     * Run status AsyncTask on create
     */
    private void checkOnCreate() {
        mDatabaseHelper = new DatabaseHelper(mActivity);

        // get enabled hosts from database
        ArrayList<String> enabledHosts = mDatabaseHelper.getAllEnabledHostsSources();
        Log.d(Constants.TAG, "Enabled hosts: " + enabledHosts.toString());

        mDatabaseHelper.close();

        // build array out of list
        String[] enabledHostsArray = new String[enabledHosts.size()];
        enabledHosts.toArray(enabledHostsArray);

        if (enabledHosts.size() < 1) {
            Log.d(Constants.TAG, "no hosts sources");
        } else {
            // execute downloading of files
            runStatusTask(enabledHostsArray);
        }
    }

    /**
     * Button Action to download and apply hosts files
     * 
     * @param view
     */
    public void applyOnClick(View view) {
        mDatabaseHelper = new DatabaseHelper(mActivity);

        // get enabled hosts from databse
        ArrayList<String> enabledHosts = mDatabaseHelper.getAllEnabledHostsSources();
        Log.d(Constants.TAG, "Enabled hosts: " + enabledHosts.toString());

        mDatabaseHelper.close();

        // build array out of list
        String[] enabledHostsArray = new String[enabledHosts.size()];
        enabledHosts.toArray(enabledHostsArray);

        if (enabledHosts.size() < 1) {
            AlertDialog alertDialog = new AlertDialog.Builder(mActivity).create();
            alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
            alertDialog.setTitle(R.string.no_sources_title);
            alertDialog.setMessage(getString(org.adaway.R.string.no_sources));
            alertDialog.setButton(getString(R.string.button_close),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dlg, int sum) {
                            dlg.dismiss();
                        }
                    });
            alertDialog.show();
        } else {
            // execute downloading of files
            runDownloadTask(enabledHostsArray);
        }
    }

    /**
     * Button Action to Revert to default hosts file
     * 
     * @param view
     */
    public void revertOnClick(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
        builder.setTitle(R.string.button_revert);
        builder.setMessage(getString(R.string.revert_question));
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setCancelable(false);
        builder.setPositiveButton(getString(R.string.button_yes),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // build standard hosts file
                        try {
                            FileOutputStream fos = mActivity.openFileOutput(
                                    Constants.HOSTS_FILENAME, Context.MODE_PRIVATE);

                            // default localhost
                            String localhost = Constants.LOCALHOST_IPv4 + " "
                                    + Constants.LOCALHOST_HOSTNAME;
                            fos.write(localhost.getBytes());
                            fos.close();

                            // copy build hosts file with RootTools
                            try {
                                ApplyHelper.copyHostsFile(mActivity, false);
                            } catch (NotEnoughSpaceException e) {
                                Log.e(Constants.TAG, "Exception: " + e);
                                e.printStackTrace();

                                throw new Exception(); // TODO: make it better
                            } catch (RemountException e) {
                                Log.e(Constants.TAG, "Exception: " + e);
                                e.printStackTrace();

                                throw new Exception(); // TODO: make it better
                            } catch (CommandException e) {
                                Log.e(Constants.TAG, "Exception: " + e);
                                e.printStackTrace();

                                throw new Exception(); // TODO: make it better
                            }

                            // delete generated hosts file after applying it
                            mActivity.deleteFile(Constants.HOSTS_FILENAME);

                            // set status to disabled
                            setStatusDisabled();

                            rebootQuestion(R.string.revert_successful_title,
                                    R.string.revert_successful);
                        } catch (Exception e) {
                            Log.e(Constants.TAG, "Exception: " + e);
                            e.printStackTrace();

                            AlertDialog alertDialog = new AlertDialog.Builder(mActivity).create();
                            alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
                            alertDialog.setTitle(R.string.button_revert);
                            alertDialog.setMessage(getString(org.adaway.R.string.revert_problem));
                            alertDialog.setButton(getString(R.string.button_close),
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dlg, int sum) {
                                            dlg.dismiss();
                                        }
                                    });
                            alertDialog.show();
                        }

                    }
                });
        builder.setNegativeButton(getString(R.string.button_no),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        AlertDialog question = builder.create();
        question.show();
    }

    /**
     * Donations Dialog of AdAway
     */
    private void showDonationsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
        builder.setTitle(R.string.donations_title);

        // build view from layout
        LayoutInflater factory = LayoutInflater.from(mActivity);
        final View dialogView = factory.inflate(R.layout.donation_dialog, null);

        mFlattrWebview = (WebView) dialogView.findViewById(R.id.flattr_webview);
        mLoadingFrame = (FrameLayout) dialogView.findViewById(R.id.loading_frame);

        // http://stackoverflow.com/questions/3283819/how-do-i-make-my-progress-dialog-dismiss-after-webview-is-loaded/3993002

        mFlattrWebview.setWebViewClient(new WebViewClient() {
            private boolean loadingFinished = true;
            private boolean redirect = false;

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String urlNewString) {
                if (!loadingFinished) {
                    redirect = true;
                }

                loadingFinished = false;
                mFlattrWebview.loadUrl(urlNewString);
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                loadingFinished = false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (!redirect) {
                    loadingFinished = true;
                }

                if (loadingFinished && !redirect) {
                    // remove loading frame, show webview
                    if (mLoadingFrame.getVisibility() == View.VISIBLE) {
                        mLoadingFrame.setVisibility(View.GONE);
                        mFlattrWebview.setVisibility(View.VISIBLE);
                    }
                } else {
                    redirect = false;
                }

            }
        });

        /*
         * Partly taken from
         * http://www.dafer45.com/android/for_developers/flattr_view_example_application_how_to.html
         * http
         * ://www.dafer45.com/android/for_developers/including_a_flattr_button_in_an_application.
         * html
         */
        String flattrURL = "http://code.google.com/p/ad-away";
        String donations_description = mActivity.getString(R.string.donations_description);

        // make text white and background black
        String htmlStart = "<html> <head><style type=\"text/css\">*{color: #FFFFFF; background-color: #000000}</style>";
        String flattrJavascript = "<script type=\"text/javascript\"> /* <![CDATA[ */    (function() {        var s = document.createElement('script'), t = document.getElementsByTagName('script')[0];        s.type = 'text/javascript';        s.async = true;        s.src = 'http://api.flattr.com/js/0.6/load.js?mode=auto';        t.parentNode.insertBefore(s, t);    })();/* ]]> */</script>";
        String htmlMiddle = "</head> <body> <table> <tr> <td>";
        String flattrHtml = "<a class=\"FlattrButton\" style=\"display:none;\" href=\""
                + flattrURL
                + "\" target=\"_blank\"></a> <noscript><a href=\"http://flattr.com/thing/369138/AdAway-Ad-blocker-for-Android\" target=\"_blank\"> <img src=\"http://api.flattr.com/button/flattr-badge-large.png\" alt=\"Flattr this\" title=\"Flattr this\" border=\"0\" /></a></noscript>";

        String htmlEnd = "</td> <td>" + donations_description
                + "</td> </tr> </table> </body> </html>";

        String flattrCode = htmlStart + flattrJavascript + htmlMiddle + flattrHtml + htmlEnd;
        mFlattrWebview.getSettings().setJavaScriptEnabled(true);

        // make background of webview black
        mFlattrWebview.setBackgroundColor(0);
        mFlattrWebview.setBackgroundResource(android.R.color.background_dark);

        mFlattrWebview.loadData(flattrCode, "text/html", "utf-8");

        builder.setView(dialogView);

        builder.setIcon(android.R.drawable.ic_dialog_info);
        builder.setNeutralButton(getString(R.string.button_close),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                });
        AlertDialog question = builder.create();
        question.show();
    }

    /**
     * About Dialog of AdAway
     */
    private void showAboutDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
        builder.setTitle(R.string.about_title);

        // build view from layout
        LayoutInflater factory = LayoutInflater.from(mActivity);
        final View dialogView = factory.inflate(R.layout.about_dialog, null);

        TextView versionText = (TextView) dialogView.findViewById(R.id.about_version);
        versionText.setText(getString(R.string.about_version) + " " + getVersion());

        builder.setView(dialogView);

        builder.setIcon(android.R.drawable.ic_dialog_info);
        builder.setNeutralButton(getString(R.string.button_close),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                });
        AlertDialog question = builder.create();
        question.show();
    }

    /**
     * Get the current package version.
     * 
     * @return The current version.
     */
    private String getVersion() {
        String result = "";
        try {
            PackageManager manager = mActivity.getPackageManager();
            PackageInfo info = manager.getPackageInfo(mActivity.getPackageName(), 0);

            result = String.format("%s (%s)", info.versionName, info.versionCode);
        } catch (NameNotFoundException e) {
            Log.w(Constants.TAG, "Unable to get application version: " + e.getMessage());
            result = "Unable to get application version.";
        }

        return result;
    }

    /**
     * AsyncTask to check for updates and determine the status of AdAway, can be executed with many
     * urls as params.
     */
    private void runStatusTask(String... urls) {
        mStatusTask = new AsyncTask<String, Integer, Enum<ReturnCode>>() {
            private String currentUrl;
            private int fileSize;
            private long lastModified = 0;
            private long lastModifiedCurrent;

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                setStatusChecking();
            }

            private boolean isAndroidOnline() {
                ConnectivityManager cm = (ConnectivityManager) mActivity
                        .getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo netInfo = cm.getActiveNetworkInfo();
                if (netInfo != null && netInfo.isConnectedOrConnecting()) {
                    return true;
                }
                return false;
            }

            @Override
            protected Enum<ReturnCode> doInBackground(String... urls) {
                ReturnCode returnCode = ReturnCode.ENABLED; // default return code

                // do only if not disabled in preferences
                if (PreferencesHelper.getUpdateCheck(mActivity)) {
                    if (isAndroidOnline()) {
                        for (String url : urls) {

                            // stop if thread canceled
                            if (isCancelled()) {
                                break;
                            }

                            @SuppressWarnings("unused")
                            InputStream is = null;
                            try {
                                Log.v(Constants.TAG, "Checking hosts file: " + url);

                                /* change URL */
                                currentUrl = url;

                                /* build connection */
                                URL mURL = new URL(url);
                                URLConnection connection = mURL.openConnection();

                                fileSize = connection.getContentLength();
                                Log.d(Constants.TAG, "fileSize: " + fileSize);

                                lastModifiedCurrent = connection.getLastModified();

                                Log.d(Constants.TAG, "lastModifiedCurrent: " + lastModifiedCurrent
                                        + " (" + Helper.longToDateString(lastModifiedCurrent) + ")");

                                Log.d(Constants.TAG, "lastModified: " + lastModified + " ("
                                        + Helper.longToDateString(lastModified) + ")");

                                // set lastModified to the maximum of all lastModifieds
                                if (lastModifiedCurrent > lastModified) {
                                    lastModified = lastModifiedCurrent;
                                }

                                // check if file is available
                                connection.connect();
                                is = connection.getInputStream();

                            } catch (Exception e) {
                                Log.e(Constants.TAG, "Exception: " + e);
                                returnCode = ReturnCode.DOWNLOAD_FAIL;
                                break; // stop for-loop
                            }
                        }
                    } else {
                        returnCode = ReturnCode.NO_CONNECTION;
                    }
                }

                /* CHECK if update is necessary */
                DatabaseHelper taskDatabaseHelper = new DatabaseHelper(mActivity);

                // get last modified from db
                long lastModifiedDatabase = taskDatabaseHelper.getLastModified();

                taskDatabaseHelper.close();

                Log.d(Constants.TAG,
                        "lastModified: " + lastModified + " ("
                                + Helper.longToDateString(lastModified) + ")");

                Log.d(Constants.TAG, "lastModifiedDatabase: " + lastModifiedDatabase + " ("
                        + Helper.longToDateString(lastModifiedDatabase) + ")");

                // check if maximal lastModified is bigger than the ones in database
                if (lastModified > lastModifiedDatabase) {
                    returnCode = ReturnCode.UPDATE_AVAILABLE;
                }

                // check if hosts file is applied
                if (!ApplyHelper.isHostsFileApplied(mActivity, Constants.ANDROID_SYSTEM_ETC_PATH)) {
                    returnCode = ReturnCode.DISABLED;
                }

                return returnCode;
            }

            @Override
            protected void onPostExecute(Enum<ReturnCode> result) {
                super.onPostExecute(result);

                mStatusProgress.setVisibility(View.GONE);

                Log.d(Constants.TAG, "onPostExecute result: " + result);

                if (result == ReturnCode.UPDATE_AVAILABLE) {
                    setStatusUpdateAvailable();
                } else if (result == ReturnCode.DISABLED) {
                    setStatusDisabled();
                } else if (result == ReturnCode.DOWNLOAD_FAIL) {
                    setStatusDownloadFail(currentUrl);
                } else if (result == ReturnCode.NO_CONNECTION) {
                    setStatusNoConnection();
                } else if (result == ReturnCode.ENABLED) {
                    setStatusEnabled();
                }
            }
        };

        mStatusTask.execute(urls);
    }

    /**
     * AsyncTask to download hosts files, can be executed with many urls as params. In onPostExecute
     * an Apply AsyncTask will be started
     */
    private void runDownloadTask(String... urls) {
        AsyncTask<String, Integer, Enum<ReturnCode>> downloadTask = new AsyncTask<String, Integer, Enum<ReturnCode>>() {
            private ProgressDialog mDownloadProgressDialog;

            private int fileSize;
            private byte data[];
            private long total;
            private int count;
            private String currentUrl;
            private boolean urlChanged;
            private boolean indeterminate;
            private boolean indeterminateChanged;

            @Override
            protected void onPreExecute() {
                super.onPreExecute();

                mDownloadProgressDialog = new ProgressDialog(mActivity);
                mDownloadProgressDialog.setMessage(getString(R.string.download_dialog));
                mDownloadProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                mDownloadProgressDialog.setCancelable(true);
                mDownloadProgressDialog.setOnCancelListener(new OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        cancel(true); // cancel thread, now isCancelled() returns true
                    }
                });

                mDownloadProgressDialog.show();

                urlChanged = false;
            }

            private boolean isAndroidOnline() {
                ConnectivityManager cm = (ConnectivityManager) mActivity
                        .getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo netInfo = cm.getActiveNetworkInfo();
                if (netInfo != null && netInfo.isConnectedOrConnecting()) {
                    return true;
                }
                return false;
            }

            @Override
            protected Enum<ReturnCode> doInBackground(String... urls) {
                ReturnCode returnCode = ReturnCode.SUCCESS; // default return code

                if (isAndroidOnline()) {
                    // output to write into
                    FileOutputStream out = null;

                    try {
                        out = mActivity.openFileOutput(Constants.DOWNLOADED_HOSTS_FILENAME,
                                Context.MODE_PRIVATE);

                        for (String url : urls) {

                            // stop if thread canceled
                            if (isCancelled()) {
                                break;
                            }

                            InputStream is = null;
                            BufferedInputStream bis = null;
                            try {
                                Log.v(Constants.TAG, "Downloading hosts file: " + url);

                                /* change URL in download dialog */
                                currentUrl = url;
                                urlChanged = true;
                                publishProgress(0); // update UI

                                /* build connection */
                                URL mURL = new URL(url);
                                URLConnection connection = mURL.openConnection();

                                fileSize = connection.getContentLength();
                                Log.d(Constants.TAG, "fileSize: " + fileSize);

                                /* set progressBar to indeterminate when fileSize is -1 */
                                if (fileSize != -1) {
                                    indeterminate = false;
                                } else {
                                    indeterminate = true;
                                }
                                indeterminateChanged = true;
                                publishProgress(0); // update UI

                                /* connect */
                                connection.connect();
                                is = connection.getInputStream();
                                bis = new BufferedInputStream(is);
                                if (is == null) {
                                    Log.e(Constants.TAG, "Stream is null");
                                }

                                /* download with progress */
                                data = new byte[1024];
                                total = 0;
                                count = 0;

                                // run while only when thread is not cancelled
                                while ((count = bis.read(data)) != -1 && !isCancelled()) {
                                    out.write(data, 0, count);

                                    total += count;

                                    if (fileSize != -1) {
                                        publishProgress((int) ((total * 100) / fileSize));
                                    } else {
                                        publishProgress(50); // no ContentLength was returned
                                    }
                                }

                                // add line seperator to add files together in one file
                                out.write(Constants.LINE_SEPERATOR.getBytes());
                            } catch (Exception e) {
                                Log.e(Constants.TAG, "Exception: " + e);
                                returnCode = ReturnCode.DOWNLOAD_FAIL;
                                break; // stop for-loop
                            } finally {
                                // flush and close streams
                                try {
                                    if (out != null) {
                                        out.flush();
                                    }
                                    if (bis != null) {
                                        bis.close();
                                    }
                                    if (is != null) {
                                        is.close();
                                    }
                                } catch (Exception e) {
                                    Log.e(Constants.TAG, "Exception on flush and closing streams: "
                                            + e);
                                    e.printStackTrace();
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e(Constants.TAG, "Private File can not be created, Exception: " + e);
                        returnCode = ReturnCode.PRIVATE_FILE_FAIL;
                    } finally {
                        try {
                            if (out != null) {
                                out.close();
                            }
                        } catch (Exception e) {
                            Log.e(Constants.TAG, "Exception on close of out: " + e);
                            e.printStackTrace();
                        }
                    }
                } else {
                    returnCode = ReturnCode.NO_CONNECTION;
                }

                return returnCode;
            }

            @Override
            protected void onProgressUpdate(Integer... progress) {
                // update dialog with filename and progress
                if (urlChanged) {
                    Log.d(Constants.TAG, "urlChanged");
                    mDownloadProgressDialog.setMessage(getString(R.string.download_dialog)
                            + Constants.LINE_SEPERATOR + currentUrl);
                    urlChanged = false;
                }
                // update progressBar of dialog
                if (indeterminateChanged) {
                    Log.d(Constants.TAG, "indeterminateChanged");
                    if (indeterminate) {
                        mDownloadProgressDialog.setIndeterminate(true);
                    } else {
                        mDownloadProgressDialog.setIndeterminate(false);
                    }
                    indeterminateChanged = false;
                }
                // Log.d(Constants.TAG, "progress: " + progress[0]);
                mDownloadProgressDialog.setProgress(progress[0]);
            }

            @Override
            protected void onPostExecute(Enum<ReturnCode> result) {
                super.onPostExecute(result);

                Log.d(Constants.TAG, "onPostExecute result: " + result);

                AlertDialog alertDialog;
                if (result == ReturnCode.SUCCESS) {
                    mDownloadProgressDialog.dismiss();

                    // Apply files by Apply thread
                    runApplyTask();
                } else {
                    mDownloadProgressDialog.dismiss();

                    alertDialog = new AlertDialog.Builder(mActivity).create();
                    alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
                    alertDialog.setButton(getString(R.string.button_close),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dlg, int sum) {
                                    dlg.dismiss();
                                }
                            });

                    if (result == ReturnCode.NO_CONNECTION) {
                        alertDialog.setTitle(R.string.no_connection_title);
                        alertDialog.setMessage(getString(org.adaway.R.string.no_connection));
                    } else if (result == ReturnCode.PRIVATE_FILE_FAIL) {
                        alertDialog.setTitle(R.string.no_private_file_title);
                        alertDialog.setMessage(getString(org.adaway.R.string.no_private_file));
                    } else if (result == ReturnCode.DOWNLOAD_FAIL) {
                        alertDialog.setTitle(R.string.download_fail_title);
                        alertDialog.setMessage(getString(org.adaway.R.string.download_fail) + "\n"
                                + currentUrl);
                    }

                    alertDialog.show();
                }
            }
        };

        downloadTask.execute(urls);
    }

    /**
     * AsyncTask to parse downloaded hosts files, build one new merged hosts file out of them using
     * the redirection ip from the preferences and apply them using RootTools.
     */
    private void runApplyTask() {
        AsyncTask<Void, String, Enum<ReturnCode>> applyTask = new AsyncTask<Void, String, Enum<ReturnCode>>() {
            private ProgressDialog mApplyProgressDialog;

            @Override
            protected Enum<ReturnCode> doInBackground(Void... unused) {
                ReturnCode returnCode = ReturnCode.SUCCESS; // default return code

                try {
                    /* PARSE: parse hosts files to sets of hostnames and comments */
                    publishProgress(getString(R.string.apply_dialog_hostnames));

                    FileInputStream fis = mActivity
                            .openFileInput(Constants.DOWNLOADED_HOSTS_FILENAME);

                    BufferedReader reader = new BufferedReader(new InputStreamReader(fis));

                    HostsParser parser = new HostsParser(reader, mActivity);
                    HashSet<String> hostnames = parser.getHostnames();

                    fis.close();

                    publishProgress(getString(R.string.apply_dialog_lists));

                    /* READ DATABSE CONTENT */
                    mDatabaseHelper = new DatabaseHelper(mActivity);

                    // get whitelist
                    HashSet<String> whitelist = mDatabaseHelper.getAllEnabledWhitelistItems();
                    Log.d(Constants.TAG, "Enabled whitelist: " + whitelist.toString());

                    // get blacklist
                    HashSet<String> blacklist = mDatabaseHelper.getAllEnabledBlacklistItems();
                    Log.d(Constants.TAG, "Enabled blacklist: " + blacklist.toString());

                    // get redirection list
                    HashMap<String, String> redirection = mDatabaseHelper
                            .getAllEnabledRedirectionItems();
                    Log.d(Constants.TAG, "Enabled redirection list: " + redirection.toString());

                    // get sources list
                    ArrayList<String> enabledHostsSources = mDatabaseHelper
                            .getAllEnabledHostsSources();
                    Log.d(Constants.TAG,
                            "Enabled hosts sources list: " + enabledHostsSources.toString());

                    mDatabaseHelper.close();

                    /* BLACKLIST AND WHITELIST */
                    // remove whitelist items
                    hostnames.removeAll(whitelist);

                    // add blacklist items
                    hostnames.addAll(blacklist);

                    /* REDIRECTION LIST: remove hostnames that are in redirection list */
                    HashSet<String> redirectionRemove = new HashSet<String>(redirection.keySet());

                    // remove all redirection hostnames
                    hostnames.removeAll(redirectionRemove);

                    /* BUILD: build one hosts file out of sets and preferences */
                    publishProgress(getString(R.string.apply_dialog_hosts));

                    FileOutputStream fos = mActivity.openFileOutput(Constants.HOSTS_FILENAME,
                            Context.MODE_PRIVATE);

                    // add adaway header
                    String header = Constants.HEADER1 + Constants.LINE_SEPERATOR
                            + Constants.HEADER2 + Constants.LINE_SEPERATOR
                            + Constants.HEADER_SOURCES;
                    fos.write(header.getBytes());

                    // write sources into header
                    String source = null;
                    for (String host : enabledHostsSources) {
                        source = Constants.LINE_SEPERATOR + "# " + host;
                        fos.write(source.getBytes());
                    }

                    fos.write(Constants.LINE_SEPERATOR.getBytes());

                    String redirectionIP = PreferencesHelper.getRedirectionIP(mActivity);

                    // add "127.0.0.1 localhost" entry
                    String localhost = Constants.LINE_SEPERATOR + Constants.LOCALHOST_IPv4 + " "
                            + Constants.LOCALHOST_HOSTNAME;
                    fos.write(localhost.getBytes());

                    fos.write(Constants.LINE_SEPERATOR.getBytes());

                    // write hostnames
                    String line;
                    for (String hostname : hostnames) {
                        line = Constants.LINE_SEPERATOR + redirectionIP + " " + hostname;
                        fos.write(line.getBytes());
                    }

                    /* REDIRECTION LIST: write redirection items */
                    String redirectionItemHostname;
                    String redirectionItemIP;
                    for (HashMap.Entry<String, String> item : redirection.entrySet()) {
                        redirectionItemHostname = item.getKey();
                        redirectionItemIP = item.getValue();

                        line = Constants.LINE_SEPERATOR + redirectionItemIP + " "
                                + redirectionItemHostname;
                        fos.write(line.getBytes());
                    }

                    fos.close();

                } catch (FileNotFoundException e) {
                    Log.e(Constants.TAG, "file to read or file to write could not be found");
                    Log.e(Constants.TAG, "Exception: " + e);
                    e.printStackTrace();

                    returnCode = ReturnCode.PRIVATE_FILE_FAIL;
                } catch (IOException e) {
                    Log.e(Constants.TAG, "files can not be written or read");
                    Log.e(Constants.TAG, "Exception: " + e);
                    e.printStackTrace();

                    returnCode = ReturnCode.PRIVATE_FILE_FAIL;
                }

                // delete downloaded hosts file from private storage
                mActivity.deleteFile(Constants.DOWNLOADED_HOSTS_FILENAME);

                /* APPLY: apply hosts file using RootTools in copyHostsFile() */
                publishProgress(getString(R.string.apply_dialog_apply));

                // copy build hosts file with RootTools
                try {
                    if (PreferencesHelper.getApplyMethod(mActivity).equals("writeToSystem")) {
                        ApplyHelper.copyHostsFile(mActivity, false);
                    } else if (PreferencesHelper.getApplyMethod(mActivity)
                            .equals("writeToDataData")) {
                        ApplyHelper.copyHostsFile(mActivity, true);
                    }
                } catch (NotEnoughSpaceException e) {
                    Log.e(Constants.TAG, "Exception: " + e);
                    e.printStackTrace();

                    returnCode = ReturnCode.NOT_ENOUGH_SPACE;
                } catch (RemountException e) {
                    Log.e(Constants.TAG, "Exception: " + e);
                    e.printStackTrace();

                    returnCode = ReturnCode.REMOUNT_FAIL;
                } catch (CommandException e) {
                    Log.e(Constants.TAG, "Exception: " + e);
                    e.printStackTrace();

                    returnCode = ReturnCode.COPY_FAIL;
                }

                // delete generated hosts file from private storage
                mActivity.deleteFile(Constants.HOSTS_FILENAME);

                /* Set lastModified date in database to current date */
                mDatabaseHelper = new DatabaseHelper(mActivity);

                long lastModified = Helper.getCurrentLongDate();
                mDatabaseHelper.updateLastModified(lastModified);
                Log.d(Constants.TAG, "Updated all hosts sources with lastModified: " + lastModified
                        + " (" + Helper.longToDateString(lastModified) + ")");

                mDatabaseHelper.close();

                /* check if hosts file is applied with chosen method */
                // check only if everything before was successful
                if (returnCode == ReturnCode.SUCCESS) {
                    if (PreferencesHelper.getApplyMethod(mActivity).equals("writeToSystem")) {
                        if (!ApplyHelper.isHostsFileApplied(mActivity,
                                Constants.ANDROID_SYSTEM_ETC_PATH)) {
                            returnCode = ReturnCode.APPLY_FAIL;
                        }
                    } else if (PreferencesHelper.getApplyMethod(mActivity)
                            .equals("writeToDataData")) {
                        if (!ApplyHelper.isHostsFileApplied(mActivity,
                                Constants.ANDROID_DATA_DATA_PATH)) {
                            returnCode = ReturnCode.APPLY_FAIL;
                        } else {
                            if (!ApplyHelper.isHostsFileApplied(mActivity,
                                    Constants.ANDROID_SYSTEM_ETC_PATH)) {
                                returnCode = ReturnCode.SYMLINK_MISSING;
                            }
                        }
                    }
                }

                return returnCode;
            }

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                mApplyProgressDialog = new ProgressDialog(mActivity);
                mApplyProgressDialog.setMessage(getString(R.string.apply_dialog));
                mApplyProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                mApplyProgressDialog.setCancelable(false);
                mApplyProgressDialog.show();
            }

            @Override
            protected void onProgressUpdate(String... status) {
                mApplyProgressDialog.setMessage(status[0]);
            }

            @Override
            protected void onPostExecute(Enum<ReturnCode> result) {
                super.onPostExecute(result);

                AlertDialog alertDialog;
                if (result == ReturnCode.SUCCESS) {
                    mApplyProgressDialog.dismiss();

                    setStatusEnabled();

                    rebootQuestion(R.string.apply_success_title, R.string.apply_success);
                } else if (result == ReturnCode.SYMLINK_MISSING) {
                    mApplyProgressDialog.dismiss();

                    AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
                    builder.setTitle(R.string.apply_symlink_missing_title);
                    builder.setMessage(getString(R.string.apply_symlink_missing));
                    builder.setIcon(android.R.drawable.ic_dialog_info);
                    builder.setPositiveButton(getString(R.string.button_yes),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    tryToCreateSymlink();
                                }
                            });
                    builder.setNegativeButton(getString(R.string.button_no),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.dismiss();

                                    setStatusDisabled();
                                }
                            });
                    AlertDialog question = builder.create();
                    question.show();
                } else {
                    mApplyProgressDialog.dismiss();

                    setStatusDisabled();

                    alertDialog = new AlertDialog.Builder(mActivity).create();
                    alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
                    alertDialog.setButton(getString(R.string.button_close),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dlg, int sum) {
                                    dlg.dismiss();
                                }
                            });

                    if (result == ReturnCode.APPLY_FAIL) {
                        alertDialog.setTitle(R.string.apply_fail_title);
                        alertDialog.setMessage(getString(org.adaway.R.string.apply_fail));

                    } else if (result == ReturnCode.PRIVATE_FILE_FAIL) {
                        alertDialog.setTitle(R.string.apply_private_file_fail_title);
                        alertDialog
                                .setMessage(getString(org.adaway.R.string.apply_private_file_fail));
                    } else if (result == ReturnCode.NOT_ENOUGH_SPACE) {
                        alertDialog.setTitle(R.string.apply_not_enough_space_title);
                        alertDialog
                                .setMessage(getString(org.adaway.R.string.apply_not_enough_space));
                    } else if (result == ReturnCode.REMOUNT_FAIL) {
                        alertDialog.setTitle(R.string.apply_remount_fail_title);
                        alertDialog.setMessage(getString(org.adaway.R.string.apply_remount_fail));
                    } else if (result == ReturnCode.COPY_FAIL) {
                        alertDialog.setTitle(R.string.apply_copy_fail_title);
                        alertDialog.setMessage(getString(org.adaway.R.string.apply_copy_fail));
                    }

                    alertDialog.show();
                }
            }
        };

        applyTask.execute();
    }

    /**
     * Trying to create symlink and displays dialogs on fail
     */
    private void tryToCreateSymlink() {
        boolean success = true;

        try {
            ApplyHelper.createSymlink();
        } catch (CommandException e) {
            Log.e(Constants.TAG, "Exception: " + e);
            e.printStackTrace();

            success = false;
        } catch (RemountException e) {
            Log.e(Constants.TAG, "Exception: " + e);
            e.printStackTrace();

            success = false;
        }

        if (success) {
            if (ApplyHelper.isHostsFileApplied(mActivity, Constants.ANDROID_SYSTEM_ETC_PATH)) {
                success = true;
            } else {
                success = false;
            }
        }

        if (success) {
            setStatusEnabled();

            rebootQuestion(R.string.apply_symlink_successful_title,
                    R.string.apply_symlink_successful);
        } else {
            setStatusDisabled();

            AlertDialog alertDialog = new AlertDialog.Builder(mActivity).create();
            alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
            alertDialog.setTitle(R.string.apply_symlink_fail_title);
            alertDialog.setMessage(getString(org.adaway.R.string.apply_symlink_fail));
            alertDialog.setButton(getString(R.string.button_close),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dlg, int sum) {
                            dlg.dismiss();
                        }
                    });
            alertDialog.show();
        }
    }

    /**
     * Show reboot question
     * 
     * @param titleR
     *            resource id of title string
     * @param messageR
     *            resource id of message string
     */
    private void rebootQuestion(int titleR, int messageR) {
        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
        builder.setTitle(titleR);
        builder.setMessage(getString(messageR));
        builder.setIcon(android.R.drawable.ic_dialog_info);
        builder.setPositiveButton(getString(R.string.button_yes),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        try {
                            ApplyHelper.reboot();
                        } catch (CommandException e) {
                            Log.e(Constants.TAG, "Exception: " + e);
                            e.printStackTrace();
                        }
                    }
                });
        builder.setNegativeButton(getString(R.string.button_no),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                });
        AlertDialog question = builder.create();
        question.show();
    }
}