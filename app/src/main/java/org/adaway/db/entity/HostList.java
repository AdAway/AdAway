package org.adaway.db.entity;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import android.support.annotation.NonNull;

@Entity(tableName = "hosts_lists")
public class HostList {
    @PrimaryKey
    @NonNull
    private String host;

    @NonNull
    private ListType type;

    @NonNull
    private Boolean enabled;

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

    @NonNull
    public Boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(@NonNull Boolean enabled) {
        this.enabled = enabled;
    }

    public String getRedirection() {
        return redirection;
    }

    public void setRedirection(String redirection) {
        this.redirection = redirection;
    }
}
