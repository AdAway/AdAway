package org.adaway.ui.adware;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.annotation.NonNull;

/**
 * This class is a {@link androidx.lifecycle.ViewModel} for adware UI.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class AdwareViewModel extends AndroidViewModel {
    /**
     * The install adware.
     */
    private final AdwareLiveData adware;

    /**
     * Constructor.
     *
     * @param application The application context.
     */
    public AdwareViewModel(@NonNull Application application) {
        super(application);
        this.adware = new AdwareLiveData(application);
    }

    /**
     * Get the installed adware.
     *
     * @return The installed adware.
     */
    public AdwareLiveData getAdware() {
        return this.adware;
    }
}
