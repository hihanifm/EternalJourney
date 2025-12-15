# Add project specific ProGuard rules here.

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep data classes
-keep class com.hanifm.eternaljourney.audio.AudioFileInfo { *; }
-keep class com.hanifm.eternaljourney.bluetooth.BluetoothDeviceInfo { *; }

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Media3 ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep service classes
-keep class com.hanifm.eternaljourney.service.** { *; }
-keep class com.hanifm.eternaljourney.receiver.** { *; }

# Keep ViewModels
-keep class com.hanifm.eternaljourney.ui.viewmodel.** { *; }

# Keep all logs for troubleshooting
# Debug logs are kept to help diagnose issues in production