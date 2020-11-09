package org.adaway.db.entity;

/**
 * This enumerate specifies the type of {@link HostsSource}.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public enum SourceType {
    /**
     * The URL type represents online source to download from URL.
     */
    URL,
    /**
     * The FILE type represents file stored source to retrieve using SAF API.
     */
    FILE,
    /**
     * The UNSUPPORTED type represents unhandled source type.
     */
    UNSUPPORTED
}
