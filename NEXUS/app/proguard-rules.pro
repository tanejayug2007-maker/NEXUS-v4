# kotlinx.serialization — keep generated serializers for NEXUS models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.nexus.launcher.**$$serializer { *; }
-keepclassmembers class com.nexus.launcher.** { *** Companion; }
-keepclasseswithmembers class com.nexus.launcher.** { kotlinx.serialization.KSerializer serializer(...); }

# Widget host view subclass is created reflectively by AppWidgetHost
-keep class com.nexus.launcher.ui.widgets.NexusWidgetHostView { *; }
