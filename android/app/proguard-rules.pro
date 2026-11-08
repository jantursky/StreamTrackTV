# kotlinx.serialization: the plugin generates serializers that R8 must keep together with their
# companion lookups. The library ships most rules itself; these cover the app's @Serializable types.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses,Signature
-keepclassmembers @kotlinx.serialization.Serializable class com.apptiary.streamtracktv.** {
    *** Companion;
    static *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.apptiary.streamtracktv.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room ships consumer rules for its runtime; the generated database implementation is created by
# reflection from its class name.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Ktor references optional JVM classes that do not exist on Android.
-dontwarn java.lang.management.**
-dontwarn org.slf4j.**
