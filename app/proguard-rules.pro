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

### Room ###
# Room instantiates generated databases reflectively, including WorkManager's database.
# R8 full mode does not implicitly retain a kept class's no-argument constructor.
-keep class * extends androidx.room.RoomDatabase {
    public <init>();
}

# WorkManager also constructs its persisted input-merger class by reflection.
-keep class * extends androidx.work.InputMerger {
    public <init>();
}

### OkHttp ###
# A resource is loaded with a relative path so the package of this class must be preserved.
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

### dnsjava ###
-dontwarn lombok.Generated
-dontwarn sun.net.spi.nameservice.NameServiceDescriptor
# dnsjava 3.6.x ships a Java 18 desktop resolver SPI descriptor in its multi-release JAR.
# Android cannot provide java.net.spi resolver APIs; the service descriptor is excluded from
# packaged resources in app/build.gradle and these warnings are limited to that desktop SPI.
-dontwarn java.net.spi.InetAddressResolver
-dontwarn java.net.spi.InetAddressResolver$LookupPolicy
-dontwarn java.net.spi.InetAddressResolverProvider
-dontwarn java.net.spi.InetAddressResolverProvider$Configuration
-dontwarn org.xbill.DNS.spi.DnsjavaInetAddressResolverProvider
