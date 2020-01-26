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
import android.os.StatFs;

import androidx.annotation.NonNull;

import org.adaway.helper.PreferenceHelper;
import org.sufficientlysecure.rootcommands.Shell;
import org.sufficientlysecure.rootcommands.Toolbox;
import org.sufficientlysecure.rootcommands.command.SimpleCommand;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

public class ApplyUtils {
    /*
     * Apply modes (see pref pref_apply_method_entries_values).
     */
    public static final String APPLY_TO_SYSTEM = "writeToSystem";
    public static final String APPLY_TO_DATA_DATA = "writeToDataData";
    public static final String APPLY_TO_DATA = "writeToData";
    public static final String APPLY_TO_CUSTOM_TARGET = "customTarget";

    /**
     * Check if there is enough space on partition where target is located
     *
     * @param size   size of file to put on partition
     * @param target path where to put the file
     * @return true if it will fit on partition of target, false if it will not fit.
     */
    private static boolean hasEnoughSpaceOnPartition(String target, long size) {
        try {
            // new File(target).getFreeSpace() (API 9) is not working on data partition
            // get directory without file
            StatFs stat = new StatFs(target);
            long blockSize = stat.getBlockSize();
            long availableBlocks = stat.getAvailableBlocks();
            long availableSpace = availableBlocks * blockSize;

            Log.i(Constants.TAG, "Checking for enough space: Target: " + target + " size: " + size
                    + ", availableSpace: " + availableSpace);

            if (size < availableSpace) {
                return true;
            } else {
                Log.e(Constants.TAG, "Not enough space on partition!");
                return false;
            }
        } catch (Exception e) {
            // if new StatFs(directory) fails catch IllegalArgumentException and just return true as
            // workaround
            Log.e(Constants.TAG, "Problem while getting available space on partition!", e);
            return true;
        }
    }

    /**
     * Check if a path is writable.
     *
     * @param shell The shell used to check if the path is writable.
     * @param path  The path to check.
     * @return <code>true</code> if the path is writable, <code>false</code> otherwise.
     */
    private static boolean isWritable(Shell shell, String path) {
        SimpleCommand touchCommand = new SimpleCommand("touch " + path);
        try {
            shell.add(touchCommand).waitForFinish();
        } catch (Exception e) {
            Log.e(Constants.TAG, "Problem while checking if writable.", e);
            return false;
        }
        return touchCommand.getExitCode() == 0;
    }

    /**
     * Checks by reading hosts file if AdAway hosts file is applied or not
     *
     * @return true if it is applied
     */
    public static boolean isHostsFileCorrect(String target) {
        boolean status;

        /* Check if first line in hosts file is AdAway comment */
        File file = new File(target);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            String firstLine = reader.readLine();

            Log.d(Constants.TAG, "First line of " + target + ": " + firstLine);

            status = firstLine.equals(Constants.HEADER1);
        } catch (FileNotFoundException e) {
            Log.e(Constants.TAG, "FileNotFoundException", e);
            status = true; // workaround for: http://code.google.com/p/ad-away/issues/detail?id=137
        } catch (Exception e) {
            Log.e(Constants.TAG, "Exception: ", e);
            status = false;
        }

