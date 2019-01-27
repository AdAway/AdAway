package org.adaway.ui.hostscontent;

import android.os.AsyncTask;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.adaway.R;
import org.adaway.helper.OpenHelper;
import org.adaway.util.Constants;
import org.adaway.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.function.Consumer;

/**
 * This class is a {@link Fragment} to explains and display the hosts files.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class HostsContentFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        /*
         * Create view.
         */
        // Retrieve activity
        FragmentActivity activity = this.getActivity();
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
        openFileButton.setOnClickListener(button -> OpenHelper.openHostsFile(activity));
        // Return created view
        return view;
    }

    /**
     * This interface is a backport of {@link Consumer} for lower Android API.
     */
    private interface SupportStringConsumer {
        /**
         * Consume the string.
         *
         * @param string The string to consume.
         * @see Consumer#accept(Object)
         */
        void acceptString(String string);
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
        private final WeakReference<SupportStringConsumer> callback;

        /**
         * Constructor.
         *
         * @param callback The callback to call with the hosts file content.
         */
        private HostsContentLoader(SupportStringConsumer callback) {
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
            SupportStringConsumer callback = this.callback.get();
            if (callback != null) {
                // Apply result
                callback.acceptString(content);
            }
        }
    }
}
