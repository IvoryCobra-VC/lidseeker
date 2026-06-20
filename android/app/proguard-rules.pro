# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.lidseeker.app.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.lidseeker.app.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
