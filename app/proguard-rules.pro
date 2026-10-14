# Room Database
-keep class com.epubreader.app.data.** { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
    @androidx.room.* <fields>;
}
-dontwarn androidx.room.**

# WebView & JS Bridge Reflection
-keepattributes *JavascriptInterface*
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.epubreader.app.ReaderActivity$* { *; }

# Glide
-keep public class * extends com.github.bumptech.glide.module.AppGlideModule { *; }
-keepattributes *Annotation*,Signature,EnclosingMethod

# Serialization, Reflection & Parcelables
-keepattributes InnerClasses,EnclosingMethod,Signature
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
-keep class com.epubreader.app.ui.** { *; }
-keep class com.epubreader.app.epub.** { *; }
-keep class com.epubreader.app.model.** { *; }
