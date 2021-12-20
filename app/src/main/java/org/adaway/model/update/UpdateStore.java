package org.adaway.model.update;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static android.content.pm.PackageManager.GET_SIGNATURES;
import static android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.P;

import timber.log.Timber;

/**
 * This enumerates represents the stores to get AdAway updates.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public enum UpdateStore {
    /**
     * The official store (usually GitHub releases) with AdAway signing key.
     */
    ADAWAY("adaway", "D647FDAC42961502AC78F99919B8E1901747E8DA78FE13E1EABA688FECC4C99E"),
    /**
     * The F-Droid store with F-Droid signing key.
     */
    F_DROID("fdroid", "42203F1AC857426D1496E971DB96FBE1F88C25C9E1F895A5C98D703891292277"),
    /**
     * An unknown store.
     */
    UNKNOWN("unknown", "");
    /**
     * The store name.
     */
    public final String name;
    /**
     * The store singing certificate digest.
     */
    public final String sign;

    UpdateStore(String name, String sign) {
        this.name = name;
        this.sign = sign;
    }

    /**
     * Get the store of the running application.
     *
     * @param context The application context.
     * @return The application store, {@link #UNKNOWN} if store can't be defined.
     */
    public static UpdateStore getApkStore(Context context) {
        PackageManager packageManager = context.getPackageManager();
        String packageName = context.getPackageName();
        Signature[] signatures;
        try {
            if (SDK_INT >= P) {
                signatures = packageManager.getPackageInfo(
                        packageName,
                        GET_SIGNING_CERTIFICATES
                ).signingInfo.getSigningCertificateHistory();
            } else {
                signatures = packageManager.getPackageInfo(
                        packageName,
                        GET_SIGNATURES
                ).signatures;
            }
        } catch (PackageManager.NameNotFoundException e) {
            Timber.w(e, "Failed to get application package info.");
            return UpdateStore.UNKNOWN;
        }
        return UpdateStore.getFromSigns(signatures);
    }

    private static UpdateStore getFromSigns(Signature[] signatures) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (Signature signature : signatures) {
                md.update(signature.toByteArray());
                String sign = bytesToHex(md.digest());
                for (UpdateStore store : UpdateStore.values()) {
                    if (store.sign.equals(sign)) {
                        return store;
                    }
                }
            }
        } catch (NoSuchAlgorithmException e) {
            Timber.w(e, "SHA-256 algorithm is no supported.");
        }
        return UpdateStore.UNKNOWN;
    }

    private static String bytesToHex(byte[] bytes) {
        final char[] hexArray = {'0', '1', '2', '3', '4', '5', '6', '7', '8',
                '9', 'A', 'B', 'C', 'D', 'E', 'F'};
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for (int j = 0; j < bytes.length; j++) {
            v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    /**
     * Get the store name.
     *
     * @return The store name.
     */
    public String getName() {
        return this.name;
    }
}
