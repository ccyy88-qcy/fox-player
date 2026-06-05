# ExoPlayer
-keep class com.google.android.exoplayer2.** { *; }
-keep interface com.google.android.exoplayer2.** { *; }

# Jsoup
-keep class org.jsoup.** { *; }

# Gson
-keepattributes Signature
-keep class com.google.gson.** { *; }
-keep class com.foxplayer.model.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
