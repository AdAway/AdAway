package org.adaway.model.hostsinstall;

import android.content.Context;

import com.topjohnwu.superuser.io.SuFile;

import org.adaway.helper.PreferenceHelper;

import java.util.Arrays;
import java.util.Objects;

import static org.adaway.util.Constants.ANDROID_DATA_DATA_HOSTS;
import static org.adaway.util.Constants.ANDROID_DATA_HOSTS;
import static org.adaway.util.Constants.ANDROID_SYSTEM_ETC_HOSTS;

/**
 * This class is the model to represent hosts file installation location.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public enum HostsInstallLocation {
    SYSTEM("writeToSystem") {
        @Override
        public String getTarget(Context context) {
            return ANDROID_SYSTEM_ETC_HOSTS;
        }
    },
    DATA_DATA("writeToDataData") {
        @Override
        public String getTarget(Context context) {
            return ANDROID_DATA_DATA_HOSTS;
        }
    },
    DATA("writeToData") {
        @Override
        public String getTarget(Context context) {
            return ANDROID_DATA_HOSTS;
        }
    },
    CUSTOM_TARGET("customTarget") {
        @Override
        public String getTarget(Context context) {
            return PreferenceHelper.getCustomTarget(context);
        }
    };

    private final String preferenceKey;

    HostsInstallLocation(String preferenceKey) {
        this.preferenceKey = preferenceKey;
    }

    public static HostsInstallLocation fromPreferenceKey(String key) {
        Objects.requireNonNull(key, "Preference key could not be null.");
        return Arrays.stream(HostsInstallLocation.values())
                .filter(location -> location.preferenceKey.equals(key))
                .findAny()
                .orElse(SYSTEM);
    }

    public abstract String getTarget(Context context);

    public boolean requireSymlink(Context context) {
        String target = getTarget(context);
        // Resolve symlink
        String hosts = new SuFile(ANDROID_SYSTEM_ETC_HOSTS).getCanonicalPath();
        return !target.equals(hosts);
    }
}
