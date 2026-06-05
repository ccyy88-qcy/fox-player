package com.foxplayer.util

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 全局共享 OkHttpClient 单例 — 连接复用，减少创建开销
 */
object HttpClientManager {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectionPool(okhttp3.ConnectionPool(4, 30, TimeUnit.SECONDS))
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "application/json, text/plain, */*")
                    .build()
                chain.proceed(req)
            }
            .build()
    }

    /** 快速GET请求获取字符串响应 */
    fun get(url: String): String? {
        return try {
            val req = okhttp3.Request.Builder().url(url).build()
            val resp = client.newCall(req).execute()
            resp.body?.string()
        } catch (_: Exception) { null }
    }
}
