package com.longynus.churrasco.friends

import android.os.Bundle
import com.google.firebase.auth.FirebaseAuth
import com.longynus.churrasco.AuthSession
import com.longynus.churrasco.R
import com.longynus.churrasco.data.ChurrascoRepository
import com.longynus.churrasco.data.model.ProfileUpdate
import com.longynus.churrasco.ui.ScreenActivity
import com.longynus.churrasco.ui.TextRole
import com.longynus.churrasco.ui.card
import com.longynus.churrasco.ui.primaryButton
import com.longynus.churrasco.ui.text
import com.longynus.churrasco.ui.textField

/** Nome que os amigos veem e chave Pix para receber na divisão das contas. */
class ProfileActivity : ScreenActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupScreen(getString(R.string.profile_title))

        lateinit var name: com.google.android.material.textfield.TextInputEditText
        lateinit var pix: com.google.android.material.textfield.TextInputEditText
        render(bottom = {
            primaryButton(getString(R.string.save)) {
                val update = ProfileUpdate(displayName = name.text.toString().trim(), pixKey = pix.text.toString().trim())
                call({ ChurrascoRepository.updateProfile(update) }) { profile ->
                    AuthSession.saveProfile(this@ProfileActivity, profile)
                    toast(getString(R.string.profile_saved))
                    finish()
                }
            }
        }) {
            text(getString(R.string.profile_title), TextRole.Title)
            text(FirebaseAuth.getInstance().currentUser?.email.orEmpty(), TextRole.Muted, top = 8)
            card(top = 24) {
                text(getString(R.string.profile_name_title), TextRole.Strong)
                text(getString(R.string.profile_name_help), TextRole.Caption, top = 4)
                name = textField(getString(R.string.login_field_label), AuthSession.displayName(this@ProfileActivity), maxLength = 60)
            }
            card(top = 16) {
                text(getString(R.string.profile_pix_title), TextRole.Strong)
                text(getString(R.string.profile_pix_help), TextRole.Caption, top = 4)
                pix = textField(getString(R.string.profile_pix_hint), AuthSession.pixKey(this@ProfileActivity).orEmpty(), maxLength = 77)
            }
        }
    }
}
