# Add project specific ProGuard rules here.
-dontobfuscate
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontpreverify
-allowaccessmodification
-verbose
-optimizations !code/simplification/arithmetic,!field/*,field/propagation/value,!class/merging/*,!code/allocation/variable

# Preserve the line number information for debugging stack traces.
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-renamesourcefileattribute SourceFile

-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference

-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}

-keepclasseswithmembernames class * {
    native <methods>;
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

-keepclassmembers class * extends android.app.Activity {
   public void *(android.view.View);
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

-dontwarn com.yalantis.ucrop**
-keep class com.yalantis.ucrop** { *; }
-keep interface com.yalantis.ucrop** { *; }

-dontwarn org.jivesoftware.**
-keep class org.igniterealtime.jbosh.**{*;}
-keep class org.jivesoftware.smack.**{*;}
-keep class org.jivesoftware.smackx.**{*;}
-keep interface org.jivesoftware.smack.** {*;}
-keep interface org.jivesoftware.smackx.** {*;}
-keepclasseswithmembers class de.measite.smack.** {*;}

-keepclasseswithmembers class * extends org.jivesoftware.smack.sasl.SASLMechanism {
	public <init>(org.jivesoftware.smack.SASLAuthentication);
}

-keep class android.support.v4.app.** { *; }
-keep interface android.support.v4.app.** { *; }
# -keep public class * extends androidx.preference.PreferenceFragmentCompat

# Library specific rules
-dontnote android.net.http.*
-dontnote org.apache.commons.codec.**
-dontnote org.apache.http.**
-dontnote de.cketti.safecontentresolver.**

-dontwarn android.app.**
-dontwarn android.javax.xml.transform.**
-dontwarn com.sun.naming.internal.**

-dontwarn java.awt.**
-dontwarn java.beans.**
-dontwarn javax.imageio.**
-dontwarn javax.naming.**
-dontwarn com.bumptech.glide.load.resource.bitmap.VideoDecoder