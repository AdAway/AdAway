package io.sentry;

public class Sentry {
    public static final boolean STUB = true;

    public static void capture(String message) {
        // Stub
    }

    public static void captureMessage(String msg, SentryLevel info) {
        // Stub
    }

    public static void captureException(Throwable tr, String msg) {
        // Stub
    }

    public static void configureScope(ScopeCallback callback) {
        // Stub
    }
}
