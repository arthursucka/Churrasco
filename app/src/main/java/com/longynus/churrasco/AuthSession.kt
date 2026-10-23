package com.longynus.churrasco

import android.app.Activity
import android.content.Context
import androidx.core.content.edit
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.messaging.FirebaseMessaging
import com.longynus.churrasco.data.ChurrascoRepository
import com.longynus.churrasco.data.model.Profile
import com.longynus.churrasco.location.LocationShareService
import com.longynus.churrasco.reminders.ReminderScheduler
import kotlinx.coroutines.tasks.await

/**
 * Liga a conta do Firebase Auth ao perfil do backend. O perfil guardado só vale para o
 * mesmo UID que o abriu, então trocar de conta nunca reaproveita dados de outra pessoa.
 */
object AuthSession {
    private const val PREFS = "ChurrascoApp"
    private const val KEY_UID = "profileUid"
    private const val KEY_NAME = "displayName"
    private const val KEY_PIX = "pixKey"
    private const val KEY_FCM = "lastFcmToken"
    private const val KEY_PENDING_INVITE = "pendingInviteToken"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Login pelo Google já vem com e-mail confirmado. */
    fun emailConfirmed(): Boolean {
        val user = FirebaseAuth.getInstance().currentUser ?: return false
        return user.isEmailVerified || user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }
    }

    fun isReady(context: Context): Boolean {
        val user = FirebaseAuth.getInstance().currentUser ?: return false
        return emailConfirmed() && prefs(context).getString(KEY_UID, null) == user.uid
    }

    fun uid(context: Context): String = prefs(context).getString(KEY_UID, "").orEmpty()
    fun displayName(context: Context): String = prefs(context).getString(KEY_NAME, "").orEmpty()
    fun pixKey(context: Context): String? = prefs(context).getString(KEY_PIX, null)

    fun saveProfile(context: Context, profile: Profile, fcmToken: String? = lastFcmToken(context)) {
        prefs(context).edit {
            putString(KEY_UID, profile.uid)
            putString(KEY_NAME, profile.displayName)
            putString(KEY_PIX, profile.pixKey)
            putString(KEY_FCM, fcmToken)
            // Chaves da v1 (nome como identidade): não servem mais para nada.
            remove("userName"); remove("userRegistered"); remove("firebaseUid")
        }
    }

    fun lastFcmToken(context: Context): String? = prefs(context).getString(KEY_FCM, null)
    fun rememberFcmToken(context: Context, token: String) = prefs(context).edit { putString(KEY_FCM, token) }

    /** Convite aberto antes do login: é aceito assim que a conta estiver pronta. */
    fun setPendingInvite(context: Context, token: String?) = prefs(context).edit { putString(KEY_PENDING_INVITE, token) }
    fun takePendingInvite(context: Context): String? =
        prefs(context).getString(KEY_PENDING_INVITE, null)?.also { setPendingInvite(context, null) }

    suspend fun currentFcmToken(): String? = runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()

    /** Abre a escolha de conta Google e entra no Firebase com ela. */
    suspend fun signInWithGoogle(activity: Activity): Result<Unit> {
        val clientIdRes = activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName)
        if (clientIdRes == 0) return Result.failure(IllegalStateException(activity.getString(R.string.login_google_not_configured)))
        return runCatching {
            val option = GetSignInWithGoogleOption.Builder(activity.getString(clientIdRes)).build()
            val response = CredentialManager.create(activity)
                .getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(option).build())
            val credential = response.credential
            require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
            val google = GoogleIdTokenCredential.createFrom(credential.data)
            FirebaseAuth.getInstance().signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null)).await()
            Unit
        }.recoverCatching { error ->
            if (error is GetCredentialCancellationException) throw CancelledException() else throw error
        }
    }

    class CancelledException : Exception()

    /** Desvincula as notificações deste aparelho, para o mapa e os lembretes e sai da conta. */
    suspend fun signOut(context: Context) {
        val app = context.applicationContext
        if (FirebaseAuth.getInstance().currentUser != null) {
            currentFcmToken()?.let { ChurrascoRepository.logout(it) }
        }
        LocationShareService.stop(app)
        ReminderScheduler.clear(app)
        FirebaseAuth.getInstance().signOut()
        runCatching { CredentialManager.create(app).clearCredentialState(ClearCredentialStateRequest()) }
        prefs(app).edit { remove(KEY_UID); remove(KEY_NAME); remove(KEY_PIX); remove(KEY_FCM) }
    }
}
