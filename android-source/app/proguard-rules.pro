# Darkman-AI uses only platform APIs and OkHttp; keep the Activity entry point.
-keep public class com.darkman.MainActivity { public <methods>; }
-dontwarn okhttp3.**
-dontwarn okio.**
