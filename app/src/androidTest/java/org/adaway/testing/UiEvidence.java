package org.adaway.testing;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;

import androidx.test.platform.app.InstrumentationRegistry;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Optional screenshots of asserted UI states, enabled with the evidenceDir runner argument. */
public final class UiEvidence {
    private UiEvidence() {
    }

    public static void capture(Context context, String name) {
        String directoryName = InstrumentationRegistry.getArguments().getString("evidenceDir");
        if (directoryName == null || directoryName.isEmpty()) {
            return;
        }
        if (!directoryName.matches("[A-Za-z0-9_-][A-Za-z0-9._-]*")) {
            throw new AssertionError("evidenceDir must be a single directory name");
        }
        File externalFiles = context.getExternalFilesDir(null);
        if (externalFiles == null) {
            throw new AssertionError("External files directory is unavailable for UI evidence");
        }
        File directory = new File(externalFiles, directoryName);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new AssertionError("Cannot create UI evidence directory: " + directory);
        }

        // Let the asserted state render and snackbar entrance animations finish. Waiting for
        // global UI idle can hang while an indefinite snackbar is visible.
        SystemClock.sleep(350);
        Bitmap screenshot = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().takeScreenshot();
        if (screenshot == null) {
            throw new AssertionError("Cannot capture UI evidence: " + name);
        }
        File target = new File(directory, name + ".png");
        try (FileOutputStream output = new FileOutputStream(target)) {
            if (!screenshot.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw new AssertionError("Cannot encode UI evidence: " + target);
            }
        } catch (IOException exception) {
            throw new AssertionError("Cannot write UI evidence: " + target, exception);
        } finally {
            screenshot.recycle();
        }
        System.out.println("UI evidence: " + target.getAbsolutePath());
    }
}
