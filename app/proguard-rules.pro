# SafeRent ProGuard / R8 rules
# Default Android optimize rules are applied via proguard-android-optimize.txt

# Keep line numbers for readable crash stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep annotations & generic signatures (needed by Gson/Retrofit/Firebase models)
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Kotlin metadata
-keep class kotlin.Metadata { *; }

# --- Add model/data classes used for JSON or Firestore serialization below ---
# -keep class com.saferenttechnologies.android.data.model.** { *; }

# --- kotlinx.serialization: keep generated serializers for API DTOs ---
# (Retrofit resolves ApiEnvelope<JsonObject> reflectively via the Companion.)
-keepclassmembers @kotlinx.serialization.Serializable class com.saferenttechnologies.android.data.remote.dto.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.saferenttechnologies.android.data.remote.dto.**$$serializer { *; }
