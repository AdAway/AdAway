package org.adaway.ui.hostscontent;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.topjohnwu.superuser.io.SuFile;

import org.adaway.R;
import org.adaway.ui.dialog.ActivityNotFoundDialogFragment;
import org.adaway.util.Constants;
import org.adaway.util.Log;
import org.adaway.util.MountType;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.function.Consumer;

import static org.adaway.util.ShellUtils.remountPartition;
import static org.adaway.util.ShellUtils.resolveSymlink;

/**
 * This class is a {@link Fragment} to explains and display the hosts files.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class HostsContentFragment extends Fragment {
    /**
     * The request code to identify the hosts file edition without remount action.
     */
    private static final int EDIT_HOSTS_REQUEST_CODE = 20;
    /**
     * The request code to identify the hosts file edition with remount action.
     */
    private static final int EDIT_HOSTS_AND_REMOUNT_REQUEST_CODE = 21;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        /*
         * Create view.
         */
        // Create fragment view
        View view = inflater.inflate(R.layout.hosts_content_fragment, container, false);
        /*
         * Load hosts file content.
         */
        // Get the hosts context text view
        TextView hostsContextTextView = view.findViewById(R.id.hosts_content_text);
        // Load hosts file content
        new HostsContentLoader(hostsContextTextView::setText).execute();
        /*
         * Configure menu button.
         */
        // Get open file button
        Button openFileButton = view.findViewById(R.id.hosts_open_file);
        // Bind on click listener
        openFileButton.setOnClickListener(button -> openHostsFile());
        // Return created view
        return view;
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == EDIT_HOSTS_AND_REMOUNT_REQUEST_CODE) {
            SuFile hostFile = resolveSymlink(new SuFile(Constants.ANDROID_SYSTEM_ETC_HOSTS));
            remountPartition(hostFile, MountType.READ_ONLY);
        }
    }

    private void openHostsFile() {
        SuFile hostFile = resolveSymlink(new SuFile(Constants.ANDROID_SYSTEM_ETC_HOSTS));
        boolean remount = !hostFile.canWrite() && remountPartition(hostFile, MountType.READ_WRITE);
        try {
            Intent intent = new Intent()
                    .setAction(Intent.ACTION_VIEW)
                    .setDataAndType(Uri.parse("file://" + hostFile.getAbsolutePath()), "text/plain");
            startActivityForResult(intent, remount ? EDIT_HOSTS_AND_REMOUNT_REQUEST_CODE : EDIT_HOSTS_REQUEST_CODE);
        } catch (ActivityNotFoundException exception) {
            FragmentManager fragmentManager = this.getFragmentManager();
            if (fragmentManager != null) {
                ActivityNotFoundDialogFragment notFoundDialog = ActivityNotFoundDialogFragment.newInstance(
                        R.string.no_text_editor_title,
                        R.string.no_text_editor,
                        "market://details?id=jp.sblo.pandora.jota", "Text Edit"
                );
                notFoundDialog.show(fragmentManager, "notFoundDialog");
            }
        }
    }

    /**
     * This class is a {@link AsyncTask} to load the first line of the hosts file.
     */
    private static class HostsContentLoader extends AsyncTask<Void, Void, String> {
        /**
         * The maximum number of read lines by the loader.
         */
        private static final short MAX_READ_LINES = 30;

        /**
         * The callback to call with the hosts file content.
         */
        private final WeakReference<Consumer<String>> callback;

        /**
         * Constructor.
         *
         * @param callback The callback to call with the hosts file content.
         */
        private HostsContentLoader(Consumer<String> callback) {
            this.callback = new WeakReference<>(callback);
        }

        @Override
        protected String doInBackground(Void... params) {
            // Get the hosts file
            File hostsFile = new File(Constants.ANDROID_SYSTEM_ETC_HOSTS);
            // Declare file content
            StringBuilder content = new StringBuilder();
            // Open reader onto the file
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(hostsFile)))) {
                // Declare line content
                String line;
                // Declare number of read lines
                short readLines = 0;
                // Read until end of file or max line to read
                while ((line = reader.readLine()) != null && readLines < MAX_READ_LINES) {
                    if (readLines > 0) {
                        content.append("\n");
                    }
                    content.append(line);
                    readLines++;
                }
                // Return lines as string
                return content.toString();
            } catch (IOException exception) {
                Log.v(Constants.TAG, "Failed to load hosts file content.", exception);
                // Return an error
                return "Error: Failed to load hosts file content.";
            }
        }

        @Override
        protected void onPostExecute(String content) {
            // Get callback
            Consumer<String> callback = this.callback.get();
            if (callback != null) {
                // Apply result
                callback.accept(content);
            }
        }
    }
}
