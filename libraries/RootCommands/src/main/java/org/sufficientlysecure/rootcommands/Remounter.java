/*
 * Copyright (C) 2012 Dominik Schürmann <dominik@dominikschuermann.de>
 * Copyright (c) 2012 Stephen Erickson, Chris Ravenscroft, Adam Shanks (RootTools)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sufficientlysecure.rootcommands;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Locale;

import org.sufficientlysecure.rootcommands.command.SimpleCommand;
import org.sufficientlysecure.rootcommands.util.Log;

//no modifier, this means it is package-private. Only our internal classes can use this.
class Remounter {

    private Shell shell;

    public Remounter(Shell shell) {
        super();
        this.shell = shell;
    }

    /**
     * This will take a path, which can contain the file name as well, and attempt to remount the
     * underlying partition.
     * <p/>
     * For example, passing in the following string:
     * "/system/bin/some/directory/that/really/would/never/exist" will result in /system ultimately
     * being remounted. However, keep in mind that the longer the path you supply, the more work
     * this has to do, and the slower it will run.
     * 
     * @param mountPoint
     *            the mount point you want to remount
     * @param mountType
     *            mount type: pass in RO (Read only) or RW (Read Write)
     * @return a <code>boolean</code> which indicates whether or not the partition has been
     *         remounted as specified.
     */
    protected boolean remount(String mountPoint,String mountType) {
        // grab an instance of the internal class
        try {
            //According to ChainFire, previous versions of Android never actually check the mount point, so we can just duplicate it.
            SimpleCommand command = new SimpleCommand("mount -o remount,"
                    + mountType.toLowerCase(Locale.US) + " "+ mountPoint+" "+mountPoint);

            // execute on shell
            shell.add(command).waitForFinish();
            if (command.getExitCode() != 0) {
                //If we receive a non-zero error, then the above remount failed, so we should attempt a remount for N
                //Note: N requires ro/rw first and remount second
                command = new SimpleCommand("mount -o "+ mountType.toLowerCase(Locale.US) + ",remount "+mountPoint);
                // execute on shell
                shell.add(command).waitForFinish();
            }
        } catch (Exception e) {
                Log.e(RootCommands.TAG, "Exception", e);
                return false;
        }
        return true;
    }

}
