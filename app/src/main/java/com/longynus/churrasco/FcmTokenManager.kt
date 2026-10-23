package com.longynus.churrasco

import android.content.Context
import com.longynus.churrasco.data.ChurrascoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Mantém o token de notificação do aparelho atualizado no perfil. */
object FcmTokenManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun syncCurrentToken(context: Context) {
        val app = context.applicationContext
        scope.launch { AuthSession.currentFcmToken()?.let { syncToken(app, it) } }
    }

    fun syncToken(context: Context, token: String) {
        val app = context.applicationContext
        if (!AuthSession.isReady(app) || AuthSession.lastFcmToken(app) == token) return
        scope.launch {
            ChurrascoRepository.login(token).onSuccess {
                AuthSession.saveProfile(app, it, token)
            }
        }
    }
}
