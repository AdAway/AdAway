package org.adaway.util.log;

import android.content.Context;
import android.util.Log;

import org.adaway.helper.PreferenceHelper;
import org.jetbrains.annotations.Nullable;

import io.sentry.Breadcrumb;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import io.sentry.android.core.SentryAndroid;
import timber.log.Timber;

import static io.sentry.SentryLevel.INFO;

import androidx.annotation.NonNull;

/**
 * This class is a helper to initialize and configuration Sentry.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public final class SentryLog {
    /**
     * Private constructor.
     */
    private SentryLog() {

    }

    /**
     * Initialize Sentry logging client according user preferences.
     *
     * @param context The application context.
     */
    public static void init(Context context) {
        setEnabled(context, PreferenceHelper.getTelemetryEnabled(context));
    }

    /**
     * Initialize Sentry logging client.
     *
     * @param context The application context.
     * @param enabled Whether the application is allowed to send events to Sentry or not.
     */
    public static void setEnabled(Context context, boolean enabled) {
        if (enabled) {
            // Initialize sentry client manually
            SentryAndroid.init(context);
        }
    }

    /**
     * Record a breadcrumb.
     *
     * @param message The breadcrumb message.
     */
    public static void recordBreadcrumb(String message) {
        Sentry.configureScope(scope -> {
            Breadcrumb breadcrumb = new Breadcrumb();
            breadcrumb.setMessage(message);
            breadcrumb.setLevel(INFO);
            scope.addBreadcrumb(breadcrumb);
        });
    }

    /**
     * Check if {@link Sentry} implementation is a stub or not.
     *
     * @return {@code true} if the runtime implementation is a stub, {@code false} otherwise.
     */
    public static boolean isStub() {
        try {
            Sentry.class.getDeclaredField("STUB");
            return true;
        } catch (NoSuchFieldException exception) {
            return false;
        }
    }

    static class SentryTree extends Timber.Tree {
        @Override
        protected void log(int priority, @Nullable String tag, @NonNull String message, @Nullable Throwable throwable) {
            if (priority == Log.WARN) {
                Sentry.captureMessage(message, SentryLevel.WARNING);
            } else if (priority == Log.ERROR) {
                Sentry.captureMessage(message, SentryLevel.ERROR);
            }
        }
    }
}
