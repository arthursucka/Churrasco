package com.longynus.churrasco.invites

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.longynus.churrasco.ActiveChurrascoDetailsActivity
import com.longynus.churrasco.AuthSession
import com.longynus.churrasco.LoginActivity
import com.longynus.churrasco.R
import com.longynus.churrasco.data.ChurrascoRepository
import com.longynus.churrasco.ui.ScreenActivity
import com.longynus.churrasco.ui.TextRole
import com.longynus.churrasco.ui.emptyState
import com.longynus.churrasco.ui.text

/**
 * Abre os links `https://.../convite/<token>` (App Link) e aceita o convite. Sem login,
 * guarda o convite e manda para a entrada; a tela principal retoma depois.
 */
class InviteLinkActivity : ScreenActivity() {

    companion object {
        const val EXTRA_TOKEN = "inviteToken"
        private val TOKEN = Regex("^[A-Za-z0-9_-]{16,64}$")

        fun tokenFrom(intent: Intent): String? {
            intent.getStringExtra(EXTRA_TOKEN)?.let { return it.takeIf(TOKEN::matches) }
            val segments = intent.data?.pathSegments.orEmpty()
            val index = segments.indexOf("convite")
            return segments.getOrNull(index + 1)?.takeIf { index >= 0 && TOKEN.matches(it) }
        }

        /** Abre a lista de apps do Android (WhatsApp, e-mail...) com o convite. */
        fun share(activity: Activity, url: String, place: String, whenText: String) {
            val text = activity.getString(R.string.invite_share_text, whenText, place, url)
            activity.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                        .putExtra(Intent.EXTRA_SUBJECT, activity.getString(R.string.invite_share_subject)),
                    activity.getString(R.string.invite_share_title),
                )
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupScreen(getString(R.string.invite_title))
        val token = tokenFrom(intent)
        if (token == null) {
            render { emptyState(R.drawable.ic_link, getString(R.string.invite_invalid_title), getString(R.string.invite_invalid_body)) }
            return
        }
        if (!AuthSession.isReady(this)) {
            AuthSession.setPendingInvite(this, token)
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        render { text(getString(R.string.invite_opening), TextRole.Muted) }
        call({ ChurrascoRepository.acceptInvite(token) }, retry = true) { event ->
            startActivity(Intent(this, ActiveChurrascoDetailsActivity::class.java).putExtra("churrascoId", event.id))
            finish()
        }
    }
}
