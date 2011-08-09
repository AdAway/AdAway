/*
 * Copyright (C) 2011 Dominik Schürmann <dominik@dominikschuermann.de>
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

package org.adaway.helper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import org.adaway.utils.Constants;
import org.adaway.utils.CommandException;
import org.adaway.utils.NotEnoughSpaceException;
import org.adaway.utils.RemountException;

import com.stericson.RootTools.RootTools;
import com.stericson.RootTools.RootToolsException;

import android.content.Context;
import android.os.StatFs;
import android.util.Log;

public class ApplyHelper {
    /**
     * Check if there is enough space on internal partition
     * 
     * @param size
     *            size of file to put on partition
     * @param path
     *            path where to put the file
     * 
     * @return <code>true</code> if it will fit on partition of <code>path</code>,
     *         <code>false</code> if it will not fit.
     */
    public static boolean hasEnoughSpaceOnPartition(String path, long size) {
        StatFs stat = new StatFs(path);
        long blockSize = stat.getBlockSize();
        long availableBlocks = stat.getAvailableBlocks();

        if (size < availableBlocks * blockSize) {
            return true;
        } else {
            Log.e(Constants.TAG, "Not enough space on partition!");
            return false;
        }
    }

    /**
     * Checks by reading hosts file if AdAway hosts file is applied or not
     * 
     * @return true if it is applied
     */
    public static boolean isHostsFileApplied(Context context, String targetPath) {
        boolean status = false;

        /* Check if lastModified in database is 0 */

        // get last modified from db
        DatabaseHelper taskDatabaseHelper = new DatabaseHelper(context);
        long lastModifiedDatabase = taskDatabaseHelper.getLastModified();
        taskDatabaseHelper.close();

        if (lastModifiedDatabase == 0) {
            status = false;
        } else {
            status = true;
        }

        /* Check if first line in $targetPath/hosts is AdAway comment */
        String hostsFile = targetPath + File.separator + Constants.HOSTS_FILENAME;

        File file = new File(hostsFile);
        InputStream stream = null;
        InputStreamReader in = null;
        BufferedReader br = null;
        try {
            stream = new FileInputStream(file);
            in = new InputStreamReader(stream);
            br = new BufferedReader(in);

            String firstLine = br.readLine();

            Log.d(Constants.TAG, "firstLine: " + firstLine);

            if (firstLine.equals(Constants.HEADER1)) {
                status = true;
            } else {
                status = false;
            }
        } catch (Exception e) {
            Log.e(Constants.TAG, "Exception: " + e);
            e.printStackTrace();
            status = false;
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    Log.e(Constants.TAG, "Exception: " + e);
                    e.printStackTrace();
                }
            }
        }

        return status;
    }

    /**
     * Copy hosts file from private storage of AdAway to internal partition using RootTools
     * 
     * @throws NotEnoughSpaceException
     *             RemountException CopyException
     */
    public static void copyHostsFile(Context context, boolean targetDataData)
            throws NotEnoughSpaceException, RemountException, CommandException {
        String privateDir = context.getFilesDir().getAbsolutePath();
        String privateFile = privateDir + File.separator + Constants.HOSTS_FILENAME;
        String SystemEtcHosts = Constants.ANDROID_SYSTEM_ETC_PATH + File.separator
                + Constants.HOSTS_FILENAME;
        String DataDataHosts = Constants.ANDROID_DATA_DATA_PATH + File.separator
                + Constants.HOSTS_FILENAME;

        String commandCopySystemEtc = Constants.COMMAND_COPY + " " + privateFile + " "
                + SystemEtcHosts;
        String commandCopyDataData = Constants.COMMAND_COPY + " " + privateFile + " "
                + DataDataHosts;
        String commandChownSystemEtcHosts = Constants.COMMAND_CHOWN + " " + SystemEtcHosts;
        String commandChmodSystemEtcHosts644 = Constants.COMMAND_CHMOD_644 + " " + SystemEtcHosts;
        String commandChmodDataDataHosts666 = Constants.COMMAND_CHMOD_666 + " " + DataDataHosts;

        String targetPath = null;
        if (!targetDataData) {
            targetPath = Constants.ANDROID_SYSTEM_ETC_PATH;
        } else {
            targetPath = Constants.ANDROID_DATA_DATA_PATH;
        }

        /* check for space on partition */
        long size = new File(privateFile).length();
        Log.d(Constants.TAG, "size: " + size);
        if (!hasEnoughSpaceOnPartition(targetPath, size)) {
            throw new NotEnoughSpaceException();
        }

        /* remount for write access */
        if (!RootTools.remount(targetPath, "RW")) {
            throw new RemountException();
        }

        /* Execute commands */
        List<String> output = null;
        try {
            if (!targetDataData) {
                // execute commands: copy, chown, chmod
                output = RootTools.sendShell(new String[] { commandCopySystemEtc,
                        commandChownSystemEtcHosts, commandChmodSystemEtcHosts644 }, 1);
            } else {
                // execute copy
                output = RootTools.sendShell(new String[] { commandCopyDataData,
                        commandChmodDataDataHosts666 }, 1);
            }
            Log.d(Constants.TAG, "output of sendShell commands: " + output.toString());
        } catch (IOException e) {
            Log.e(Constants.TAG, "Exception: " + e);
            e.printStackTrace();

            throw new CommandException();
        } catch (InterruptedException e) {
            Log.e(Constants.TAG, "Exception: " + e);
            e.printStackTrace();

            throw new CommandException();
        } catch (RootToolsException e) {
            Log.e(Constants.TAG, "Exception: " + e);
            e.printStackTrace();

            throw new CommandException();
        } finally {
            // after all remount system back as read only
            if (!targetDataData) {
                RootTools.remount(Constants.ANDROID_SYSTEM_ETC_PATH, "RO");
            }
        }
    }

    /**
     * Create symlink from /system/etc/hosts to /data/data/hosts
     * 
     * @throws RemountException
     *             CommandException
     */
    public static void createSymlink() throws RemountException, CommandException {
        String SystemEtcHosts = Constants.ANDROID_SYSTEM_ETC_PATH + File.separator
                + Constants.HOSTS_FILENAME;
        String DataDataHosts = Constants.ANDROID_DATA_DATA_PATH + File.separator
                + Constants.HOSTS_FILENAME;

        String commandRm = Constants.COMMAND_RM + " " + SystemEtcHosts;
        String commandSymlink = Constants.COMMAND_LN + " " + DataDataHosts + " " + SystemEtcHosts;
        String commandChownDataDataHosts = Constants.COMMAND_CHOWN + " " + DataDataHosts;
        String commandChmodDataDataHosts644 = Constants.COMMAND_CHMOD_644 + " " + DataDataHosts;

        String targetPath = Constants.ANDROID_SYSTEM_ETC_PATH;

        /* remount for write access */
        if (!RootTools.remount(targetPath, "RW")) {
            throw new RemountException();
        }

        /* Execute commands */
        List<String> output = null;
        try {
            // create symlink
            output = RootTools.sendShell(new String[] { commandRm, commandSymlink, commandChownDataDataHosts, commandChmodDataDataHosts644 }, 1);

            Log.d(Constants.TAG, "output of sendShell commands: " + output.toString());
        } catch (IOException e) {
            Log.e(Constants.TAG, "Exception: " + e);
            e.printStackTrace();

            throw new CommandException();
        } catch (InterruptedException e) {
            Log.e(Constants.TAG, "Exception: " + e);
            e.printStackTrace();

            throw new CommandException();
        } catch (RootToolsException e) {
            Log.e(Constants.TAG, "Exception: " + e);
            e.printStackTrace();

            throw new CommandException();
        } finally {
            // after all remount system back as read only
            RootTools.remount(Constants.ANDROID_SYSTEM_ETC_PATH, "RO");
        }
    }

    /**
     * Reboots Android
     * 
     * @throws CommandException
     */
    public static void reboot() throws CommandException {
        String commandReboot = "reboot";

        /* Execute commands */
        List<String> output = null;
        try {
            // create symlink
            output = RootTools.sendShell(commandReboot);

            Log.d(Constants.TAG, "output of sendShell commands: " + output.toString());
        } catch (IOException e) {
            Log.e(Constants.TAG, "Exception: " + e);
            e.printStackTrace();

            throw new CommandException();
        } catch (InterruptedException e) {
            Log.e(Constants.TAG, "Exception: " + e);
            e.printStackTrace();

            throw new CommandException();
        } catch (RootToolsException e) {
            Log.e(Constants.TAG, "Exception: " + e);
            e.printStackTrace();

            throw new CommandException();
        }
    }
}
