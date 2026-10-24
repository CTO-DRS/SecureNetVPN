# ---- SecureNet VPN custom ProGuard/R8 rules ----

# Keep everything referenced from JNI. Native code resolves these symbols
# reflectively at runtime, so R8 must not rename or remove them.
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.securenet.vpn.nativelib.** { *; }

# WireGuard Go bridge: the settings strings are parsed by the Go layer.
-keep class com.securenet.vpn.util.KeyUtils { *; }
-keep class com.securenet.vpn.util.WireGuardConfigParser { *; }

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepclasseswithmembers class okhttp3.internal.** { *; }

# Lottie ships consumer rules; this is a defensive keep for JSON model classes.
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# Coroutines
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# Keep data classes used across the JNI / JSON boundaries intact.
-keep class com.securenet.vpn.data.model.** { *; }
-keep class com.securenet.vpn.data.remote.** { *; }

# androidX security crypto
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.api.client.**

# WorkManager
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

# Preserve source file names + line numbers for crash reports (release only).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Crash-and-burn for unresolved issues: log instead of failing silently.
-printusage build/usage.txt
-printmapping build/mapping.txt
