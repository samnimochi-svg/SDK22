# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Shizuku API keep rules
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

# Keep models for reflection/serialization
-keep class com.example.model.** { *; }

# Conscrypt JNI & security provider keep rules
-keep class org.conscrypt.** { *; }
-dontwarn org.conscrypt.**

# libadb keep rules
-keep class io.github.muntashirakon.adb.** { *; }
-dontwarn io.github.muntashirakon.adb.**

# sun.security key generation keep rules
-keep class android.sun.security.** { *; }
-dontwarn android.sun.security.**

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
