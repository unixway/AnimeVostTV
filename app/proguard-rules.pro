# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /home/zakon/Android/Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep rules here:

# Glide
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public class * extends com.bumptech.glide.module.LibraryGlideModule
-keep class com.bumptech.glide.GeneratedAppGlideModuleImpl

# Ktor
-keep class io.ktor.** { *; }

# Media3
-keep class androidx.media3.** { *; }
