package org.adaway.db.entity;

import android.webkit.URLUtil;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.time.ZonedDateTime;
import java.util.Objects;

import static org.adaway.db.entity.SourceType.FILE;
import static org.adaway.db.entity.SourceType.UNSUPPORTED;
import static org.adaway.db.entity.SourceType.URL;

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
    public static final String USER_SOURCE_URL = "content://org.adaway/user/hosts";

    @PrimaryKey(autoGenerate = true)
    private int id;
    @NonNull
    private String label;
    @NonNull
    private String url;
    private boolean enabled = true;
    private boolean allowEnabled = false;
    private boolean redirectEnabled = false;
    @ColumnInfo(name = "last_modified_local")
    private ZonedDateTime localModificationDate;
    @ColumnInfo(name = "last_modified_online")
    private ZonedDateTime onlineModificationDate;
    private int size;

    /**
     * Check whether an URL is valid for as host source.<br>
     * A valid URL is a HTTPS URL or file URL.
     *
     * @param url The URL to check.
     * @return {@code true} if the URL is valid, {@code false} otherwise.
     */
    public static boolean isValidUrl(String url) {
        return (!"https://".equals(url) && URLUtil.isHttpsUrl(url)) || URLUtil.isContentUrl(url);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @NonNull
    public String getLabel() {
        return label;
    }

    public void setLabel(@NonNull String label) {
        this.label = label;
    }

    @NonNull
    public String getUrl() {
        return url;
    }

    public void setUrl(@NonNull String url) {
        this.url = url;
    }

    public SourceType getType() {
        if (this.url.startsWith("https://")) {
            return URL;
        } else if (this.url.startsWith("content://")) {
            return FILE;
        } else {
            return UNSUPPORTED;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAllowEnabled() {
        return allowEnabled;
    }

    public void setAllowEnabled(boolean allowEnabled) {
        this.allowEnabled = allowEnabled;
    }

    public boolean isRedirectEnabled() {
        return redirectEnabled;
    }

    public void setRedirectEnabled(boolean redirectEnabled) {
        this.redirectEnabled = redirectEnabled;
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

    public int getSize() {
        return this.size;
    }

    public void setSize(int size) {
        this.size = size;
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
