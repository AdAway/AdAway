package org.adaway.ui.adware;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.support.annotation.NonNull;

/**
 * This class is a {@link android.arch.lifecycle.ViewModel} for adware UI.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
class AdwareViewModel extends AndroidViewModel {
    /**
     * The install adware.
     */
    private final AdwareLiveData adware;

    /**
     * Constructor.
     *
     * @param application The application context.
     */
    AdwareViewModel(@NonNull Application application) {
        super(application);
        this.adware = new AdwareLiveData(application);
    }

    /**
     * Get the installed adware.
     *
     * @return The installed adware.
     */
    AdwareLiveData getAdware() {
        return this.adware;
    }
}
