package org.adaway.db.entity;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import android.support.annotation.NonNull;

import java.util.Date;

@Entity(tableName = "hosts_sources")
public class HostsSource {
    @PrimaryKey
    @NonNull
    private String url;
    @NonNull
    private Boolean enabled;
    @ColumnInfo(name = "last_modified_local")
    private Date lastLocalModification;
    @ColumnInfo(name = "last_modified_online")
    private Date lastOnlineModification;

    @NonNull
    public String getUrl() {
        return url;
    }

    public void setUrl(@NonNull String url) {
        this.url = url;
    }

    @NonNull
    public Boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(@NonNull Boolean enabled) {
        this.enabled = enabled;
    }

    public Date getLastLocalModification() {
        return lastLocalModification;
    }

    public void setLastLocalModification(Date lastLocalModification) {
        this.lastLocalModification = lastLocalModification;
    }

    public Date getLastOnlineModification() {
        return lastOnlineModification;
    }

    public void setLastOnlineModification(Date lastOnlineModification) {
        this.lastOnlineModification = lastOnlineModification;
    }
}
