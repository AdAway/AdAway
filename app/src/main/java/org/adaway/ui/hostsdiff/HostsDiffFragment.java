package org.adaway.ui.hostsdiff;

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.difflib.DiffUtils;
import com.github.difflib.algorithm.DiffAlgorithmListener;
import com.github.difflib.algorithm.DiffException;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;

import org.adaway.R;
import org.adaway.util.ApplyUtils;
import org.adaway.util.Constants;
import org.adaway.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Shows the hosts diff from the latest update.
 */
public class HostsDiffFragment extends Fragment implements CallbackInterface {

    private View view;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.hosts_diff_fragment, container, false);
        // Generate and show hosts diff
        try {
            String androidHostsFilename = ApplyUtils.getAndroidHostsFilename(view.getContext());
            FileInputStream androidHostsInputStream = new FileInputStream(new File(androidHostsFilename));
            FileInputStream privateHostsInputStream = view.getContext().openFileInput(Constants.HOSTS_BACKUP_FILENAME);
            new HostsDiffGenerator(androidHostsInputStream, privateHostsInputStream, this).execute();
        } catch (FileNotFoundException e) {
            Log.i(Constants.TAG, "Hosts backup file doesn't exist.", e);
            setResult("Error: Hosts backup file doesn't exist.");
        }
        return view;
    }

    /**
     * Show the progress bar.
     */
    public void showProgressBar(int progress, int max) {
        if (view == null) {
            return;
        }
        ProgressBar progressBar = view.findViewById(R.id.diff_progressbar);
        progressBar.setIndeterminate(false);
        progressBar.setMax(max);
        progressBar.setProgress(progress);
        progressBar.setVisibility(View.VISIBLE);
    }

    /**
     * Hide the progress bar.
     */
    public void hideProgressBar() {
        if (view == null) {
            return;
        }
        ProgressBar progressBar = view.findViewById(R.id.diff_progressbar);
        progressBar.setVisibility(View.GONE);
    }

    /**
     * Show the diff result.
     */
    public void setResult(@NonNull String string) {
        if (view == null) {
            return;
        }
        TextView hostsDiffTextView = view.findViewById(R.id.hosts_diff_text);
        hostsDiffTextView.setText(string);
    }

    /**
     * This class is a {@link AsyncTask} to calculate the hosts diff.
     */
    private static class HostsDiffGenerator extends AsyncTask<Void, Integer, String> {

        /**
         * InputStream to read the previous hosts file.
         */
        @NonNull
        private final InputStream oldHostsFileInputStream;

        /**
         * InputStream to read the current hosts file.
         */
        @NonNull
        private final InputStream newHostsFileInputStream;

        /**
         * The maximum number of lines of the result.
         */
        private static final short MAX_LINES = 50;

        /**
         * Line counter to limit the length of the result.
         */
        private int lineCount = 0;

        /**
         * The callback to call with the hosts file content.
         */
        @NonNull
        private final WeakReference<CallbackInterface> callback;

        /**
         * Constructor.
         *
         * @param callback The callback to call with the hosts file content.
         */
        private HostsDiffGenerator(@NonNull InputStream oldHostsFileInputStream, @NonNull InputStream newHostsFileInputStream, @NonNull CallbackInterface callback) {
            this.oldHostsFileInputStream = oldHostsFileInputStream;
            this.newHostsFileInputStream = newHostsFileInputStream;
            this.callback = new WeakReference<>(callback);
        }

        @Override
        protected String doInBackground(Void... params) {
            try {
                // Read hosts files
                List<String> oldHostsLines = ApplyUtils.readTextFile(oldHostsFileInputStream);
                List<String> newHostsLines = ApplyUtils.readTextFile(newHostsFileInputStream);
                // Calculate diff
                DiffAlgorithmListener diffAlgorithmListener = new DiffAlgorithmListener() {
                    @Override
                    public void diffStart() {
                        Log.d(Constants.TAG, "Calculating hosts diff started");
                    }

                    @Override
                    public void diffStep(int value, int max) {
                        Log.d(Constants.TAG, "Calculating hosts diff: Step " + value + "/" + max);
                        publishProgress(value, max);
                    }

                    @Override
                    public void diffEnd() {
                        Log.d(Constants.TAG, "Calculating hosts diff finished");
                    }
                };
                Patch<String> patch = DiffUtils.diff(oldHostsLines, newHostsLines, diffAlgorithmListener);
                // Generate diff result String representation
                return generateDiffResult(patch);
            } catch (IOException e) {
                Log.i(Constants.TAG, "Failed to load hosts file content.", e);
                return "Error: Failed to load hosts file content.";
            } catch (DiffException e) {
                Log.i(Constants.TAG, "Error while calculating diff between old and new hosts file.", e);
                return "Error while calculating diff between old and new hosts file.";
            }
        }

        /**
         * Generate diff result String representation.
         */
        @NonNull
        private String generateDiffResult(@NonNull Patch<String> patch) {
            StringWriter stringWriter = new StringWriter();
            PrintWriter writer = new PrintWriter(stringWriter);
            for (AbstractDelta<String> delta : patch.getDeltas()) {
                printLine("Source position: " + delta.getSource().getPosition(), writer);
                switch (delta.getType()) {
                    case CHANGE:
                        printLines("-", delta.getSource().getLines(), writer);
                        printLines("+", delta.getTarget().getLines(), writer);
                        break;
                    case DELETE:
                        printLines("-", delta.getSource().getLines(), writer);
                        break;
                    case INSERT:
                        printLines("+", delta.getTarget().getLines(), writer);
                        break;
                    case EQUAL:
                    default:
                        // hide unchanged lines, show only differences
                }
            }
            if (lineCount >= MAX_LINES) {
                writer.println("...");
            }
            return stringWriter.toString();
        }

        /**
         * Writes lines with prefix to a PrintWriter.
         *
         * @param prefix Write prefix in front of every line
         * @param lines  List of line to write
         * @param writer PrintWriter to write the output to
         */
        private void printLines(@NonNull String prefix, @NonNull List<String> lines, @NonNull PrintWriter writer) {
            for (String line : lines) {
                printLine(prefix + line, writer);
            }
        }

        /**
         * Write a single line if the maximum line count is not hit.
         *
         * @param line   Line to write
         * @param writer PrintWriter to write the output to
         */
        private void printLine(@NonNull String line, @NonNull PrintWriter writer) {
            if (lineCount >= MAX_LINES) {
                return;
            }
            writer.println(line);
            lineCount++;
        }

        @Override
        protected void onProgressUpdate (Integer... values) {
            if (values.length != 2) {
                return;
            }
            CallbackInterface callbackInterface = HostsDiffGenerator.this.callback.get();
            if (callbackInterface != null) {
                callbackInterface.showProgressBar(values[0], values[1]);
            }
        }

        @Override
        protected void onPostExecute(String content) {
            CallbackInterface callbackInterface = this.callback.get();
            if (callbackInterface != null) {
                callbackInterface.hideProgressBar();
                callbackInterface.setResult(content);
            }
        }
    }
}
