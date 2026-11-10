# Pleos Connect SDK: 서비스 연결(AIDL)과 직렬화 클래스를 그대로 보존
-keep class ai.pleos.** { *; }
-keep interface ai.pleos.** { *; }
-dontwarn ai.pleos.**

# osmdroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# kotlinx.serialization (Pleos NaviHelper 데이터 클래스가 사용)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
