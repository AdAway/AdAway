package org.adaway.ui.welcome;

import androidx.fragment.app.Fragment;

/**
 * This class is the base fragment to all setup fragments.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public abstract class WelcomeFragment extends Fragment {
    private boolean goNext = false;

    protected void allowNext() {
        this.goNext = true;
        getNavigable().allowNext();
    }

    protected void blockNext() {
        this.goNext = false;
        getNavigable().blockNext();
    }

    protected boolean canGoNext() {
        return this.goNext;
    }

    private WelcomeNavigable getNavigable() {
        return (WelcomeNavigable) getActivity();
    }
}
