# Nexconn Chat SDK: JNI + reflection heavy; keep the whole SDK surface.
-keep class ai.nexconn.** { *; }
-keep class io.rong.** { *; }
-dontwarn ai.nexconn.**
-dontwarn io.rong.**

# Custom Nexconn message contents are created by reflection (public no-arg constructor).
-keep class app.campusbazaar.chat.messages.** { *; }

# kotlinx.serialization DTOs
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-keepclassmembers @kotlinx.serialization.Serializable class app.campusbazaar.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.campusbazaar.**$$serializer { *; }

# Retrofit interfaces use generic signatures at runtime.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
