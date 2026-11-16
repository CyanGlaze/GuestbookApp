# 保留 Gson 反射用到的泛型签名
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.example.liyibook.model.** { *; }
-keep class com.google.gson.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
