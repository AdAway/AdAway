package org.adaway.db.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

import android.webkit.URLUtil;

import java.util.Date;
import java.util.Objects;

/**
 * This entity represents a source to get hosts list.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
@Entity(
        tableName = "hosts_sources",
        indices = {@Index(value = "url", unique = true)}
)
public class HostsSource {
    /**
     * The user source ID.
     */
    public static final int USER_SOURCE_ID = 1;
    /**
     * The user source URL.
     */
    public static final String USER_SOURCE_URL = "file://app/user/hosts";

    @PrimaryKey(autoGenerate = true)
    private int id;
    @NonNull
    private String url;
    private boolean enabled;
    @ColumnInfo(name = "last_modified_local")
    private Date lastLocalModification;
    @ColumnInfo(name = "last_modified_online")
    private Date lastOnlineModification;

    /**
     * Check whether an URL is valid for as host source.<br>
     * A valid URL is a HTTPS URL or file URL.
     *
     * @param url The URL to check.
     * @return {@code true} if the URL is valid, {@code false} otherwise.
     */
    public static boolean isValidUrl(String url) {
        return URLUtil.isHttpsUrl(url) || URLUtil.isFileUrl(url);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @NonNull
    public String getUrl() {
        return url;
    }

    public void setUrl(@NonNull String url) {
        this.url = url;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        HostsSource that = (HostsSource) o;

        if (id != that.id) return false;
        if (enabled != that.enabled) return false;
        if (!url.equals(that.url)) return false;
        if (!Objects.equals(lastLocalModification, that.lastLocalModification))
            return false;
        return Objects.equals(lastOnlineModification, that.lastOnlineModification);

    }

    @Override
    public int hashCode() {
        int result = id;
        result = 31 * result + url.hashCode();
        result = 31 * result + (enabled ? 1 : 0);
        result = 31 * result + (lastLocalModification != null ? lastLocalModification.hashCode() : 0);
        result = 31 * result + (lastOnlineModification != null ? lastOnlineModification.hashCode() : 0);
        return result;
    }
}
