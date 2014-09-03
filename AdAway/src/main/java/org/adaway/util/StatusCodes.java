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

/**
 * Return codes of various AsyncTasks
 */
public class StatusCodes {
    public static final int CHECKING = 0;
    public static final int SUCCESS = 1;
    public static final int PRIVATE_FILE_FAIL = 2;
    public static final int UPDATE_AVAILABLE = 3;
    public static final int ENABLED = 4;
    public static final int DISABLED = 5;
    public static final int DOWNLOAD_FAIL = 6;
    public static final int NO_CONNECTION = 7;
    public static final int APPLY_FAIL = 8;
    public static final int SYMLINK_MISSING = 9;
    public static final int NOT_ENOUGH_SPACE = 10;
    public static final int REMOUNT_FAIL = 11;
    public static final int COPY_FAIL = 12;
    public static final int REVERT_SUCCESS = 14;
    public static final int REVERT_FAIL = 15;
    public static final int APN_PROXY = 16;
}