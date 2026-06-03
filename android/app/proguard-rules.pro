# Keep the JNI-bound native methods (names must match the C++ in cpp/posedetectionYoloNAS.cpp).
-keepclasseswithmembernames class io.vyayama.pose.** {
    native <methods>;
}
# Coroutines / Compose defaults are handled by their bundled consumer rules.
