package org.adaway.db.converter;

import androidx.room.TypeConverter;

import java.util.Date;

/**
 * This class is a type converter for Room to support {@link Date} type.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public final class DateConverter {
    private DateConverter() {
        // Prevent instantiation
    }

    @TypeConverter
    public static Date fromTimestamp(Long value) {
        return value == null ? null : new Date(value);
    }

    @TypeConverter
    public static Long dateToTimestamp(Date date) {
        return date == null ? null : date.getTime();
    }
}
