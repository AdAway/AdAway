package org.adaway.db.converter;

import androidx.room.TypeConverter;

import org.adaway.db.entity.ListType;

/**
 * This class is a type converter for Room to support {@link ListType} type.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public final class ListTypeConverter {
    private ListTypeConverter() {
        // Prevent instantiation
    }

    @TypeConverter
    public static ListType fromValue(Integer value) {
        return value == null ? null : ListType.fromValue(value);
    }

    @TypeConverter
    public static Integer typeToValue(ListType type) {
        return type == null ? null : type.getValue();
    }
}
