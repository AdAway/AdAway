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

import static org.adaway.util.Constants.TAG;

public class ApplyUtils {


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