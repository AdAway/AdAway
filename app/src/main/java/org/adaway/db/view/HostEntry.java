package org.adaway.db.view;

import androidx.annotation.NonNull;
import androidx.room.DatabaseView;

import org.adaway.db.entity.ListType;

/**
 * This entity represents an entry of the build hosts file.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
@DatabaseView(
        value = "WITH redir AS (" +
                "    SELECT host, redirection FROM hosts_lists WHERE enabled = 1 AND type = 2 ORDER BY source_id ASC" +
                "), allowed AS (" +
                "    SELECT host FROM `hosts_lists` WHERE `enabled` = 1 AND `type` = 1" +
                ") " +
                "SELECT list.`host`, max(`type`) AS type, redir.redirection " +
                "FROM `hosts_lists` AS `list` " +
                "LEFT JOIN redir ON list.host = redir.host " +
                "WHERE `enabled` = 1 AND ( (`type` = 0 AND NOT EXISTS (SELECT 1 FROM allowed WHERE `list`.`host` LIKE allowed.host ) ) OR `type` = 2) " +
                "GROUP BY list.`host`",
        viewName = "host_entries")
public class HostEntry {
    @NonNull
    private String host;
    @NonNull
    private ListType type;
    private String redirection;

    @NonNull
    public String getHost() {
        return host;
    }

    public void setHost(@NonNull String host) {
        this.host = host;
    }

    @NonNull
    public ListType getType() {
        return type;
    }

    public void setType(@NonNull ListType type) {
        this.type = type;
    }

    public String getRedirection() {
        return redirection;
    }

    public void setRedirection(String redirection) {
        this.redirection = redirection;
    }
}
