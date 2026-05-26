# ProGuard Rules for Movie Aggregator

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Gson
-keep class com.aggregator.movie.data.model.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ExoPlayer / Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# JSoup
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# Compose
-dontwarn androidx.compose.**

# Keep data models
-keep class com.aggregator.movie.data.model.Movie { *; }
-keep class com.aggregator.movie.data.model.PlaySource { *; }
-keep class com.aggregator.movie.data.model.Episode { *; }
-keep class com.aggregator.movie.data.model.PlayUrl { *; }
-keep class com.aggregator.movie.data.model.HomeData { *; }
-keep class com.aggregator.movie.data.model.SearchResult { *; }
-keep class com.aggregator.movie.data.model.Category { *; }
-keep class com.aggregator.movie.data.model.MovieType { *; }
-keep class com.aggregator.movie.data.model.VideoFormat { *; }
