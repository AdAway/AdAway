package org.adaway;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import android.app.AlertDialog;
import android.content.DialogInterface;

import com.stericson.RootTools.*;

public class Information extends Activity {
    private Context mContext;
    static final String TAG = "AdAway";

    private ProgressDialog mProgressDialog;
    public static final int DIALOG_DOWNLOAD_PROGRESS = 0;
    
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
        case R.id.menu_hostname_files:
            // TODO
            Log.i(TAG, "menu hostname");
            return true;
        case R.id.menu_help:
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
        if (!RootTools.isRootAvailable()) { // wants root: || !RootTools.isBusyboxAvailable()) {
            // su binary does not exist, raise root dialog
            showRootDialog();
        }

        // check if private hostfile exists:
        // -> enable apply button

        // /system/etc/hosts
        // infos http://forum.xda-developers.com/showthread.php?t=509997
        // http://pgl.yoyo.org/adservers/serverlist.php?hostformat=nohtml

        // try {
        // FileInputStream fis = openFileInput(FILENAME);
        //
        // BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
        //
        // String nextLine = null;
        // while ((nextLine = reader.readLine()) != null) {
        // Log.i("test", nextLine);
        // }
        // } catch (FileNotFoundException e) {
        // // TODO Auto-generated catch block
        // e.printStackTrace();
        // } catch (IllegalStateException e) {
        // // TODO Auto-generated catch block
        // e.printStackTrace();
        // } catch (IOException e) {
        // // TODO Auto-generated catch block
        // e.printStackTrace();
        // }
        //
        // }
        // });

    }

    public void downloadOnClick(View view) {
        // download
        // downloadFiles("http://ad-away.googlecode.com/files/hostnames-2011-03-08.txt", "http://ad-away.googlecode.com/files/hostnames-2011-03-08.txt");
        downloadFiles("http://ad-away.googlecode.com/files/test.txt", "http://ad-away.googlecode.com/files/test.txt");
    }

    private void downloadFiles(String... urls) {
        new DownloadHostnameFiles().execute(urls);
    }

    public void applyOnClick(View view) {
        // parse hostname files
        // make one big set (menge) of all hostnames so no hostname is more than once in it
        // build hosts file based on pref, default 127.0.0.1 ip
        // apply hosts file using roottools

        // TODO: check for file syntax?

    }

    private void showRootDialog() {
        Dialog rootDialog = new Dialog(mContext);
        rootDialog.setContentView(R.layout.root_dialog);
        rootDialog.setTitle(R.string.no_root_title);

        // Cyanogenmod Button goes to website
        Button cyanogenmodButton = (Button) rootDialog.findViewById(R.id.cyanogenmod_button);
        cyanogenmodButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Uri uri = Uri.parse("http://www.cyanogenmod.com");
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            }
        });

        // Exit Button closes application
        Button exitButton = (Button) rootDialog.findViewById(R.id.exit_button);
        exitButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // finish this activity
                finish();
            }
        });

        rootDialog.show();
    }

    private class DownloadHostnameFiles extends AsyncTask<String, Integer, Boolean> {
        private String currentURL;
        private HttpURLConnection connection;
        private int fileSize;
        private byte data[];
        private long total;
        private int count;
        private boolean titleChanged;

        public DownloadHostnameFiles() {
            titleChanged = false;
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
                    FileOutputStream out = openFileOutput("hostnames", Context.MODE_PRIVATE);

                    for (String url : urls) {
                        Log.v(TAG, "Starting downloading hostname file: " + urls[0]);

                        currentURL = url; // for displaying in progress dialog
                        titleChanged = true; // with this, onProgressUpdate knows that the title has been set

                        URL mURL = new URL(url);
                        connection = (HttpURLConnection) mURL.openConnection();
                        fileSize = connection.getContentLength();

                        // TODO:
                        // long getLastModified()
                        // Returns the value of the last-modified header field.

                        connection.connect();

                        InputStream in = connection.getInputStream();
                        if (in == null) {
                            Log.e(TAG, "Stream is null");
                        }

                        data = new byte[1024];

                        total = 0;
                        count = 0;
                        while ((count = in.read(data)) != -1) {
                            total += count;
                            publishProgress((int) ((total * 100) / fileSize));
                            out.write(data, 0, count);
                        }

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
                System.out.println("Exception: " + e);
                return false;
            }
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showDialog(DIALOG_DOWNLOAD_PROGRESS);

            super.onPreExecute();
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            Log.d(TAG, progress[0].toString());

            // update dialog with filename and progress
            if (titleChanged) {
                mProgressDialog.setMessage(getString(R.string.download_dialog) + currentURL);
                titleChanged = false;
            }
            mProgressDialog.setProgress(progress[0]);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            Log.d(TAG, "on post exec");
            Log.d(TAG, result.toString());

            if (result) {
                dismissDialog(DIALOG_DOWNLOAD_PROGRESS);
            } else {
                dismissDialog(DIALOG_DOWNLOAD_PROGRESS);
                Log.i(TAG, "problem");
                AlertDialog alertDialog;
                alertDialog = new AlertDialog.Builder(mContext).create();
                alertDialog.setTitle(R.string.no_connection_title);
                alertDialog.setMessage(getString(org.adaway.R.string.no_connection));
                alertDialog.setButton(getString(R.string.close_button), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dlg, int sum) {
                        // do nothing, close
                    }
                });
                alertDialog.show();
            }

            super.onPostExecute(result);
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
        case DIALOG_DOWNLOAD_PROGRESS:
            mProgressDialog = new ProgressDialog(this);
            mProgressDialog.setMessage(getString(R.string.download_dialog));
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.setCancelable(false);
            mProgressDialog.show();
            return mProgressDialog;
        default:
            return null;
        }
    }
}