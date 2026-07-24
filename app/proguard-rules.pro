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

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# 反射（NumberPickerStyleHelper 访问 NumberPicker 内部字段）
-keepclassmembers class android.widget.NumberPicker {
    private android.graphics.Paint mSelectorWheelPaint;
}

# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# jieba 分词（保留词典文件）
-keep class com.huaban.analysis.jieba.** { *; }
-dontwarn com.huaban.analysis.jieba.**

# SnakeYAML
-dontwarn java.beans.**
-keep class org.yaml.snakeyaml.** { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod

# ML Kit Barcode Scanning & GMS (Resolve getClass() NPE on initialization)
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**
-keepclassmembers class * extends com.google.android.gms.internal.mlkit_vision_barcode_bundled.zzeh {
    <fields>;
}

# CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**