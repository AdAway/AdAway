package org.adaway.ui.welcome;

import androidx.fragment.app.Fragment;

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
