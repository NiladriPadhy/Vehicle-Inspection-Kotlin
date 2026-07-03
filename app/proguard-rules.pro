# Add project-specific ProGuard rules here.
# Keep Hilt-generated components and kotlinx.serialization metadata.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class kotlinx.serialization.json.** { *; }
