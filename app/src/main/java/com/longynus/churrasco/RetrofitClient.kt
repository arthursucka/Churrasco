// RetrofitClient.kt
package com.longynus.churrasco

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "https://fcm-server-oe8u.onrender.com/"
    private var initialized = false
    private lateinit var retrofit: Retrofit

    fun init(context: Context) {
        if (initialized) return

        val prefs = context.getSharedPreferences("ChurrascoApp", Context.MODE_PRIVATE)

        val client = OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .addInterceptor { chain ->
                val userName = prefs.getString("userName", "") ?: ""
                Log.d("RETROFIT", "Adicionando header X-User: $userName")
                val newRequest = chain.request().newBuilder()
                    .addHeader("X-User", userName)
                    .build()
                chain.proceed(newRequest)
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()

        retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        initialized = true
    }

    val instance: ApiService
        get() {
            check(::retrofit.isInitialized) {
                "RetrofitClient not initialized. Call RetrofitClient.init(context) in Application.onCreate()"
            }
            return retrofit.create(ApiService::class.java)
        }
}
