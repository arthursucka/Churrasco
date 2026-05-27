package com.longynus.churrasco

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.longynus.churrasco.model.LoginRequest
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

object FcmTokenManager {
    private const val TAG = "FcmTokenManager"
    private const val PREFS_NAME = "ChurrascoApp"
    private const val KEY_USER_NAME = "userName"
    private const val KEY_USER_REGISTERED = "userRegistered"
    private const val KEY_LAST_FCM_TOKEN = "lastFcmToken"

    fun syncCurrentToken(context: Context) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Não foi possível obter token FCM", task.exception)
                return@addOnCompleteListener
            }

            syncToken(context, task.result)
        }
    }

    fun syncToken(context: Context, token: String) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val userName = prefs.getString(KEY_USER_NAME, "")?.trim().orEmpty()
        val registered = prefs.getBoolean(KEY_USER_REGISTERED, false)
        val lastToken = prefs.getString(KEY_LAST_FCM_TOKEN, null)

        if (userName.isBlank() || !registered) {
            Log.d(TAG, "Token recebido antes de usuário registrado; sincronização adiada.")
            return
        }

        if (lastToken == token) {
            Log.d(TAG, "Token FCM já sincronizado.")
            return
        }

        RetrofitClient.instance.login(LoginRequest(username = userName, fcmToken = token))
            .enqueue(object : Callback<ApiResponse<Any>> {
                override fun onResponse(
                    call: Call<ApiResponse<Any>>,
                    response: Response<ApiResponse<Any>>
                ) {
                    if (response.isSuccessful && response.body()?.success == true) {
                        prefs.edit()
                            .putString(KEY_LAST_FCM_TOKEN, token)
                            .apply()
                        Log.d(TAG, "Token FCM sincronizado com backend.")
                    } else {
                        Log.w(TAG, "Falha ao sincronizar token FCM: ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<ApiResponse<Any>>, t: Throwable) {
                    Log.w(TAG, "Erro de rede ao sincronizar token FCM", t)
                }
            })
    }
}
