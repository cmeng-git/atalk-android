# Add project specific ProGuard rules here.
-dontobfuscate

# Preserve the line number information for debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# Library specific rules
-dontnote android.net.http.*
-dontnote org.apache.commons.codec.**
-dontnote org.apache.http.**
-dontnote de.cketti.safecontentresolver.**

-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.naming.**
-dontwarn com.sun.naming.internal.**

# Project specific rules
## -dontnote com.fsck.k9.ui.messageview.**
