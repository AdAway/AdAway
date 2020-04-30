package org.adaway.db.converter;

import androidx.room.TypeConverter;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

import static java.time.ZoneOffset.UTC;

/**
 * This class is a type converter for Room to support {@link java.time.ZonedDateTime} type.
 * It is stored as a Unix epoc timestamp.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public final class ZonedDateTimeConverter {
    private ZonedDateTimeConverter() {
        // Prevent instantiation
    }

    @TypeConverter
    public static ZonedDateTime fromTimestamp(Long value) {
        return value == null ? null : ZonedDateTime.of(LocalDateTime.ofEpochSecond(value, 0, UTC), UTC);
    }

    @TypeConverter
    public static Long toTimestamp(ZonedDateTime zonedDateTime) {
        return zonedDateTime == null ? null : zonedDateTime.toEpochSecond();
    }
}
