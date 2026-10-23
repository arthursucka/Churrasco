package com.longynus.churrasco

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.longynus.churrasco.model.LoginRequest
import com.longynus.churrasco.model.Usuario
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

object FcmTokenManager {
    private const val TAG = "FcmTokenManager"

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

        if (!AuthSession.isReady(appContext)) {
            Log.d(TAG, "Token recebido antes do login; sincronização adiada.")
            return
        }

        if (AuthSession.lastFcmToken(appContext) == token) {
            return
        }

        RetrofitClient.instance.login(LoginRequest(fcmToken = token))
            .enqueue(object : Callback<ApiResponse<Usuario>> {
                override fun onResponse(
                    call: Call<ApiResponse<Usuario>>,
                    response: Response<ApiResponse<Usuario>>
                ) {
                    if (response.isSuccessful && response.body()?.success == true) {
                        AuthSession.rememberFcmToken(appContext, token)
                    } else {
                        Log.w(TAG, "Falha ao sincronizar token FCM: ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<ApiResponse<Usuario>>, t: Throwable) {
                    Log.w(TAG, "Erro de rede ao sincronizar token FCM", t)
                }
            })
    }
}
