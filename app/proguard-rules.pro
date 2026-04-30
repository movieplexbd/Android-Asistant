-keep class com.jarvis.ceotitan.** { *; }
-keep class org.vosk.** { *; }
-keep class com.google.mlkit.** { *; }
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
-keep class okhttp3.** { *; }
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
