# Consumer proguard rules for data module

-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-dontwarn net.sqlcipher.**

-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
