# error-prone annotations are referenced by tink (via security-crypto) but
# are not present at runtime — safe to ignore.
-dontwarn com.google.errorprone.annotations.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.lidseeker.app.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.lidseeker.app.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
