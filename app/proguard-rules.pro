# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# 1. Jetpack Compose Rules
-keepclassmembers class * extends androidx.compose.runtime.State { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# 2. Room Database & SQLCipher
-keep class androidx.room.** { *; }
-keep class net.zetetic.** { *; }
-dontwarn net.zetetic.**
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class com.example.data.database.** { *; }
-keep class com.example.sync.local.** { *; }

# 3. Application Domain & Data Models (Leads, Reminders, Notes, Sync, Network, Security)
-keep class com.example.data.model.** { *; }
-keepclassmembers class com.example.data.model.** { *; }
-keep class com.example.data.network.** { *; }
-keepclassmembers class com.example.data.network.** { *; }
-keep class com.example.data.security.** { *; }
-keepclassmembers class com.example.data.security.** { *; }
-keep class com.example.sync.** { *; }
-keepclassmembers class com.example.sync.** { *; }

# 4. Kotlinx Serialization / JSON Parsing
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
    kotlinx.serialization.KSerializer serializer(...);
}

# 5. Firebase & Google Play Services
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# 6. Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}

# 7. Strip Debug Logs in Release Builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
