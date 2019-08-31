package org.adaway.util;

import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;

import io.sentry.Sentry;
import io.sentry.android.AndroidSentryClientFactory;
import io.sentry.event.Breadcrumb;
import io.sentry.event.BreadcrumbBuilder;


/**
 * This class is a helper to initialize and configuration Sentry.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public final class SentryLog {
    /**
     * The API key to
     */
    private static final String SENTRY_DSN = "https://8dac17b798fb45e492278a678c5ab028@sentry.io/1331667";

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
        // Select DSN according activation status (null prevent event collection and sending)
        String dsn = enabled ? SENTRY_DSN : null;
        // Initialize sentry client
        Sentry.init(dsn, new AndroidSentryClientFactory(context));
    }

    /**
     * Record a breadcrumb.
     *
     * @param message The breadcrumb message.
     */
    public static void recordBreadcrumb(String message) {
        Breadcrumb breadcrumb = new BreadcrumbBuilder().setMessage(message).build();
        Sentry.getContext().recordBreadcrumb(breadcrumb);
    }

    /**
     * Ensure the user accept telemetry before enabling it (opt-in behavior).
     *
     * @param context The application context.
     */
    public static void requestUserConsent(Context context) {
        // Check if user consent was requested
        if (!PreferenceHelper.getDisplayTelemetryConsent(context)) {
            return;
        }
        // Check stub implementation
        if (isStub()) {
            // Do no more show user request
            PreferenceHelper.setDisplayTelemetryConsent(context, false);
            return;
        }
        // Display user request dialog
        LayoutInflater inflater = LayoutInflater.from(context);
        View messageView = inflater.inflate(R.layout.dialog_telemetry, null);
        TextView textView = messageView.findViewById(R.id.telemetry_message);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.dialog_telemetry_title)
                .setIcon(R.drawable.outline_cloud_upload_24)
                .setView(messageView)
                .setCancelable(false)
                .setPositiveButton(R.string.dialog_telemetry_enable, (dialog, which) -> {
                    PreferenceHelper.setDisplayTelemetryConsent(context, false);
                    PreferenceHelper.setTelemetryEnabled(context, true);
                    SentryLog.setEnabled(context, true);
                })
                .setNegativeButton(R.string.dialog_telemetry_disable, (dialog, which) ->
                        PreferenceHelper.setDisplayTelemetryConsent(context, false)
                ).show();
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
}
