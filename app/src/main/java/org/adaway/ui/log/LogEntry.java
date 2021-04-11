package org.adaway.ui.log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.adaway.db.entity.ListType;

/**
 * This class represents a DNS request log entry.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
class LogEntry implements Comparable<LogEntry> {
    @NonNull
    private final String host;

    @Nullable
    private ListType type;

    LogEntry(@NonNull String host, @Nullable ListType type) {
        this.host = host;
        this.type = type;
    }

    @NonNull
    public String getHost() {
        return this.host;
    }

    @Nullable
    public ListType getType() {
        return this.type;
    }

    public void setType(@Nullable ListType type) {
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LogEntry that = (LogEntry) o;

        if (!host.equals(that.host)) return false;
        return type == that.type;
    }

    @Override
    public int hashCode() {
        int result = host.hashCode();
        result = 31 * result + (type != null ? type.hashCode() : 0);
        return result;
    }

    @Override
    public int compareTo(@NonNull LogEntry o) {
        return this.host.compareTo(o.host);
    }
}
