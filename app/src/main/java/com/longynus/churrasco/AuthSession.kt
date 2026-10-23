package com.longynus.churrasco

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.longynus.churrasco.model.LoginRequest
import com.longynus.churrasco.model.Usuario
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * Liga a conta do Firebase Auth ao perfil do backend (username/displayName).
 * O perfil só vale para o mesmo UID que o abriu, para que trocar de conta
 * nunca reaproveite o nome de outra pessoa.
 */
object AuthSession {
    private const val PREFS_NAME = "ChurrascoApp"
    private const val KEY_USER_NAME = "userName"
    private const val KEY_DISPLAY_NAME = "displayName"
    private const val KEY_PROFILE_UID = "profileUid"
    private const val KEY_LAST_FCM_TOKEN = "lastFcmToken"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isReady(context: Context): Boolean {
        val user = FirebaseAuth.getInstance().currentUser ?: return false
        val prefs = prefs(context)
        return user.isEmailVerified &&
            prefs.getString(KEY_PROFILE_UID, null) == user.uid &&
            !prefs.getString(KEY_USER_NAME, null).isNullOrBlank()
    }

    fun userName(context: Context): String =
        prefs(context).getString(KEY_USER_NAME, "").orEmpty()

    fun saveProfile(context: Context, uid: String, profile: Usuario, fcmToken: String?) {
        prefs(context).edit()
            .putString(KEY_PROFILE_UID, uid)
            .putString(KEY_USER_NAME, profile.username)
            .putString(KEY_DISPLAY_NAME, profile.displayName)
            .putString(KEY_LAST_FCM_TOKEN, fcmToken)
            // Chave da versão sem login; não serve mais como prova de cadastro.
            .remove("userRegistered")
            .apply()
    }

    fun lastFcmToken(context: Context): String? =
        prefs(context).getString(KEY_LAST_FCM_TOKEN, null)

    fun rememberFcmToken(context: Context, token: String) {
        prefs(context).edit().putString(KEY_LAST_FCM_TOKEN, token).apply()
    }

    /** Desvincula as notificações deste aparelho e sai da conta. */
    fun signOut(context: Context, onDone: () -> Unit) {
        val appContext = context.applicationContext
        val finish = {
            FirebaseAuth.getInstance().signOut()
            prefs(appContext).edit()
                .remove(KEY_PROFILE_UID)
                .remove(KEY_USER_NAME)
                .remove(KEY_DISPLAY_NAME)
                .remove(KEY_LAST_FCM_TOKEN)
                .remove("userRegistered")
                .apply()
            onDone()
        }

        if (FirebaseAuth.getInstance().currentUser == null) {
            finish()
            return
        }

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            val token = if (task.isSuccessful) task.result else null
            if (token == null) {
                finish()
                return@addOnCompleteListener
            }

            RetrofitClient.instance.logout(LoginRequest(fcmToken = token))
                .enqueue(object : Callback<ApiResponse<Any>> {
                    override fun onResponse(
                        call: Call<ApiResponse<Any>>,
                        response: Response<ApiResponse<Any>>
                    ) = finish()

                    override fun onFailure(call: Call<ApiResponse<Any>>, t: Throwable) = finish()
                })
        }
    }
}
