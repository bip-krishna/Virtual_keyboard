-keep class com.visionboard.vision.model.** { *; }
-keep class com.visionboard.vision.hand.** { *; }
-keep class com.visionboard.vision.detector.** { *; }
-keep class com.visionboard.vision.keyboard.** { *; }
-keep class com.visionboard.vision.overlay.** { *; }

# MediaPipe
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# OpenCV
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**
