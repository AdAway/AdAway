package org.adaway;

//TODO: read database and constant best practices

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;

import com.stericson.RootTools.*;

public class AdAway extends Activity {
    private Context mContext;
    private HostsDatabase mHostsDatabase;

    static final String TAG = "AdAway";
    static final String TAG_APPLY = TAG + " Apply Async";
    static final String TAG_DOWNLOAD = TAG + " Download Async";

    static final String LOCALHOST_IPv4 = "127.0.0.1";
    static final String LOCALHOST_HOSTNAME = "hostname";
    static final String DOWNLOADED_HOSTS_FILENAME = "hosts_downloaded";
    static final String HOSTS_FILENAME = "hosts";
    static final String LINE_SEPERATOR = System.getProperty("line.separator");
    static final String CP_COMMAND = "cp -f";
    static final String ANDROID_HOSTS_PATH = "/system/etc/hosts";

    private ProgressDialog mDownloadProgressDialog;
    public static final int DIALOG_DOWNLOAD_PROGRESS = 0;

    private ProgressDialog mApplyProgressDialog;
    public static final int DIALOG_APPLY_PROGRESS = DIALOG_DOWNLOAD_PROGRESS + 1;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
        case R.id.menu_hosts_files:
            startActivity(new Intent(this, HostsFiles.class));
            return true;

        case R.id.menu_preferences:
            startActivity(new Intent(this, Preferences.class));
            return true;

        case R.id.menu_about:
            showAboutDialog();
            return true;

