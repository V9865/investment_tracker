# Proguard rules for application release builds

# 1. Strip all android.util.Log methods in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int println(...);
}

# 2. Preserve SQLCipher native and JNI interfaces
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-dontwarn net.sqlcipher.**

# 3. Preserve Room database & DAOs
-keep class * extends androidx.room.RoomDatabase {
    public <init>();
    public <init>(...);
}
-dontwarn androidx.room.paging.**

# 4. Preserve Kotlinx Serialization models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# 5. Preserve AndroidX Biometric and Security Crypto
-keep class androidx.biometric.** { *; }
-keep class androidx.security.crypto.** { *; }
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# 6. Preserve AndroidX WorkManager
-keep class androidx.work.** { *; }
-keep class androidx.work.impl.** { *; }
-keep class androidx.work.impl.WorkDatabase_Impl {
    public <init>();
}


