package io.sentry;

public class Sentry {
    public static final boolean STUB = true;

    public static void configureScope(ScopeCallback callback) {
        // Stub
    }

    public interface OptionsConfiguration<T extends SentryOptions> {
        /**
         * configure the options
         *
         * @param options the options
         */
        void configure(T options);
    }
}
