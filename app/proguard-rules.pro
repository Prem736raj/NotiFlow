# NotiFlow ProGuard / R8 Rules

# Preserve annotations and reflection attributes
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature

# WorkManager worker constructors instantiated via reflection
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# FileProvider component
-keep class androidx.core.content.FileProvider { *; }

# Data models used in persistence and state
-keep class com.premraj.notiflow.data.** { *; }

# Services and Receivers declared in AndroidManifest
-keep class com.premraj.notiflow.service.** { *; }
-keep class com.premraj.notiflow.receiver.** { *; }

# Compose Composable functions
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}
