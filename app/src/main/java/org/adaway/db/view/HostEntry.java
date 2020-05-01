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
        value = "SELECT `host`, `type`, `redirection` " +
                "FROM `hosts_lists` " +
                "WHERE `enabled` = 1 AND ((`type` = 0 AND `host` NOT LIKE (SELECT `host` FROM `hosts_lists` WHERE `enabled` = 1 and `type` = 1)) OR `type` = 2) " +
                "GROUP BY `host` " +
                "ORDER BY `host` ASC, `type` DESC, `redirection` ASC",
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
