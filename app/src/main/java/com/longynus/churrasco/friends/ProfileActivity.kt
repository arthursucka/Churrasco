package com.longynus.churrasco.friends

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.longynus.churrasco.AuthSession
import com.longynus.churrasco.LoginActivity
import com.longynus.churrasco.R
import com.longynus.churrasco.data.ChurrascoRepository
import com.longynus.churrasco.data.model.ProfileUpdate
import com.longynus.churrasco.showLoading
import com.longynus.churrasco.ui.ScreenActivity
import com.longynus.churrasco.ui.TextRole
import com.longynus.churrasco.ui.avatar
import com.longynus.churrasco.ui.card
import com.longynus.churrasco.ui.dp
import com.longynus.churrasco.ui.primaryButton
import com.longynus.churrasco.ui.text
import com.longynus.churrasco.ui.textButton
import com.longynus.churrasco.ui.textField
import kotlinx.coroutines.launch

/** Perfil: o nome que os amigos veem, a chave Pix para receber nas contas e sair da conta. */
class ProfileActivity : ScreenActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupScreen(getString(R.string.profile_title), navItem = R.id.nav_profile)
        draw()
    }

    private fun draw() {
        lateinit var name: com.google.android.material.textfield.TextInputEditText
        lateinit var pix: com.google.android.material.textfield.TextInputEditText
        val displayName = AuthSession.displayName(this)
        render {
            text(getString(R.string.profile_title), TextRole.Title)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(context.avatar(displayName, AuthSession.uid(this@ProfileActivity), 72), LinearLayout.LayoutParams(context.dp(72), context.dp(72)).apply { marginEnd = context.dp(16) })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    text(displayName, TextRole.Section)
                    text(FirebaseAuth.getInstance().currentUser?.email.orEmpty(), TextRole.Caption, top = 4)
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(24) })
            card(top = 24) {
                text(getString(R.string.profile_name_title), TextRole.Strong)
                text(getString(R.string.profile_name_help), TextRole.Caption, top = 4)
                name = textField(getString(R.string.login_field_label), displayName, maxLength = 60)
            }
            card(top = 16) {
                text(getString(R.string.profile_pix_title), TextRole.Strong)
                text(getString(R.string.profile_pix_help), TextRole.Caption, top = 4)
                pix = textField(getString(R.string.profile_pix_hint), AuthSession.pixKey(this@ProfileActivity).orEmpty(), maxLength = 77, icon = R.drawable.ic_pix_logo)
            }
            primaryButton(getString(R.string.save), top = 24) {
                val update = ProfileUpdate(displayName = name.text.toString().trim(), pixKey = pix.text.toString().trim())
                call({ ChurrascoRepository.updateProfile(update) }) { profile ->
                    AuthSession.saveProfile(this@ProfileActivity, profile)
                    toast(getString(R.string.profile_saved))
                    draw()
                }
            }
            textButton(getString(R.string.trocar_usuario), top = 24, danger = true, icon = R.drawable.ic_sign_out) { confirmSignOut() }.id = R.id.btnSwitchUser
        }
    }

    private fun confirmSignOut() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.signout_title)
            .setMessage(R.string.signout_body)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.signout_action) { _, _ ->
                lifecycleScope.launch {
                    root.showLoading()
                    AuthSession.signOut(this@ProfileActivity)
                    startActivity(Intent(this@ProfileActivity, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                }
            }
            .show()
    }
}
