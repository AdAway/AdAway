package io.sentry;

import io.sentry.android.AndroidSentryClientFactory;
import io.sentry.context.Context;

public class Sentry {
    public static final boolean STUB = true;

    private static Context context = new Context();

    public static void init(String dsn, AndroidSentryClientFactory clientFactory) {
        // Stub
    }

    public static Context getContext() {
        return Sentry.context;
    }

    public static void capture(String message) {
        // Stub
    }

    public static void capture(Throwable throwable) {
        // Stub
    }
}
