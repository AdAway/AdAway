-keep public class * extends android.content.ContentProvider

-keepclassmembers class io.sentry.Sentry {
    public static final boolean STUB;
}

-dontwarn com.google.**
-dontwarn io.sentry.**
