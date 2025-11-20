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

# ========================================
# DATADOG SDK - REGLAS CRÍTICAS
# ========================================

# Datadog SDK - Mantener todas las clases
-keep class com.datadog.** { *; }
-keep interface com.datadog.** { *; }
-dontwarn com.datadog.**

# Datadog RUM
-keep class com.datadog.android.rum.** { *; }
-keep interface com.datadog.android.rum.** { *; }

# Datadog Logs
-keep class com.datadog.android.log.** { *; }
-keep interface com.datadog.android.log.** { *; }

# Datadog Trace
-keep class com.datadog.android.trace.** { *; }
-keep interface com.datadog.android.trace.** { *; }

# OkHttp (usado por Datadog)
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Gson (usado por Datadog para serialización)
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Mantener clases de modelos que se serializan
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ========================================
# RETROFIT & NETWORKING
# ========================================

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# ========================================
# FIREBASE
# ========================================

-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ========================================
# KOTLIN & COROUTINES
# ========================================

-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.coroutines.**

# ========================================
# HILT / DAGGER
# ========================================

-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-dontwarn dagger.**

# ========================================
# MODELOS DE DATOS (ajusta según tu app)
# ========================================

# Mantener todas las clases de modelo
-keep class com.roomu.app.data.model.** { *; }
-keep class com.roomu.app.domain.model.** { *; }

# Mantener clases que usan @SerializedName
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ========================================
# GENERAL
# ========================================

# Mantener información de números de línea para stack traces
-keepattributes SourceFile,LineNumberTable

# Renombrar archivo fuente para ofuscar
-renamesourcefileattribute SourceFile