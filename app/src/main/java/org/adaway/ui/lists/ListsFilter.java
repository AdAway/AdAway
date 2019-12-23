package org.adaway.ui.lists;

import org.adaway.db.AppDatabase;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;

/**
 * This class represents the filter to apply to host lists.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
class ListsFilter {
    static final ListsFilter ALL = new ListsFilter(true, "");
    /**
     * Whether included hosts from sources or not.
     */
    final boolean sourcesIncluded;
    /**
     * The filter to apply to hosts name (wildcard based).
     */
    final String hostFilter;

    ListsFilter(boolean sourcesIncluded, String hostFilter) {
        this.sourcesIncluded = sourcesIncluded;
        this.hostFilter = hostFilter;
    }

    private static String convertToLikeQuery(String filter) {
        return "%" + filter.replaceAll("\\*", "%")
                .replaceAll("\\?", "_") + "%";
    }

    SqlFilter compute(AppDatabase database) {
        return new SqlFilter(
                this,
                this.sourcesIncluded ? database.hostsSourceDao().getAllIds() : new int[]{USER_SOURCE_ID},
                convertToLikeQuery(this.hostFilter)
        );
    }

    /**
     * This class is the inner SQL filter used for room queries.
     *
     * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
     */
    static class SqlFilter {
        /**
         * The original filter.
         */
        final ListsFilter source;
        /**
         * The hosts sources identifiers.
         */
        final int[] sourceIds;
        /**
         * The filter to apply to host name (SQL LIKE formatted).
         */
        final String query;

        SqlFilter(ListsFilter source, int[] sourceIds, String query) {
            this.source = source;
            this.sourceIds = sourceIds;
            this.query = query;
        }
    }
}
