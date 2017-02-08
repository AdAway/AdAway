package org.adaway.util.systemless;

import android.content.Context;

import org.sufficientlysecure.rootcommands.Shell;

/**
 * This class provides implementation for not supported systemless mode.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class NotSupportedSystemlessMode extends AbstractSystemlessMode {
    /**
     * Do not support systemless mode.
     *
     * @return <code>false</code>.
     */
    @Override
    public boolean isSupported() {
        return false;
    }

    /**
     * Do not report systemless mode enabled.
     *
     * @param context The application context (current activity).
     * @return <code>false</code>.
     */
    @Override
    boolean isEnabled(Context context, Shell shell) throws Exception {
        return false;
    }

    /**
     * Do not enable systemless mode.
     *
     * @param context The application context (current activity).
     * @return <code>false</code>.
     */
    @Override
    public boolean enable(Context context) {
        return false;
    }

    /**
     * Do not disable systemless mode.
     *
     * @param context The application context (current activity).
     * @return <code>false</code>.
     */
    @Override
    public boolean disable(Context context) {
        return false;
    }

    @Override
    public boolean isRebootNeededAfterActivation() {
        return false;
    }

    @Override
    public boolean isRebootNeededAfterDeactivation() {
        return false;
    }
}
