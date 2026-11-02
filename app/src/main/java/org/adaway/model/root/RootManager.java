package org.adaway.model.root;

import com.topjohnwu.superuser.Shell;

import timber.log.Timber;

/**
 * The root solutions AdAway knows how to recognise.
 * <p>
 * Detection is informational (UI, logs). The hosts installation does not depend on it:
 * the systemless module layout is the same for all of them.
 */
public enum RootManager {
    MAGISK("Magisk"),
    KERNELSU("KernelSU"),
    APATCH("APatch"),
    UNKNOWN("Unknown");

    private final String displayName;

    RootManager(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    /**
     * Whether the root solution has no built-in "systemless hosts" feature
     * and therefore requires AdAway to ship its own module.
     */
    public boolean needsOwnModule() {
        return this == KERNELSU || this == APATCH;
    }

    /**
     * Detect the root manager. Must be called from a background thread.
     * APatch is checked before KernelSU as both can expose KernelSU-like paths.
     */
    public static RootManager detect() {
        Shell.Result result = Shell.cmd(
                "if [ -d /data/adb/ap ] || command -v apd >/dev/null 2>&1; then echo apatch;"
                        + " elif [ -d /data/adb/ksu ] || command -v ksud >/dev/null 2>&1; then echo kernelsu;"
                        + " elif [ -d /data/adb/magisk ] || command -v magisk >/dev/null 2>&1; then echo magisk;"
                        + " else echo unknown; fi"
        ).exec();
        String out = result.getOut().isEmpty() ? "" : result.getOut().get(0).trim();
        RootManager manager;
        switch (out) {
            case "apatch":
                manager = APATCH;
                break;
            case "kernelsu":
                manager = KERNELSU;
                break;
            case "magisk":
                manager = MAGISK;
                break;
            default:
                manager = UNKNOWN;
        }
        Timber.i("Detected root manager: %s.", manager);
        return manager;
    }
}
