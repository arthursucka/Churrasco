// RetrofitClient.kt
package com.longynus.churrasco

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "https://fcm-server-oe8u.onrender.com/"
    private var initialized = false
    private lateinit var api: ApiService

    fun init(context: Context) {
        if (initialized) return

        val builder = OkHttpClient.Builder()
            .addInterceptor { chain ->
                // O backend só aceita o ID token do Firebase Auth como prova de identidade.
                val request = chain.request().newBuilder()
                currentIdToken()?.let { request.header("Authorization", "Bearer $it") }
                chain.proceed(request.build())
            }
            // O servidor no Render gratuito pode levar ~1 min para acordar.
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(75, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
        }

        api = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(builder.build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)

        initialized = true
    }

    // Roda na thread do OkHttp, então pode esperar o token (o SDK renova quando expira).
    private fun currentIdToken(): String? {
        val user = FirebaseAuth.getInstance().currentUser ?: return null
        return runCatching {
            Tasks.await(user.getIdToken(false), 20, TimeUnit.SECONDS).token
        }.getOrNull()
    }

    val instance: ApiService
        get() {
            check(initialized) {
                "RetrofitClient not initialized. Call RetrofitClient.init(context) in Application.onCreate()"
            }
            return api
        }
}
