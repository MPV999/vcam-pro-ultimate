# VCAM PRO ULTIMATE VIP 2026 — R8 rules

# ML Kit (mô hình face detection đóng gói offline)
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_face.** { *; }
-dontwarn com.google.mlkit.**

# MediaPipe Tasks (mô hình landmark đóng gói trong assets)
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# Media3 Transformer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Coroutines
-dontwarn kotlinx.coroutines.**

# Giữ lại tên model dữ liệu để JSON metadata luôn đọc được
-keep class com.vcampro.ultimate.core.data.** { *; }
-keep class com.vcampro.ultimate.core.vision.** { *; }
