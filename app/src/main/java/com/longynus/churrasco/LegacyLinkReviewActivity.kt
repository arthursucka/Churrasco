package com.longynus.churrasco

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.os.Bundle
import android.text.InputType
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import com.longynus.churrasco.data.ChurrascoRepository
import com.longynus.churrasco.data.model.LegacyLinkRequestInfo
import com.longynus.churrasco.ui.ScreenActivity
import com.longynus.churrasco.ui.TextRole
import com.longynus.churrasco.ui.card
import com.longynus.churrasco.ui.dp
import com.longynus.churrasco.ui.emptyState
import com.longynus.churrasco.ui.primaryButton
import com.longynus.churrasco.ui.text
import com.longynus.churrasco.ui.textButton
import com.longynus.churrasco.ui.textField

/**
 * Tela do administrador para aprovar ou recusar pedidos de recuperação de contas
 * antigas. O backend só responde para o UID revisor configurado.
 */
class LegacyLinkReviewActivity : ScreenActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupScreen(getString(R.string.legacy_review_accounts), onRefresh = ::load)
        load()
    }

    private fun load(): Unit = call({ ChurrascoRepository.legacyReviews() }, retry = true) { rows -> draw(rows) }

    private fun draw(rows: List<LegacyLinkRequestInfo>): Unit = render {
        text(getString(R.string.legacy_review_accounts), TextRole.Title)
        text(getString(R.string.legacy_review_help), TextRole.Muted, top = 8)
        if (rows.isEmpty()) {
            emptyState(R.drawable.ic_check_circle, getString(R.string.legacy_review_empty), getString(R.string.legacy_review_empty_body), top = 24)
        }
        rows.forEach { row ->
            card(top = 16) {
                text(row.username, TextRole.Section)
                text(getString(R.string.legacy_verified_email, row.email), TextRole.Caption, top = 4)
                primaryButton(getString(if (row.status == "activating") R.string.legacy_retry else R.string.legacy_approve)) { review(row, approve = true) }
                if (row.status == "pending") textButton(getString(R.string.legacy_reject), danger = true) { review(row, approve = false) }
            }
        }
    }

    private fun review(row: LegacyLinkRequestInfo, approve: Boolean) {
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), 0) }
        val input = form.textField(
            getString(if (approve) R.string.legacy_evidence_hint else R.string.legacy_reject_hint),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
            maxLength = 500,
        )
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(if (approve) R.string.legacy_approve else R.string.legacy_reject))
            .setMessage("${row.username}\n${row.email}")
            .setView(form)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok, null)
            .create()
        // O botão só fecha o diálogo quando a evidência ou o motivo foi preenchido.
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val reference = input.text.toString().trim()
                if (reference.isBlank()) {
                    input.error = getString(if (approve) R.string.legacy_evidence_required else R.string.legacy_rejection_required)
                    return@setOnClickListener
                }
                dialog.dismiss()
                call({ if (approve) ChurrascoRepository.approveLegacy(row, reference) else ChurrascoRepository.rejectLegacy(row.id, reference) }) { load() }
            }
        }
        dialog.show()
    }
}
