package org.adaway.db.entity;

import android.webkit.URLUtil;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.time.ZonedDateTime;
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
    private ZonedDateTime localModificationDate;
    @ColumnInfo(name = "last_modified_online")
    private ZonedDateTime onlineModificationDate;

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

    public ZonedDateTime getLocalModificationDate() {
        return localModificationDate;
    }

    public void setLocalModificationDate(ZonedDateTime localModificationDate) {
        this.localModificationDate = localModificationDate;
    }

    public ZonedDateTime getOnlineModificationDate() {
        return onlineModificationDate;
    }

    public void setOnlineModificationDate(ZonedDateTime lastOnlineModification) {
        this.onlineModificationDate = lastOnlineModification;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        HostsSource that = (HostsSource) o;

        if (id != that.id) return false;
        if (enabled != that.enabled) return false;
        if (!url.equals(that.url)) return false;
        if (!Objects.equals(localModificationDate, that.localModificationDate))
            return false;
        return Objects.equals(onlineModificationDate, that.onlineModificationDate);

    }

    @Override
    public int hashCode() {
        int result = id;
        result = 31 * result + url.hashCode();
        result = 31 * result + (enabled ? 1 : 0);
        result = 31 * result + (localModificationDate != null ? localModificationDate.hashCode() : 0);
        result = 31 * result + (onlineModificationDate != null ? onlineModificationDate.hashCode() : 0);
        return result;
    }
}
