/*
 * Copyright (C) 2011-2012 Dominik Schürmann <dominik@dominikschuermann.de>
 *
 * This file is part of AdAway.
 *
 * AdAway is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AdAway is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AdAway.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.adaway.util;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.io.SuFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.util.List;

import static org.adaway.util.Constants.ANDROID_SYSTEM_ETC_HOSTS;
import static org.adaway.util.Constants.COMMAND_CHCON_SYSTEMFILE;
import static org.adaway.util.Constants.COMMAND_CHMOD_644;
import static org.adaway.util.Constants.COMMAND_CHOWN;
import static org.adaway.util.Constants.COMMAND_LN;
import static org.adaway.util.Constants.COMMAND_RM;
import static org.adaway.util.Constants.TAG;
import static org.adaway.util.MountType.READ_ONLY;
import static org.adaway.util.MountType.READ_WRITE;
import static org.adaway.util.ShellUtils.mergeAllLines;

public class ApplyUtils {
    /**
     * Check if there is enough space on partition where target is located
     *
     * @param size   size of file to put on partition
     * @param target path where to put the file
     * @return true if it will fit on partition of target, false if it will not fit.
     */
    private static boolean hasEnoughSpaceOnPartition(String target, long size) {
        long freeSpace = new SuFile(target).getFreeSpace();
        return (freeSpace == 0 || freeSpace > size);
    }

    /**
     * Check if a path is writable.
     *
     * @param path The path to check.
     * @return <code>true</code> if the path is writable, <code>false</code> otherwise.
     */
    private static boolean isWritable(String path) {
        return new SuFile(path).canWrite();
    }

    /**
     * Checks by reading hosts file if AdAway hosts file is applied or not
     *
     * @return true if it is applied
     */
    public static boolean isHostsFileCorrect(String target) {
        boolean status;

        /* Check if first line in hosts file is AdAway comment */
        SuFile file = new SuFile(target);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            String firstLine = reader.readLine();

            Log.d(TAG, "First line of " + target + ": " + firstLine);

            status = firstLine.equals(Constants.HEADER1);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "FileNotFoundException", e);
            status = true; // workaround for: http://code.google.com/p/ad-away/issues/detail?id=137
        } catch (Exception e) {
            Log.e(TAG, "Exception: ", e);
            status = false;
        }

        return status;
    }

    /**
     * Copy hosts file from private storage of AdAway to internal partition using RootTools
     *
     * @throws NotEnoughSpaceException RemountException CopyException
     */
    public static void copyHostsFile(Context context, String target)
            throws NotEnoughSpaceException, RemountException, CommandException {
        Log.i(TAG, "Copy hosts file with target: " + target);
        String privateDir = context.getFilesDir().getAbsolutePath();
        String privateFile = privateDir + File.separator + Constants.HOSTS_FILENAME;

        // if the target has a trailing slash, it is not a valid target!
        if (target.endsWith("/")) {
            throw new CommandException("Custom target ends with trailing slash, it is not a valid target!");
        }

        SuFile targetFile = new SuFile(target);
        if (!target.equals(ANDROID_SYSTEM_ETC_HOSTS)) {
            /*
             * If custom target like /data/etc/hosts is set, create missing directories for writing
             * this file
             */
            createDirectories(targetFile);
        }

        /* check for space on partition */
        long size = new File(privateFile).length();
        Log.i(TAG, "Size of hosts file: " + size);
        if (!hasEnoughSpaceOnPartition(target, size)) {
            throw new NotEnoughSpaceException();
        }

        /* Execute commands */
        boolean writable = isWritable(target);
        try {
            if (!writable) {
                // remount for write access
                Log.i(TAG, "Remounting for RW...");
                if (!ShellUtils.remountPartition(targetFile, READ_WRITE)) {
                    throw new RemountException("Failed to remount hosts file partition as read-write.");
                }
            }

            if (target.equals(ANDROID_SYSTEM_ETC_HOSTS)) {
                // remove before copying when using /system/etc/hosts
                targetFile.delete();
            }
            // Copy hosts file then set owner and permissions
            Shell.Result result = Shell.su(
                    "dd if=" + privateFile + " of=" + target,
                    COMMAND_CHOWN + " " + target,
                    COMMAND_CHMOD_644 + " " + target
            ).exec();
            if (!result.isSuccess()) {
                throw new CommandException("Failed to copy hosts file: " +mergeAllLines(result.getErr()));
            }
        } finally {
            if (!writable) {
                // after all remount target back as read only
                ShellUtils.remountPartition(targetFile, READ_WRITE);
            }

        }
    }

    /**
     * Create symlink from /system/etc/hosts to target.
     *
     * @param target The target of the symbolic link.
     */
    public static boolean createSymlink(String target) {
        // Mount hosts file partition as read/write
        SuFile hostsFile = new SuFile(ANDROID_SYSTEM_ETC_HOSTS);
        if (!ShellUtils.remountPartition(hostsFile, READ_WRITE)) {
            return false;
        }
        Shell.Result result = Shell.su(
                COMMAND_RM + " " + ANDROID_SYSTEM_ETC_HOSTS,
                COMMAND_LN + " " + target + " " + ANDROID_SYSTEM_ETC_HOSTS,
                COMMAND_CHCON_SYSTEMFILE + " " + target,
                COMMAND_CHOWN + " " + target,
                COMMAND_CHMOD_644 + " " + target
        ).exec();
        boolean success = result.isSuccess();
        if (!success) {
            Log.e(TAG, "Failed to create symbolic link: "+mergeAllLines(result.getErr()));
        }
        // Mount hosts file partition as read only
        ShellUtils.remountPartition(hostsFile, READ_ONLY);
        return success;
    }

    /**
     * Checks whether /system/etc/hosts is a symlink and pointing to the target or not
     */
    public static boolean isSymlinkCorrect(String target) {
        Log.i(TAG, "Checking whether /system/etc/hosts is a symlink and pointing to " + target + " or not.");

        Shell.Result exec = Shell.su("readlink -e " + target).exec();
        if (!exec.isSuccess()) {
            return false;
        }
        List<String> out = exec.getOut();
        if (out.isEmpty()) {
            return false;
        }
        String read = out.get(0);
        Log.d(TAG, "symlink: " + read + "; target: " + target);
        return read.equals(target);
    }

    /**
     * Create directories if missing, if /data/etc/hosts is set as target, this creates /data/etc/
     * directories. Needs RW on partition!
     *
     * @throws CommandException If the directories could not be created.
     */
    private static void createDirectories(SuFile file) throws CommandException {
        SuFile parent = file.getParentFile();
        if (!parent.isDirectory()) {
            String path = parent.getAbsolutePath();
            Shell.Result exec = Shell.su("mkdir -p " + path).exec();
            if (!exec.isSuccess()) {
                throw new CommandException("Failed to create directories: " + path);
            }
        }
    }

    /**
     * Returns true when an APN proxy is set. This means data is routed through this proxy. As a
     * result hostname blocking does not work reliable because images can come from a different
     * hostname!
     *
     * @param context The application context.
     * @return true if proxy is set
     */
    public static boolean isApnProxySet(Context context) {
        boolean result = false; // default to false!

        try {
            final Uri defaultApnUri = Uri.parse("content://telephony/carriers/preferapn");
            final String[] projection = new String[]{"_id", "name", "proxy"};
            // get cursor for default apns
            Cursor cursor = context.getContentResolver().query(defaultApnUri, projection, null,
                    null, null);

            // get default apn
            if (cursor != null) {
                // get columns
                int nameColumn = cursor.getColumnIndex("name");
                int proxyColumn = cursor.getColumnIndex("proxy");

                if (cursor.moveToFirst()) {
                    // get name and proxy
                    String name = cursor.getString(nameColumn);
                    String proxy = cursor.getString(proxyColumn);

                    Log.d(TAG, "APN " + name + " has proxy: " + proxy);

                    // if it contains anything that is not a whitespace
                    if (!proxy.matches("\\s*")) {
                        result = true;
                    }
                }

                cursor.close();
            } else {
                Log.d(TAG, "Could not get APN cursor!");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error while getting default APN!", e);
            // ignore exception, result = false
        }

        return result;
    }
}