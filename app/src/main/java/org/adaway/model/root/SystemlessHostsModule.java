package org.adaway.model.root;

import static com.topjohnwu.superuser.ShellUtils.escapedString;
import static org.adaway.model.root.ShellUtils.mergeAllLines;
import static org.adaway.util.Constants.ANDROID_SYSTEM_ETC_HOSTS;

import com.topjohnwu.superuser.Shell;

import timber.log.Timber;

/**
 * Manage an AdAway owned systemless module providing {@code /system/etc/hosts}.
 * <p>
 * The module layout ({@code /data/adb/modules/<id>/module.prop} + {@code system/...}) is shared by
 * Magisk, KernelSU and APatch, so the same code works for all of them.
 * <ul>
 *   <li>The module makes the hosts file persistent across reboots (mounted by the root manager at boot).</li>
 *   <li>A bind mount in the init mount namespace applies the change immediately, without reboot.</li>
 * </ul>
 */
final class SystemlessHostsModule {
    static final String MODULE_ID = "adaway_hosts";
    private static final String MODULE_DIR = "/data/adb/modules/" + MODULE_ID;
    private static final String MODULE_HOSTS = MODULE_DIR + "/system/etc/hosts";
    private static final String SELINUX_CONTEXT = "u:object_r:system_file:s0";

    private SystemlessHostsModule() {
    }

    /**
     * Write the hosts file into the module, then try to apply it live.
     *
     * @param sourceFile The hosts file to install (readable by root).
     * @throws CommandException If the module could not be written.
     */
    static void install(String sourceFile) throws CommandException {
        Shell.Result result = Shell.cmd(
                "mkdir -p " + escapedString(MODULE_DIR + "/system/etc"),
                "printf '%s\\n' 'id=" + MODULE_ID + "' 'name=AdAway systemless hosts'"
                        + " 'version=1' 'versionCode=1' 'author=AdAway'"
                        + " 'description=Hosts file managed by AdAway. Do not edit.' > "
                        + escapedString(MODULE_DIR + "/module.prop"),
                // Rewrite in place (keep the inode) so an existing mount sees the new content
                "cat " + escapedString(sourceFile) + " > " + escapedString(MODULE_HOSTS),
                "chmod 644 " + escapedString(MODULE_HOSTS),
                "chown 0:0 " + escapedString(MODULE_HOSTS)
        ).exec();
        if (!result.isSuccess()) {
            throw new CommandException("Failed to write systemless hosts module: " + mergeAllLines(result.getErr()));
        }
        // Best effort, some ROMs do not support chcon
        Shell.cmd("chcon " + SELINUX_CONTEXT + " " + escapedString(MODULE_HOSTS)).exec();

        if (!bindLive()) {
            Timber.w("Hosts module written but live bind mount failed. A reboot is required to apply it.");
        }
    }

    /**
     * Bind mount the module hosts file over the system one, in the init mount namespace
     * (the one the DNS resolver lives in).
     *
     * @return <code>true</code> if the bind mount succeeded.
     */
    private static boolean bindLive() {
        String script = "while grep -q ' " + ANDROID_SYSTEM_ETC_HOSTS + " ' /proc/mounts; do"
                + " umount -l " + ANDROID_SYSTEM_ETC_HOSTS + " || break; done;"
                + " mount --bind " + MODULE_HOSTS + " " + ANDROID_SYSTEM_ETC_HOSTS;
        String quoted = escapedString(script);
        Shell.Result result = Shell.cmd(
                "if command -v nsenter >/dev/null 2>&1; then"
                        + " nsenter --mount=/proc/1/ns/mnt -- sh -c " + quoted + ";"
                        + " else sh -c " + quoted + "; fi"
        ).exec();
        if (!result.isSuccess()) {
            Timber.w("Bind mount failed: %s.", mergeAllLines(result.getErr()));
        }
        return result.isSuccess();
    }
}
