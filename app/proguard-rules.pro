# Keep Gemini AI client classes
-keep class com.google.ai.client.generativeai.** { *; }
-keep class com.google.ai.client.** { *; }

# Keep Room entities
-keep class com.assistant.android.memory.entity.** { *; }

# Keep accessibility service
-keep class com.assistant.android.automation.AssistantAccessibilityService { *; }

# Standard Android keep rules
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
