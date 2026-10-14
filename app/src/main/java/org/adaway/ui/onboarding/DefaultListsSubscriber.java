package org.adaway.ui.onboarding;

import android.content.Context;

import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostsSource;
import org.adaway.model.source.FilterListCatalog;
import org.adaway.model.source.SiteCompatibilityAllowlist;
import org.adaway.model.source.WaTgSafetyAllowlist;

import java.util.ArrayList;
import java.util.List;

/**
 * Prepares the user's filter lists for their first download after onboarding.
 *
 * <p>Called from {@link org.adaway.ui.home.HomeActivity} on the diskIO executor
 * when the onboarding complete flag is present in the launch intent.</p>
 *
 * <p>This class performs synchronous DB operations and must never be called
 * on the main thread.</p>
 */
public final class DefaultListsSubscriber {

    private DefaultListsSubscriber() {
        // Utility class — no instances
    }

    /**
     * Subscribe defaults only when no external sources exist, then check download state.
     *
     * @param context Any context; the application context is obtained internally.
     * @return {@code true} if an enabled source has never been downloaded, regardless of
     *         whether this call inserted it or the production database already seeded it.
     */
    public static boolean prepareInitialSources(Context context) {
        Context applicationContext = context.getApplicationContext();
        HostsSourceDao hostsSourceDao = AppDatabase.getInstance(
                applicationContext).hostsSourceDao();
        return prepareInitialSources(hostsSourceDao, () -> {
            WaTgSafetyAllowlist.ensureAllowlistSync(applicationContext);
            SiteCompatibilityAllowlist.ensureAllowlistSync(applicationContext);
        });
    }

    static boolean prepareInitialSources(HostsSourceDao hostsSourceDao, Runnable ensureAllowlists) {
        if (hostsSourceDao.getAll().isEmpty()) {
            List<FilterListCatalog.CatalogEntry> defaults = FilterListCatalog.getDefaults();
            List<HostsSource> toInsert = new ArrayList<>(defaults.size());
            for (FilterListCatalog.CatalogEntry entry : defaults) {
                toInsert.add(entry.toHostsSource());
            }
            if (!toInsert.isEmpty()) {
                hostsSourceDao.insertAll(toInsert);
                // Finish safety entries on diskIO before the download can start.
                ensureAllowlists.run();
            }
        }

        for (HostsSource source : hostsSourceDao.getEnabled()) {
            if (source.getLocalModificationDate() == null) {
                return true;
            }
        }
        return false;
    }
}
