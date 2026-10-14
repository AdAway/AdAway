package org.adaway.ui.onboarding;

import android.content.Context;

import androidx.annotation.MainThread;
import androidx.lifecycle.ViewModel;

import org.adaway.util.AppExecutors;

import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

/** Owns one onboarding download attempt across Home activity recreation. */
public final class InitialSourcesViewModel extends ViewModel {
    private boolean mStarted;

    @MainThread
    public void initialize(Context context, Runnable update) {
        Context applicationContext = context.getApplicationContext();
        AppExecutors executors = AppExecutors.getInstance();
        initialize(executors.diskIO(), executors.mainThread(),
                () -> DefaultListsSubscriber.prepareInitialSources(applicationContext), update);
    }

    @MainThread
    void initialize(Executor diskExecutor, Executor mainExecutor,
            BooleanSupplier prepareSources, Runnable update) {
        if (this.mStarted) {
            return;
        }
        // This guards an attempt, not success. No persisted flag suppresses a later retry.
        this.mStarted = true;
        diskExecutor.execute(() -> {
            if (prepareSources.getAsBoolean()) {
                mainExecutor.execute(update);
            }
        });
    }
}
