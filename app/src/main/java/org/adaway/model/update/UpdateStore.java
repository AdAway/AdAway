package org.adaway.model.update;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

import org.adaway.BuildConfig;
import org.adaway.R;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

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
     * Direct APK distribution. The legacy digest identifies upstream builds only.
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
    public final String storeName;
    /**
     * The store singing certificate digest.
     */
    public final String sign;

    UpdateStore(String name, String sign) {
        this.storeName = name;
        this.sign = sign;
    }

    /**
     * Get the store of the running application.
     *
     * @param context The application context.
     * @return The application store, {@link #UNKNOWN} if store can't be defined.
     */
    @SuppressLint("PackageManagerGetSignatures")
    public static UpdateStore getApkStore(Context context) {
        PackageManager packageManager = context.getPackageManager();
        String packageName = context.getPackageName();
        Signature[] signatures;
        try {
            if (SDK_INT >= P) {
                PackageInfo info = packageManager.getPackageInfo(
                        packageName, GET_SIGNING_CERTIFICATES);
                if (info.signingInfo == null) {
                    return UNKNOWN;
                }
                signatures = info.signingInfo.hasMultipleSigners()
                        ? info.signingInfo.getApkContentsSigners()
                        : info.signingInfo.getSigningCertificateHistory();
            } else {
                // Signatures are not used for security reason. Only to guess the flavor of the app.
                signatures = packageManager.getPackageInfo(
                        packageName,
                        GET_SIGNATURES
                ).signatures;
            }
        } catch (PackageManager.NameNotFoundException e) {
            Timber.w(e, "Failed to get application package info.");
            return UpdateStore.UNKNOWN;
        }
        return UpdateStore.getFromSigns(signatures, BuildConfig.DIRECT_APK_UPDATES_ENABLED,
                context.getString(R.string.release_signing_certificate_sha256));
    }

    private static UpdateStore getFromSigns(
            Signature[] signatures, boolean directRelease, String configuredCertificate) {
        if (signatures == null || signatures.length == 0) {
            return UNKNOWN;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            Set<String> digests = new HashSet<>();
            for (Signature signature : signatures) {
                digests.add(bytesToHex(md.digest(signature.toByteArray())));
            }
            return fromCertificateDigests(digests, directRelease, configuredCertificate);
        } catch (NoSuchAlgorithmException e) {
            Timber.w(e, "SHA-256 algorithm is no supported.");
        }
        return UpdateStore.UNKNOWN;
    }

    static UpdateStore fromCertificateDigests(
            Set<String> digests, boolean directRelease, String configuredCertificate) {
        if (digests.contains(F_DROID.sign)) {
            return F_DROID;
        }
        if (directRelease) {
            String expected = configuredCertificate.replaceAll("[:\\s]", "")
                    .toUpperCase(Locale.ROOT);
            return expected.matches("[0-9A-F]{64}") && digests.contains(expected)
                    ? ADAWAY : UNKNOWN;
        }
        return digests.contains(ADAWAY.sign) ? ADAWAY : UNKNOWN;
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
        return this.storeName;
    }
}
