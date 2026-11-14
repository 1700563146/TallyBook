# TallyBook release rules.
#
# Hilt, Room, AndroidX and Compose ship their own consumer rules. kotlinx.serialization relies on
# generated serializer entry points, which R8 must retain even though most references are static.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-keepclassmembers class **$$serializer {
    public static ** INSTANCE;
    public static ** serializer(...);
}
-keepclasseswithmembers class * {
    public static ** serializer(...);
}

# Keep enough source metadata to produce actionable stack traces while still obfuscating names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
