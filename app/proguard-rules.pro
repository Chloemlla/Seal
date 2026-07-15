# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

#noinspection ShrinkerUnresolvedReference

# Keep diagnostics useful for crash reports after minify.
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# obfuscation enabled for release (audit fix)

# Native download engines (youtubedl-android / ffmpeg / aria2c)
-keep class com.yausername.** { *; }
-keep class org.apache.commons.compress.archivers.zip.** { *; }
-dontwarn com.yausername.**
-dontwarn org.apache.commons.compress.**

# MMKV is loaded reflectively / via JNI.
-keep class com.tencent.mmkv.** { *; }
-dontwarn com.tencent.mmkv.**

############################################################
# Lumen Crash SDK minify exemption
# Artifact: com.chloemlla.lumen:lumen-crash
# Required when release minify/resource shrink is enabled so author
# integrity + public API symbols remain available after R8.
# Missing these can white-screen / fail-closed at Application install.
############################################################

# Required: author attribution constants must keep source values/names.
-keep class com.chloemlla.lumen.crash.CrashAuthorAttribution {
    public static final java.lang.String *;
    public static *** payload();
}
-keepclassmembers class com.chloemlla.lumen.crash.CrashAuthorAttribution {
    public static final java.lang.String *;
}

# Required: integrity entry points used on install / report / UI open.
-keep class com.chloemlla.lumen.crash.AuthorIntegrity {
    public static *** verifyOrThrow(...);
    public static *** fingerprintHex();
    public static *** verifiedAuthorBlock();
}
-keep class com.chloemlla.lumen.crash.AuthorBlock { *; }

# Required: public host integration API.
-keep class com.chloemlla.lumen.crash.LumenCrash { *; }
-keep class com.chloemlla.lumen.crash.LumenCrashConfig { *; }
-keep class com.chloemlla.lumen.crash.CrashReport { *; }
-keep class com.chloemlla.lumen.crash.CrashAppInfo { *; }
-keep class com.chloemlla.lumen.crash.CrashReportStore { *; }
-keep class com.chloemlla.lumen.crash.CrashBreadcrumbs { *; }
-keep class com.chloemlla.lumen.crash.ui.LumenCrashReportScreenKt { *; }

# Package-level exemption (safe default for release minify hosts).
-keep class com.chloemlla.lumen.crash.** { *; }
-keepclassmembers class com.chloemlla.lumen.crash.** { *; }
-keepnames class com.chloemlla.lumen.crash.**
-dontwarn com.chloemlla.lumen.crash.**

# Monet / dynamic color (module :color also keeps these; keep here as backup).
-keep class com.kyant.monet.** { *; }
-keep class io.material.hct.** { *; }
-dontwarn com.kyant.monet.**
-dontwarn io.material.hct.**

# Compose / AndroidX noise suppression for optimized release builds.
-dontwarn androidx.compose.**
-dontwarn androidx.lifecycle.**
-dontwarn androidx.navigation.**
-dontwarn androidx.room.**

# Keep `Companion` object fields of serializable classes.
# This avoids serializer lookup through `getDeclaredClasses` as done for named companion objects.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects (both default and named) of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep kotlinx.serialization generated serializers referenced at runtime.
-keepclassmembers class **$$serializer {
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn kotlinx.serialization.**
