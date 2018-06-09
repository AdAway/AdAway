package org.adaway.db.converter;

import android.arch.persistence.room.TypeConverter;

import org.adaway.db.entity.ListType;

public class ListTypeConverter {
    @TypeConverter
    public static ListType fromValue(Integer value) {
        return value == null ? null : ListType.fromValue(value);
    }

    @TypeConverter
    public static Integer typeToValue(ListType type) {
        return type == null ? null : type.getValue();
    }
}