        default:
            return super.onOptionsItemSelected(item);
        }
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mContext = this;

        RootTools.debugMode = true;

        // check for root on device
        if (!RootTools.isRootAvailable()) {
            // su binary does not exist, raise no root dialog
            showNoRootDialog();
        } else {
            // su binary exists, request permission
            if (!RootTools.isAccessGiven()) {
                showNoRootDialog();
            } else {
                if (!RootTools.isBusyboxAvailable()) { // checking for busybox needs root
                    showNoRootDialog();
                }
            }
        }

        // TEST --------------
        mHostsDatabase = new HostsDatabase(mContext);
        // mHostsDatabase.insertHostsFile("http://bla.de");

        mHostsDatabase.modifyEnabled(2, 1);

        ArrayList<String> list = mHostsDatabase.getAllEnabledHosts();

        Log.d(TAG, list.toString());

        Log.d(TAG, "after err");

    }

    public void applyOnClick(View view) {
        new DownloadHostsFiles().execute("http://winhelp2002.mvps.org/hosts.txt", "http://winhelp2002.mvps.org/hosts.txt");
        // apply is executed in onPostExecute in DownloadHos
    }

    public void revertOnClick(View view) {
        Log.d(TAG, "revert");
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.button_revert);
        builder.setMessage(getString(R.string.revert_question));
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setCancelable(false);
        builder.setPositiveButton(getString(R.string.button_yes), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // build standard hosts file
                try {
                    FileOutputStream fos = openFileOutput(HOSTS_FILENAME, Context.MODE_PRIVATE);

                    // default localhost
                    String localhost = LOCALHOST_IPv4 + " " + LOCALHOST_HOSTNAME;
                    fos.write(localhost.getBytes());
                    fos.close();
                } catch (IOException e) {
                    Log.e(TAG, "IO Exception");
                    e.printStackTrace();
                }

                // copy hosts file with RootTools
                copyHostsFile();

                // delete generated hosts file after applying it
                deleteFile(HOSTS_FILENAME);

                AlertDialog alertDialog = new AlertDialog.Builder(mContext).create();
                alertDialog.setIcon(android.R.drawable.ic_dialog_info);
                alertDialog.setTitle(R.string.button_revert);
                alertDialog.setMessage(getString(org.adaway.R.string.revert_successfull));
                alertDialog.setButton(getString(R.string.button_close), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dlg, int sum) {
                        // do nothing, close
                    }
                });
                alertDialog.show();
            }
        });
        builder.setNegativeButton(getString(R.string.button_no), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });
        AlertDialog question = builder.create();
        question.show();
    }

    private void showAboutDialog() {
        final Dialog dialog = new Dialog(mContext);
        dialog.requestWindowFeature(Window.FEATURE_LEFT_ICON);
        dialog.setContentView(R.layout.about_dialog);
        dialog.setTitle(R.string.about_title);

        TextView versionText = (TextView) dialog.findViewById(R.id.about_version);
        versionText.setText(getString(R.string.about_version) + " " + getVersion());

        Button closeBtn = (Button) dialog.findViewById(R.id.about_close);
        closeBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                dialog.cancel();
            }
        });

        dialog.show();
        dialog.setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, android.R.drawable.ic_dialog_info);
    }

    /**
     * Get the current package version.
     * 
     * @return The current version.
     */
    private String getVersion() {
        String result = "";
        try {
            PackageManager manager = this.getPackageManager();
            PackageInfo info = manager.getPackageInfo(this.getPackageName(), 0);

            result = String.format("%s (%s)", info.versionName, info.versionCode);
        } catch (NameNotFoundException e) {
            Log.w(TAG, "Unable to get application version: " + e.getMessage());
            result = "Unable to get application version.";
        }

        return result;
    }

    private void showNoRootDialog() {
        final Dialog dialog = new Dialog(mContext);
        dialog.requestWindowFeature(Window.FEATURE_LEFT_ICON);
        dialog.setContentView(R.layout.no_root_dialog);
        dialog.setTitle(R.string.no_root_title);

        // Exit Button closes application
        Button exitButton = (Button) dialog.findViewById(R.id.no_root_exit);
        exitButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish(); // finish current activity, means exiting app
            }
        });

        // when dialog is closed by pressing back exit app
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                finish();
            }
        });

        dialog.show();
        dialog.setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, android.R.drawable.ic_dialog_alert);
    }

    private boolean copyHostsFile() {
        String appDir = getFilesDir().getAbsolutePath();
        Log.d(TAG_APPLY, "dir: " + appDir);

        String file = appDir + File.separator + HOSTS_FILENAME;

        Log.d(TAG_APPLY, "file: " + file);

        String command = CP_COMMAND + " " + file + " " + ANDROID_HOSTS_PATH;

        Log.d(TAG_APPLY, "command: " + command);

        // do it with RootTools
        try {
            // remount for write access
            RootTools.remount(ANDROID_HOSTS_PATH, "RW");

            // TODO: check for space
            // RootTools.getSpace(ANDROID_HOSTS_PATH);

            // do command
            List<String> output = RootTools.sendShell(command);

            Log.d(TAG_APPLY, "output of command: " + output.toString());
        } catch (IOException e) {
            Log.e(TAG_APPLY, "IOException");

            return false;
        } catch (InterruptedException e) {
            Log.e(TAG_APPLY, "InterruptedException");
            e.printStackTrace();

            return false;
        } catch (RootToolsException e) {
            Log.e(TAG_APPLY, "RootToolsException");
            e.printStackTrace();

            return false;
        } finally {
            // after all remount back as read only
            RootTools.remount(ANDROID_HOSTS_PATH, "RO");
        }

        return true;
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
        case DIALOG_DOWNLOAD_PROGRESS:
            mDownloadProgressDialog = new ProgressDialog(this);
            mDownloadProgressDialog.setMessage(getString(R.string.download_dialog));
            mDownloadProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mDownloadProgressDialog.setCancelable(false);
            mDownloadProgressDialog.show();
            return mDownloadProgressDialog;
        case DIALOG_APPLY_PROGRESS:
            mApplyProgressDialog = new ProgressDialog(this);
            mApplyProgressDialog.setMessage(getString(R.string.apply_dialog));
            mApplyProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mApplyProgressDialog.setCancelable(false);
            mApplyProgressDialog.show();
            return mApplyProgressDialog;
        default:
            return null;
        }
    }

    private class DownloadHostsFiles extends AsyncTask<String, Integer, Boolean> {
        private String currentURL;
        private int fileSize;
        private byte data[];
        private long total;
        private int count;
        private boolean messageChanged;

        public DownloadHostsFiles() {
            messageChanged = false;
        }

        private boolean isAndroidOnline() {
            try {
                ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
                return cm.getActiveNetworkInfo().isConnectedOrConnecting();
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        protected Boolean doInBackground(String... urls) {
            try {
                if (isAndroidOnline()) {
                    // output to write into
                    FileOutputStream out = openFileOutput(DOWNLOADED_HOSTS_FILENAME, Context.MODE_PRIVATE);

                    for (String url : urls) {
                        Log.v(TAG, "Starting downloading hostname file: " + urls[0]);

                        URL mURL = new URL(url);
                        // if (mURL.getProtocol() == "http") { // TODO: implement SSL httpsURLConnection
                        HttpURLConnection connection = (HttpURLConnection) mURL.openConnection();
                        // } else if (mURL.getProtocol() == "https") {
                        //
                        // } else {
                        // Log.e(TAG, "wrong protocol");
                        // }
                        fileSize = connection.getContentLength();

                        // TODO:
                        // long getLastModified()
                        // Returns the value of the last-modified header field.

                        connection.connect();

                        InputStream in = connection.getInputStream();
                        if (in == null) {
                            Log.e(TAG_DOWNLOAD, "Stream is null");
                        }

                        data = new byte[1024];

                        total = 0;
                        count = 0;

                        currentURL = url; // for displaying in progress dialog
                        messageChanged = true; // with this, onProgressUpdate knows that the message has been set

                        while ((count = in.read(data)) != -1) {
                            total += count;
                            publishProgress((int) ((total * 100) / fileSize));
                            out.write(data, 0, count);
                        }

                        out.write(LINE_SEPERATOR.getBytes()); // add line seperator to add hostname files together in one file
                        out.flush();
                        in.close();
                        connection.disconnect();
                    }

                    out.close();

                    return true;
                } else {
                    return false;
                }
            } catch (Exception e) {
                Log.e(TAG_DOWNLOAD, "Exception: " + e);
                return false;
            }
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showDialog(DIALOG_DOWNLOAD_PROGRESS);
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            // Log.d(TAG_DOWNLOAD, progress[0].toString());

            // update dialog with filename and progress
            if (messageChanged) {
                Log.d(TAG_DOWNLOAD, "messageChanged");
                mDownloadProgressDialog.setMessage(getString(R.string.download_dialog) + LINE_SEPERATOR + currentURL);
                messageChanged = false;
            }
            mDownloadProgressDialog.setProgress(progress[0]);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);

            Log.d(TAG_DOWNLOAD, "on post exec");
            Log.d(TAG_DOWNLOAD, result.toString());

            if (result) {
                removeDialog(DIALOG_DOWNLOAD_PROGRESS);

                // Apply files by Apply thread
                new Apply().execute();
            } else {
                removeDialog(DIALOG_DOWNLOAD_PROGRESS);
                Log.d(TAG_DOWNLOAD, "problem");
                AlertDialog alertDialog = new AlertDialog.Builder(mContext).create();
                alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
                alertDialog.setTitle(R.string.no_connection_title);
                alertDialog.setMessage(getString(org.adaway.R.string.no_connection));
                alertDialog.setButton(getString(R.string.button_close), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dlg, int sum) {
                        // do nothing, close
                    }
                });
                alertDialog.show();
            }
        }
    }

    private class Apply extends AsyncTask<Void, String, Boolean> {

        @Override
        protected Boolean doInBackground(Void... unused) {
            // parse hostname files
            // make one big set of all hostnames so no hostname is more than once in it
            // build hosts file based on redirection ip from prefs, default is 127.0.0.1

            try {
                publishProgress(getString(R.string.apply_dialog_hostnames));

                FileInputStream fis = openFileInput(DOWNLOADED_HOSTS_FILENAME);

                BufferedReader reader = new BufferedReader(new InputStreamReader(fis));

                // parse file using HostsParser
                HostsParser parser = new HostsParser(reader, getApplicationContext());
                HashSet<String> hostnames = parser.getHostnames();
                LinkedList<String> comments = parser.getComments();

                fis.close();

                publishProgress(getString(R.string.apply_dialog_hosts));

                // build hosts file out of it

                FileOutputStream fos = openFileOutput(HOSTS_FILENAME, Context.MODE_PRIVATE);

                // add adaway header
                String header = "# This hosts file is generated by AdAway." + LINE_SEPERATOR + "# Please do not modify it directly, it will be overwritten when AdAway is applied again." + LINE_SEPERATOR + "# " + LINE_SEPERATOR + "# The following lines are comments from the downloaded hosts files:";
                fos.write(header.getBytes());

                // write comments from other files to header
                Iterator<String> itComments = comments.iterator();
                String comment;
                while (itComments.hasNext()) {
                    comment = itComments.next();
                    comment = LINE_SEPERATOR + comment;
                    fos.write(comment.getBytes());
                }

                fos.write(LINE_SEPERATOR.getBytes());

                String redirectionIP = SharedPrefs.getRedirectionIP(getApplicationContext());

                // add "127.0.0.1 localhost" entry
                String localhost = LINE_SEPERATOR + LOCALHOST_IPv4 + " " + LOCALHOST_HOSTNAME;
                fos.write(localhost.getBytes());

                fos.write(LINE_SEPERATOR.getBytes());

                // write hostnames
                Iterator<String> itHostname = hostnames.iterator();
                String line;
                String hostname;
                while (itHostname.hasNext()) {
                    // Get element
                    hostname = itHostname.next();
                    // Log.d(TAG_APPLY, hostname);

                    line = LINE_SEPERATOR + redirectionIP + " " + hostname;
                    fos.write(line.getBytes());
                }

                fos.close();

                // delete downloaded hosts file
                deleteFile(DOWNLOADED_HOSTS_FILENAME);

                publishProgress(getString(R.string.apply_dialog_apply));

                // copy build hosts file with RootTools
                copyHostsFile();

                // delete generated hosts file after applying it
                deleteFile(HOSTS_FILENAME);

            } catch (FileNotFoundException e) {
                Log.e(TAG_APPLY, "FileNotFoundException");
                e.printStackTrace();
            } catch (IllegalStateException e) {
                Log.e(TAG_APPLY, "IllegalStateException");
                e.printStackTrace();
            } catch (IOException e) {
                Log.e(TAG_APPLY, "IOException");
                e.printStackTrace();
            }

            return true;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showDialog(DIALOG_APPLY_PROGRESS);
        }

        @Override
        protected void onProgressUpdate(String... status) {
            Log.d(TAG_APPLY, status[0].toString());

            mApplyProgressDialog.setMessage(status[0]);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);

            Log.d(TAG_APPLY, "on post exec");
            Log.d(TAG_APPLY, result.toString());

            if (result) {
                removeDialog(DIALOG_APPLY_PROGRESS);
                AlertDialog alertDialog = new AlertDialog.Builder(mContext).create();
                alertDialog.setIcon(android.R.drawable.ic_dialog_info);
                alertDialog.setTitle(R.string.apply_dialog);
                alertDialog.setMessage(getString(R.string.apply_success));
                alertDialog.setButton(getString(R.string.button_close), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dlg, int sum) {
                        // do nothing, close
                    }
                });
                alertDialog.show();

            } else {
                removeDialog(DIALOG_APPLY_PROGRESS);
                Log.d(TAG_APPLY, "problem");
                // AlertDialog alertDialog;
                // alertDialog = new AlertDialog.Builder(mContext).create();
                // alertDialog.setTitle(R.string.no_connection_title);
                // alertDialog.setMessage(getString(org.adaway.R.string.no_connection));
                // alertDialog.setButton(getString(R.string.close_button), new DialogInterface.OnClickListener() {
                // public void onClick(DialogInterface dlg, int sum) {
                // // do nothing, close
                // }
                // });
                // alertDialog.show();
            }
        }
    }

}