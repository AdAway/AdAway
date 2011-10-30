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

package org.adaway.util;

import org.adaway.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import com.stericson.RootTools.RootTools;
import com.stericson.RootTools.RootToolsException;

import android.content.Context;
import android.os.StatFs;

public class ApplyUtils {
    /**
     * Check if there is enough space on partition where target is located
     * 
     * @param size
     *            size of file to put on partition
     * @param target
     *            path where to put the file
     * 
     * @return <code>true</code> if it will fit on partition of <code>path</code>,
     *         <code>false</code> if it will not fit.
     */
    public static boolean hasEnoughSpaceOnPartition(String target, long size) {
        long availableSpace;

        // new File(target).getFreeSpace() (API 9) is not working on data partition

        // get directory without file
        String directory = new File(target).getParent().toString();

        StatFs stat = new StatFs(directory);
        long blockSize = stat.getBlockSize();
        long availableBlocks = stat.getAvailableBlocks();
        availableSpace = availableBlocks * blockSize;

        Log.i(Constants.TAG, "Checking for enough space: Target: " + target + ", directory: "
                + directory + " size: " + size + ", availableSpace: " + availableSpace);

        if (size < availableSpace) {
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
    public static boolean isHostsFileCorrect(Context context, String target) {
        boolean status = false;

        /* Check if first line in hosts file is AdAway comment */
        String hostsFile = target;

        File file = new File(hostsFile);
        InputStream stream = null;
        InputStreamReader in = null;
        BufferedReader br = null;
        try {
            stream = new FileInputStream(file);
            in = new InputStreamReader(stream);
            br = new BufferedReader(in);

            String firstLine = br.readLine();

            Log.d(Constants.TAG, "First line of " + target + ": " + firstLine);

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
    public static void copyHostsFile(Context context, String customTarget)
            throws NotEnoughSpaceException, RemountException, CommandException {
        Log.i(Constants.TAG, "Copy hosts file with target: " + customTarget);
        String privateDir = context.getFilesDir().getAbsolutePath();
        String privateFile = privateDir + File.separator + Constants.HOSTS_FILENAME;

        // if the customTarget has a trailing slash, it is not a valid target!
        if (customTarget.endsWith("/")) {
            Log.e(Constants.TAG,
                    "Custom target ends with trailing slash, it is not a valid target!");
            throw new CommandException();
        }

        // commands when using /system/etc/hosts
        String commandCopySystemEtc = Constants.COMMAND_COPY + " " + privateFile + " "
                + Constants.ANDROID_SYSTEM_ETC_HOSTS;
        String commandChownSystemEtcHosts = Constants.COMMAND_CHOWN + " "
                + Constants.ANDROID_SYSTEM_ETC_HOSTS;
        String commandChmodSystemEtcHosts644 = Constants.COMMAND_CHMOD_644 + " "
                + Constants.ANDROID_SYSTEM_ETC_HOSTS;

        String target = null;
        if (customTarget == "") {
            target = Constants.ANDROID_SYSTEM_ETC_HOSTS;
        } else {
            target = customTarget;
        }

        // commands when using customTarget
        String commandCopyAlternativePath = Constants.COMMAND_COPY + " " + privateFile + " "
                + target;
        String commandChmodAlternativePath666 = Constants.COMMAND_CHMOD_666 + " " + target;

        /* remount for write access */
        if (!RootTools.remount(target, "RW")) {
            throw new RemountException();
        }

        /*
         * If custom target like /data/etc/hosts is set, create missing directories for writing this
         * file
         */
        if (customTarget != "") {
            createDirectories(target);
        }

        /* check for space on partition */
        long size = new File(privateFile).length();
        Log.d(Constants.TAG, "size: " + size);
        if (!hasEnoughSpaceOnPartition(target, size)) {
            throw new NotEnoughSpaceException();
        }

        /* Execute commands */
        List<String> output = null;
        try {
            if (customTarget == "") {
                // execute commands: copy, chown, chmod
                output = RootTools.sendShell(new String[] { commandCopySystemEtc,
                        commandChownSystemEtcHosts, commandChmodSystemEtcHosts644 }, 1);
            } else {
                // execute copy
                output = RootTools.sendShell(new String[] { commandCopyAlternativePath,
                        commandChmodAlternativePath666 }, 1);
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
            if (customTarget == "") {
                RootTools.remount(Constants.ANDROID_SYSTEM_ETC_HOSTS, "RO");
            }
        }
    }

    /**
     * Create symlink from /system/etc/hosts to /data/data/hosts
     * 
     * @throws RemountException
     *             CommandException
     */
    public static void createSymlink(String target) throws RemountException, CommandException {
        String commandRm = Constants.COMMAND_RM + " " + Constants.ANDROID_SYSTEM_ETC_HOSTS;
        String commandSymlink = Constants.COMMAND_LN + " " + target + " "
                + Constants.ANDROID_SYSTEM_ETC_HOSTS;
        String commandChownTarget = Constants.COMMAND_CHOWN + " " + target;
        String commandChmodTarget644 = Constants.COMMAND_CHMOD_644 + " " + target;

        /* remount /system/etc for write access */
        if (!RootTools.remount(Constants.ANDROID_SYSTEM_ETC_HOSTS, "RW")) {
            throw new RemountException();
        }

        Log.i(Constants.TAG, "Create symlink with " + commandRm + "; " + commandSymlink + "; "
                + commandChownTarget + "; " + commandChmodTarget644);

        /* Execute commands */
        List<String> output = null;
        try {
            // create symlink
            output = RootTools.sendShell(new String[] { commandRm, commandSymlink,
                    commandChownTarget, commandChmodTarget644 }, 1);

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
            RootTools.remount(Constants.ANDROID_SYSTEM_ETC_HOSTS, "RO");
        }
    }

    /**
     * Checks whether /system/etc/hosts is a symlink and pointing to the target or not
     * 
     * @param target
     * @return
     * @throws CommandException
     */
    public static boolean isSymlinkCorrect(String target) {
        String commandReadlink = Constants.COMMAND_READLINK + " "
                + Constants.ANDROID_SYSTEM_ETC_HOSTS;

        Log.i(Constants.TAG,
                "Checking whether /system/etc/hosts is a symlink and pointing to the target or not.");

        /* Execute commands */
        List<String> output = null;
        try {
            // create directories
            output = RootTools.sendShell(new String[] { commandReadlink }, 1);

            Log.d(Constants.TAG, "output of sendShell commands: " + output.toString());
        } catch (IOException e) {
            Log.e(Constants.TAG, "Exception: " + e);
            e.printStackTrace();
        } catch (InterruptedException e) {
            Log.e(Constants.TAG, "Exception: " + e);
            e.printStackTrace();
        } catch (RootToolsException e) {
            Log.e(Constants.TAG, "Exception: " + e);
            e.printStackTrace();
        }

        Log.d(Constants.TAG, "Output of " + commandReadlink + ": " + output.get(0));

        if (output.get(0).equals(target)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Create directories if missing, if /data/etc/hosts is set as target, this creates /data/etc/
     * directories. Needs RW on partition!
     * 
     * @throws CommandException
     */
    public static void createDirectories(String target) throws CommandException {
        // get directory without file
        String directory = new File(target).getParent().toString();

        String commandMkdir = Constants.COMMAND_MKDIR + " " + directory;

        Log.i(Constants.TAG, "Create directories using " + commandMkdir);

        /* Execute commands */
        List<String> output = null;
        try {
            // create directories
            output = RootTools.sendShell(new String[] { commandMkdir }, 1);

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
