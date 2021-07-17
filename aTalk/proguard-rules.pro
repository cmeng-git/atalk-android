# Add project specific ProGuard rules here.
-dontobfuscate

# Preserve the line number information for debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# Library specific rules
-dontnote android.net.http.*
-dontnote org.apache.commons.codec.**
-dontnote org.apache.http.**
-dontnote de.cketti.safecontentresolver.**

-dontwarn android.app.**
-dontwarn android.javax.xml.transform.**
-dontwarn com.sun.naming.internal.**

-dontwarn org.atalk.android.util.javax.swing.**
-dontwarn org.atalk.android.util.java.awt.**

-dontwarn java.awt.**
-dontwarn java.beans.**
-dontwarn javax.imageio.**
-dontwarn javax.naming.**
-dontwarn com.bumptech.glide.load.resource.bitmap.VideoDecoder