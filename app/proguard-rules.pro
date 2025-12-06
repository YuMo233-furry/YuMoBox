# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep line number information for debugging stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep Compose related classes
-keep class androidx.compose.** { *; }
-keep class kotlin.Metadata { *; }

# Keep Room database classes
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# Keep Coil image loading classes
-keep class coil.** { *; }
-keep class kotlinx.coroutines.** { *; }

# Keep ExoPlayer classes
-keep class com.google.android.exoplayer2.** { *; }

# Keep FileOperator classes
-keep class com.javakam.file.** { *; }

# Keep PhotoView classes
-keep class com.github.chrisbanes.photoview.** { *; }

# Keep Retrofit and Moshi classes
-keep class retrofit2.** { *; }
-keep class com.squareup.moshi.** { *; }

# Keep data classes with @Serializable
-keep @kotlinx.serialization.Serializable class * {
    <fields>;
}

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}