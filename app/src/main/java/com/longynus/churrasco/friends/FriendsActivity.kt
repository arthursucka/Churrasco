package com.longynus.churrasco.friends

import android.os.Bundle
import android.text.InputType
import com.longynus.churrasco.R
import com.longynus.churrasco.data.ChurrascoRepository
import com.longynus.churrasco.data.model.FriendsPayload
import com.longynus.churrasco.ui.avatar
import com.longynus.churrasco.ui.ScreenActivity
import com.longynus.churrasco.ui.TextRole
import com.longynus.churrasco.ui.card
import com.longynus.churrasco.ui.emptyState
import com.longynus.churrasco.ui.primaryButton
import com.longynus.churrasco.ui.row
import com.longynus.churrasco.ui.section
import com.longynus.churrasco.ui.text
import com.longynus.churrasco.ui.textButton
import com.longynus.churrasco.ui.textField

/** Lista de amigos: adicionar por e-mail, aceitar pedidos e remover. */
class FriendsActivity : ScreenActivity() {

    private var data = FriendsPayload()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupScreen(getString(R.string.friends_title), onRefresh = ::load, navItem = R.id.nav_friends)
        draw()
        load()
    }

    private fun load(): Unit = call({ ChurrascoRepository.friends() }, retry = true, silent = data.friends.isNotEmpty()) {
        data = it
        draw()
    }

    private fun draw(): Unit = render {
        text(getString(R.string.friends_title), TextRole.Title)
        text(getString(R.string.friends_subtitle), TextRole.Muted, top = 8)

        card(top = 24) {
            text(getString(R.string.friends_add_title), TextRole.Strong)
            val email = textField(getString(R.string.login_email_label), inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
            primaryButton(getString(R.string.friends_add_action)) {
                val value = email.text.toString().trim()
                if (value.isBlank()) return@primaryButton
                call({ ChurrascoRepository.addFriend(value) }) { status ->
                    toast(getString(if (status.status == "accepted") R.string.friends_now_friends else R.string.friends_request_sent))
                    load()
                }
            }
        }

        if (data.incoming.isNotEmpty()) {
            section(getString(R.string.friends_incoming_title), getString(R.string.friends_incoming_subtitle))
            data.incoming.forEach { friend ->
                card(top = 12) {
                    row(context.avatar(friend.displayName, friend.uid), friend.displayName, friend.email, top = 0)
                    primaryButton(getString(R.string.friends_accept)) {
                        call({ ChurrascoRepository.acceptFriend(friend.id) }) { load() }
                    }
                    textButton(getString(R.string.friends_decline)) {
                        call({ ChurrascoRepository.removeFriend(friend.id) }) { load() }
                    }
                }
            }
        }

        section(getString(R.string.friends_list_title, data.friends.size))
        if (data.friends.isEmpty()) {
            emptyState(R.drawable.ic_users_three, getString(R.string.friends_empty_title), getString(R.string.friends_empty_body))
        } else {
            card(top = 12) {
                data.friends.forEachIndexed { i, friend ->
                    row(context.avatar(friend.displayName, friend.uid), friend.displayName, friend.email, top = if (i == 0) 0 else 12) {
                        confirm(
                            getString(R.string.friends_remove_title, friend.displayName),
                            getString(R.string.friends_remove_body),
                            getString(R.string.friends_remove_action),
                            danger = true,
                        ) { call({ ChurrascoRepository.removeFriend(friend.id) }) { load() } }
                    }
                }
            }
        }

        if (data.outgoing.isNotEmpty()) {
            section(getString(R.string.friends_outgoing_title))
            card(top = 12) {
                data.outgoing.forEachIndexed { i, friend ->
                    row(context.avatar(friend.displayName, friend.uid), friend.displayName, friend.email, top = if (i == 0) 0 else 12)
                }
            }
        }
    }
}
