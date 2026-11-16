# IssabelGate ProGuard / R8 Keep Rules

# Preserve annotations, generic signatures, and stack-trace line numbers
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# ------------------------------------------------------------------------------
# Linphone SDK (org.linphone.*) & Native JNI VoIP / Audio / SIP Stack
# ------------------------------------------------------------------------------
-keep class org.linphone.** { *; }
-keep interface org.linphone.** { *; }
-keep enum org.linphone.** { *; }
-keepclassmembers class org.linphone.** {
    <init>(...);
    *;
}
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-dontwarn org.linphone.**
-dontwarn javax.naming.**
-dontwarn java.beans.**

# ------------------------------------------------------------------------------
# Room Database, Entities, DAOs & Generated Implementations
# ------------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-keepclassmembers class * extends androidx.room.RoomDatabase {
    *;
}
-keep class * extends androidx.room.RoomDatabase$Callback {
    *;
}
-keep @androidx.room.Entity class * {
    *;
}
-keep @androidx.room.Dao interface * {
    *;
}
-keep @androidx.room.Dao class * {
    *;
}
-keep class com.example.data.local.** {
    *;
}
-dontwarn androidx.room.**

# ------------------------------------------------------------------------------
# Kotlinx Serialization (Provisioning Codec & Type-Safe Navigation Routes)
# ------------------------------------------------------------------------------
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.data.model.**$$serializer {
    *;
}
-keepclassmembers class com.example.data.model.** {
    *** Companion;
}
-keep,includedescriptorclasses class com.example.data.provisioning.**$$serializer {
    *;
}
-keepclassmembers class com.example.data.provisioning.** {
    *** Companion;
}
-keep class com.example.AppRoute { *; }
-keep class com.example.AppRoute$* { *; }

# ------------------------------------------------------------------------------
# Telecom ConnectionService, ForegroundService & BroadcastReceivers
# ------------------------------------------------------------------------------
-keep class com.example.engine.** { *; }
-keep class com.example.IssabelGateApp { *; }
