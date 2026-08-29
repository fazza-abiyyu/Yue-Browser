# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# WebView
-keep class android.webkit.** { *; }
-keep class androidx.webkit.** { *; }
-dontwarn android.webkit.**

# ML Kit Barcode Scanning
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# CameraX View
-keep class androidx.camera.view.** { *; }
-dontwarn androidx.camera.view.**

# Kotlin Coroutines
-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Navigation
-keep class androidx.navigation.** { *; }

# DataStore
-keep class androidx.datastore.** { *; }

# Retrofit / OkHttp (if used)
-keepattributes Signature, InnerClasses, EnclosingMethod

# Keep generic type info for serialization
-keepattributes *Annotation*, Signature

# Keep all classes in the app package
-keep class com.yue.browser.** { *; }
-keepclassmembers class com.yue.browser.** { *; }
