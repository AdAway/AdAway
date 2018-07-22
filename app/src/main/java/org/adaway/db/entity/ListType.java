package org.adaway.db.entity;

/**
 * This enumerate specifies the type of {@link HostListItem}.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public enum ListType {
    BLACK_LIST(0),
    WHITE_LIST(1),
    REDIRECTION_LIST(2);

    private int value;

    ListType(int value) {
        this.value = value;
    }

    public static ListType fromValue(int value) {
        for (ListType listType : ListType.values()) {
            if (listType.value == value) {
                return listType;
            }
        }
        throw new IllegalArgumentException("Invalid value for list type: " + value);
    }

    public int getValue() {
        return value;
    }
}
