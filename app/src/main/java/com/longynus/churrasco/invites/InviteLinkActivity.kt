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
        /**
         * Compartilha o convite: a imagem do cartão (InviteCard) com o texto e o link. Se a
         * imagem não puder ser gerada, vai só o texto, como antes.
         */
        fun share(activity: Activity, url: String, place: String, whenText: String, address: String = "", host: String = "") {
            val text = activity.getString(R.string.invite_share_text, whenText, place, url)
            val image = runCatching { InviteCard.save(activity, InviteCard.render(activity, place, whenText, address, host)) }.getOrNull()
            val send = Intent(Intent.ACTION_SEND)
                .putExtra(Intent.EXTRA_TEXT, text)
                .putExtra(Intent.EXTRA_SUBJECT, activity.getString(R.string.invite_share_subject))
            if (image != null) {
                send.setType("image/png").putExtra(Intent.EXTRA_STREAM, image).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                send.clipData = android.content.ClipData.newRawUri(null, image)
            } else send.setType("text/plain")
            activity.startActivity(Intent.createChooser(send, activity.getString(R.string.invite_share_title)))
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
