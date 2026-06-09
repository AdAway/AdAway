package org.adaway.ui.log

import org.adaway.db.entity.ListType

/**
 * This class represents a DNS request log entry.
 */
data class LogEntry(
    val host: String,
    var type: ListType? = null
) : Comparable<LogEntry> {
    override fun compareTo(other: LogEntry): Int {
        return this.host.compareTo(other.host)
    }
}
