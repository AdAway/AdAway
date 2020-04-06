-keep public class * extends android.content.ContentProvider

# Temporary fix for androidx preference fragement reference
# See https://issuetracker.google.com/issues/145316223
-keep public class org.adaway.ui.prefs.PrefsBackupRestoreFragment
-keep public class org.adaway.ui.prefs.PrefsRootFragment
-keep public class org.adaway.ui.prefs.PrefsUpdateFragment
-keep public class org.adaway.ui.prefs.PrefsVpnFragment

-keepclassmembers class io.sentry.Sentry {
    public static final boolean STUB;
}

-dontobfuscate

### Android Jetpack ###
-dontwarn com.google.**

### Sentry ###
-dontwarn io.sentry.**

### OkHttp ###
# JSR 305 annotations are for embedding nullability information.
-dontwarn javax.annotation.**
# A resource is loaded with a relative path so the package of this class must be preserved.
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
# Animal Sniffer compileOnly dependency to ensure APIs are compatible with older versions of Java.
-dontwarn org.codehaus.mojo.animal_sniffer.*
# OkHttp platform used only on JVM and when Conscrypt dependency is available.
-dontwarn okhttp3.internal.platform.ConscryptPlatform