        return status;
    }

    /**
     * Copy hosts file from private storage of AdAway to internal partition using RootTools
     *
     * @throws NotEnoughSpaceException RemountException CopyException
     */
    public static void copyHostsFile(Context context, String target, Shell shell)
            throws NotEnoughSpaceException, RemountException, CommandException {
        Log.i(Constants.TAG, "Copy hosts file with target: " + target);
        String privateDir = context.getFilesDir().getAbsolutePath();
        String privateFile = privateDir + File.separator + Constants.HOSTS_FILENAME;

        // if the target has a trailing slash, it is not a valid target!
        if (target.endsWith("/")) {
            throw new CommandException("Custom target ends with trailing slash, it is not a valid target!");
        }

        if (!target.equals(Constants.ANDROID_SYSTEM_ETC_HOSTS)) {
            /*
             * If custom target like /data/etc/hosts is set, create missing directories for writing
             * this file
             */
            createDirectories(target, shell);
        }

        /* check for space on partition */
        long size = new File(privateFile).length();
        Log.i(Constants.TAG, "Size of hosts file: " + size);
        if (!hasEnoughSpaceOnPartition(target, size)) {
            throw new NotEnoughSpaceException();
        }

        Toolbox tb = new Toolbox(shell);
        boolean writable = isWritable(shell, target);
        /* Execute commands */
        try {
            if (!writable) {
                // remount for write access
                Log.i(Constants.TAG, "Remounting for RW...");
                if (!tb.remount(target, "RW")) {
                    throw new RemountException("Remounting as RW failed! Probably not a problem!");
                }
            }

            if (target.equals(Constants.ANDROID_SYSTEM_ETC_HOSTS)) {
                // remove before copying when using /system/etc/hosts
                SimpleCommand command = new SimpleCommand(Constants.COMMAND_RM + " " + target);
                shell.add(command).waitForFinish();
            }
            // copy file
            if (!tb.copyFile(privateFile, target, false, false)) {
                throw new CommandException();
            }

            // execute commands: chown, chmod
            SimpleCommand command = new SimpleCommand(Constants.COMMAND_CHOWN + " " + target,
                    Constants.COMMAND_CHMOD_644 + " " + target);
            shell.add(command).waitForFinish();
        } catch (IOException | TimeoutException exception) {
            Log.e(Constants.TAG, "Exception!", exception);

            throw new CommandException("Failed to copy hosts file.", exception);
        } finally {
            if (!writable) {
                // after all remount target back as read only
                Log.i(Constants.TAG, "Remounting back to RO...");
                if (!tb.remount(target, "RO")) {
                    Log.e(Constants.TAG, "Remounting as RO failed! Probably not a problem!");
                }
            }

        }
    }

    /**
     * Create symlink from /system/etc/hosts to /data/data/hosts
     *
     * @throws RemountException CommandException
     */
    public static void createSymlink(String target) throws RemountException, CommandException {
        Shell rootShell;
        try {
            rootShell = Shell.startRootShell();
        } catch (Exception e) {
            throw new CommandException("Problem opening root shell!");
        }
        Toolbox tb = new Toolbox(rootShell);

        /* remount /system/etc for write access */
        if (!tb.remount(Constants.ANDROID_SYSTEM_ETC_HOSTS, "RW")) {
            throw new RemountException();
        }

        /* Execute commands */
        try {
            // create symlink
            SimpleCommand command = new SimpleCommand(Constants.COMMAND_RM + " "
                    + Constants.ANDROID_SYSTEM_ETC_HOSTS, Constants.COMMAND_LN + " " + target + " "
                    + Constants.ANDROID_SYSTEM_ETC_HOSTS,
                    Constants.COMMAND_CHCON_SYSTEMFILE + " " + target,
                    Constants.COMMAND_CHOWN + " " + target,
                    Constants.COMMAND_CHMOD_644 + " " + target
            );

            rootShell.add(command).waitForFinish();
        } catch (Exception exception) {
            throw new CommandException("Failed to create symbolic link.", exception);
        } finally {
            // after all remount system back as read only
            tb.remount(Constants.ANDROID_SYSTEM_ETC_HOSTS, "RO");

            try {
                rootShell.close();
            } catch (IOException e) {
                Log.w(Constants.TAG, "Failed to close shell.");
            }
        }
    }

    /**
     * Checks whether /system/etc/hosts is a symlink and pointing to the target or not
     */
    public static boolean isSymlinkCorrect(String target, Shell shell) {
        Log.i(Constants.TAG, "Checking whether /system/etc/hosts is a symlink and pointing to "
                + target + " or not.");

        Toolbox tb = new Toolbox(shell);
        String symlink;
        try {
            symlink = tb.getSymlink(Constants.ANDROID_SYSTEM_ETC_HOSTS);
        } catch (Exception e) {
            Log.e(Constants.TAG, "Problem getting symlink!", e);
            return false;
        }

        Log.d(Constants.TAG, "symlink: " + symlink + "; target: " + target);

        return symlink != null && symlink.equals(target);
    }

    /**
     * Create directories if missing, if /data/etc/hosts is set as target, this creates /data/etc/
     * directories. Needs RW on partition!
     *
     * @throws CommandException If the directories could not be created.
     */
    private static void createDirectories(String target, Shell shell) throws CommandException {
        /* Execute commands */
        try {
            // get directory without file
            String directory = new File(target).getParent();

            // create directories
            try {
                SimpleCommand mkdirCommand = new SimpleCommand(Constants.COMMAND_MKDIR + " "
                        + directory);

                shell.add(mkdirCommand).waitForFinish();
            } catch (Exception e) {
                Log.e(Constants.TAG, "Mkdir Exception", e);
            }
        } catch (Exception e) {
            throw new CommandException("Failed to create directories.", e);
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

                    Log.d(Constants.TAG, "APN " + name + " has proxy: " + proxy);

                    // if it contains anything that is not a whitespace
                    if (!proxy.matches("\\s*")) {
                        result = true;
                    }
                }

                cursor.close();
            } else {
                Log.d(Constants.TAG, "Could not get APN cursor!");
            }
        } catch (Exception e) {
            Log.e(Constants.TAG, "Error while getting default APN!", e);
            // ignore exception, result = false
        }

        return result;
    }

    /**
     * Returns the Android hosts file name.
     *
     * @return the current hosts file name.
     * @throws IllegalStateException if the apply method is unknown.
     */
    @NonNull
    public static String getAndroidHostsFilename(@NonNull Context context) {
        String applyMethod = PreferenceHelper.getApplyMethod(context);
        switch (applyMethod) {
            case APPLY_TO_SYSTEM:
                // /system/etc/hosts
                return Constants.ANDROID_SYSTEM_ETC_HOSTS;
            case APPLY_TO_DATA_DATA:
                // /data/data/hosts
                return Constants.ANDROID_DATA_DATA_HOSTS;
            case APPLY_TO_DATA:
                // /data/hosts
                return Constants.ANDROID_DATA_HOSTS;
            case APPLY_TO_CUSTOM_TARGET:
                return PreferenceHelper.getCustomTarget(context);
            default:
                throw new IllegalStateException("The apply method " + applyMethod + " is not supported.");
        }
    }

    /**
     * Read a text file line by line into a String list.
     *
     * @param inputStream InputStream to read the file content from
     * @return File content as String list
     * @throws IOException If the file could not be read.
     */
    @NonNull
    public static List<String> readTextFile(@NonNull InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            List<String> result = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                result.add(line);
            }
            return result;
        }
    }
}