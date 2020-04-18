package org.adaway.vpn;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import android.util.Log;

import java.io.Closeable;
import java.io.FileDescriptor;

/**
 * Utility class for working with files.
 */

final class FileHelper {

//    /**
//     * Try open the file with {@link Context#openFileInput(String)}, falling back to a file of
//     * the same name in the assets.
//     */
//    public static InputStream openRead(Context context, String filename) throws IOException {
//        try {
//            return context.openFileInput(filename);
//        } catch (FileNotFoundException e) {
//            return context.getAssets().open(filename);
//        }
//    }

//    /**
//     * Write to the given file in the private files dir, first renaming an old one to .bak
//     *
//     * @param context  A context
//     * @param filename A filename as for @{link {@link Context#openFileOutput(String, int)}}
//     * @return See @{link {@link Context#openFileOutput(String, int)}}
//     * @throws IOException See @{link {@link Context#openFileOutput(String, int)}}
//     */
//    public static OutputStream openWrite(Context context, String filename) throws IOException {
//        File out = context.getFileStreamPath(filename);
//
//        // Create backup
//        out.renameTo(context.getFileStreamPath(filename + ".bak"));
//
//        return context.openFileOutput(filename, Context.MODE_PRIVATE);
//    }

//    private static Configuration readConfigFile(Context context, String name, boolean defaultsOnly) throws IOException {
//        InputStream stream;
//        if (defaultsOnly)
//            stream = context.getAssets().open(name);
//        else
//            stream = FileHelper.openRead(context, name);
//
//        return Configuration.read(new InputStreamReader(stream));
//    }
//
//    public static Configuration loadCurrentSettings(Context context) {
//        try {
//            return readConfigFile(context, "settings.json", false);
//        } catch (Exception e) {
//            Toast.makeText(context, context.getString(R.string.cannot_read_config, e.getLocalizedMessage()), Toast.LENGTH_LONG).show();
//            return loadPreviousSettings(context);
//        }
//    }
//
//    public static Configuration loadPreviousSettings(Context context) {
//        try {
//            return readConfigFile(context, "settings.json.bak", false);
//        } catch (Exception e) {
//            Toast.makeText(context, context.getString(R.string.cannot_restore_previous_config, e.getLocalizedMessage()), Toast.LENGTH_LONG).show();
//            return loadDefaultSettings(context);
//        }
//    }
//
//    public static Configuration loadDefaultSettings(Context context) {
//        try {
//            return readConfigFile(context, "settings.json", true);
//        } catch (Exception e) {
//            Toast.makeText(context, context.getString(R.string.cannot_load_default_config, e.getLocalizedMessage()), Toast.LENGTH_LONG).show();
//            return null;
//        }
//    }
//
//    public static void writeSettings(Context context, Configuration config) {
//        Log.d("FileHelper", "writeSettings: Writing the settings file");
//        try {
//            Writer writer = new OutputStreamWriter(FileHelper.openWrite(context, "settings.json"));
//            config.write(writer);
//            writer.close();
//        } catch (IOException e) {
//            Toast.makeText(context, context.getString(R.string.cannot_write_config, e.getLocalizedMessage()), Toast.LENGTH_SHORT).show();
//        }
//    }
//
//    /**
//     * Returns a file where the item should be downloaded to.
//     *
//     * @param context A context to work in
//     * @param item    A configuration item.
//     * @return File or null, if that item is not downloadable.
//     */
//    public static File getItemFile(Context context, Configuration.Item item) {
//        if (item.isDownloadable()) {
//            try {
//                return new File(context.getExternalFilesDir(null), java.net.URLEncoder.encode(item.location, "UTF-8"));
//            } catch (UnsupportedEncodingException e) {
//                e.printStackTrace();
//                return null;
//            }
//        } else {
//            return null;
//        }
//    }
//
//    public static InputStreamReader openItemFile(Context context, Configuration.Item item) throws FileNotFoundException {
//        if (item.location.startsWith("content://")) {
//            try {
//                return new InputStreamReader(context.getContentResolver().openInputStream(Uri.parse(item.location)));
//            } catch (SecurityException e) {
//                Log.d("FileHelper", "openItemFile: Cannot open", e);
//                throw new FileNotFoundException(e.getMessage());
//            }
//        } else {
//            File file = getItemFile(context, item);
//            if (file == null)
//                return null;
//            if (item.isDownloadable())
//                return new InputStreamReader(new SingleWriterMultipleReaderFile(getItemFile(context, item)).openRead());
//            return new FileReader(getItemFile(context, item));
//        }
//    }

//    /**
//     * Wrapper around {@link Os#poll(StructPollfd[], int)} that automatically restarts on EINTR
//     * While post-Lollipop devices handle that themselves, we need to do this for Lollipop.
//     *
//     * @param fds     Descriptors and events to wait on
//     * @param timeout Timeout. Should be -1 for infinite, as we do not lower the timeout when
//     *                retrying due to an interrupt
//     * @return The number of fds that have events
//     * @throws ErrnoException See {@link Os#poll(StructPollfd[], int)}
//     */
//    static int poll(StructPollfd[] fds, int timeout) throws ErrnoException, InterruptedException {
//        while (true) {
//            if (Thread.interrupted())
//                throw new InterruptedException();
//            try {
//                return Os.poll(fds, timeout);
//            } catch (ErrnoException e) {
//                if (e.errno == OsConstants.EINTR)
//                    continue;
//                throw e;
//            }
//        }
//    }

    static FileDescriptor closeOrWarn(FileDescriptor fd, String tag, String message) {
        try {
            if (fd != null)
                Os.close(fd);
        } catch (ErrnoException e) {
            Log.e(tag, "closeOrWarn: " + message, e);
        }
        return null;
    }

    static <T extends Closeable> T closeOrWarn(T fd, String tag, String message) {
        try {
            if (fd != null)
                fd.close();
        } catch (Exception e) {
            Log.e(tag, "closeOrWarn: " + message, e);
        }
        return null;
    }
}
