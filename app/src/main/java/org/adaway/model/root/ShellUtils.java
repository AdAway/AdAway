package org.adaway.model.root;

import static com.topjohnwu.superuser.ShellUtils.escapedString;

import android.content.Context;

import com.topjohnwu.superuser.Shell;

import java.io.File;
import java.util.List;
import java.util.Optional;

import timber.log.Timber;

/**
 * This class is an utility class to help with shell commands.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public final class ShellUtils {
    private static final String EXECUTABLE_PREFIX = "lib";
    private static final String EXECUTABLE_SUFFIX = "_exec.so";

    /**
     * Private constructor.
     */
    private ShellUtils() {

    }

    public static String mergeAllLines(List<String> lines) {
        return String.join("\n", lines);
    }

    public static boolean isBundledExecutableRunning(String executable) {
        return Shell.cmd("ps -A | grep " + EXECUTABLE_PREFIX + executable + EXECUTABLE_SUFFIX).exec().isSuccess();
    }

    public static boolean runBundledExecutable(Context context, String executable, String parameters) {
        String nativeLibraryDir = context.getApplicationInfo().nativeLibraryDir;
        String command = "LD_LIBRARY_PATH=" + nativeLibraryDir + " " +
                nativeLibraryDir + File.separator + EXECUTABLE_PREFIX + executable + EXECUTABLE_SUFFIX + " " +
                parameters + " &";
        return Shell.cmd(command).exec().isSuccess();
    }

    public static void killBundledExecutable(String executable) {
        Shell.cmd("killall " + EXECUTABLE_PREFIX + executable + EXECUTABLE_SUFFIX).exec();
    }



    /**
     * Check if a path is writable.
     *
     * @param file The file to check.
     * @return <code>true</code> if the path is writable, <code>false</code> otherwise.
     */
    public static boolean isWritable(File file) {
        // Check first if file can be written without privileges
        if (file.canWrite()) {
            return true;
        }
        return Shell.cmd("test -w " + escapedString(file.getAbsolutePath()))
                .exec()
                .isSuccess();
    }

    public static boolean remountPartition(File file, MountType type) {
        Optional<String> partitionOptional = findPartition(file);
        if (!partitionOptional.isPresent()) {
            return false;
        }
        String partition = partitionOptional.get();
        Shell.Result result = Shell.cmd("mount -o " + type.getOption() + ",remount " + partition).exec();
        boolean success = result.isSuccess();
        if (!success) {
            Timber.w("Failed to remount partition %s as %s: %s.", partition, type.getOption(), mergeAllLines(result.getErr()));
        }
        return success;
    }

    private static Optional<String> findPartition(File file) {
        // Get mount points
        Shell.Result result = Shell.cmd("cat /proc/mounts | cut -d ' ' -f2").exec();
        List<String> out = result.getOut();
        // Check file and each parent against mount points
        while (file != null) {
            String path = file.getAbsolutePath();
            for (String mount : out) {
                if (path.equals(mount)) {
                    return Optional.of(mount);
                }
            }
            file = file.getParentFile();
        }
        return Optional.empty();
    }
}
