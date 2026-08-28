package com.example.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Centralized OkHttpClient to reuse connections and configure common settings.
 * TokenProvider can be wired to read tokens from SecurePrefs via GitHubConfigManager.initialize(...)
 */
object HttpClient {
    interface TokenProvider { fun getToken(): String? }
    var tokenProvider: TokenProvider? = null

    private val authInterceptor = object : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val original: Request = chain.request()
            val builder = original.newBuilder()
            val token = tokenProvider?.getToken()
            if (!token.isNullOrBlank()) {
                builder.header("Authorization", "Bearer $token")
            }
            return chain.proceed(builder.build())
        }
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .build()
}
