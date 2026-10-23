package com.longynus.churrasco

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.longynus.churrasco.model.LegacyLinkApproval
import com.longynus.churrasco.model.LegacyLinkRejection
import com.longynus.churrasco.model.LegacyLinkRequestInfo
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * Tela do administrador para aprovar ou recusar pedidos de recuperação de
 * contas antigas. O backend só responde para o UID revisor configurado.
 */
class LegacyLinkReviewActivity : AppCompatActivity() {

    private lateinit var content: LinearLayout
    private lateinit var requests: LinearLayout
    private lateinit var status: TextView
    private var saving = false
    private var reviewerUid: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reviewerUid = FirebaseAuth.getInstance().currentUser?.uid
        if (reviewerUid == null) {
            finish()
            return
        }

        val scroll = ScrollView(this).apply {
            id = R.id.rootLayout
            isFillViewport = true
            setBackgroundColor(getColor(R.color.mainBackground))
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp(), 24.dp(), 24.dp(), 24.dp())
        }
        scroll.addView(content)
        setContentView(scroll)
        KeyboardInsetsHelper.setup(this)

        addText(content, getString(R.string.legacy_review_accounts), 26f)
        addText(content, getString(R.string.legacy_review_help))
        status = addText(content, "")
        addButton(content, getString(R.string.legacy_refresh)) { if (!saving) refresh() }
        requests = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(requests)

        refresh()
    }

    private fun refresh() {
        requests.removeAllViews()
        status.text = getString(R.string.legacy_loading)
        RetrofitClient.instance.legacyLinkReviews().enqueue(
            callback({ rows: List<LegacyLinkRequestInfo> ->
                status.text = if (rows.isEmpty()) getString(R.string.legacy_review_empty) else ""
                rows.forEach { row ->
                    addText(requests, row.username, 22f)
                    addText(requests, getString(R.string.legacy_verified_email, row.email))
                    val approveLabel =
                        if (row.status == "activating") R.string.legacy_retry else R.string.legacy_approve
                    addButton(requests, getString(approveLabel)) { if (!saving) review(row, approve = true) }
                    if (row.status == "pending") {
                        addButton(requests, getString(R.string.legacy_reject)) { if (!saving) review(row, approve = false) }
                    }
                }
            }, { message -> status.text = message })
        )
    }

    private fun review(row: LegacyLinkRequestInfo, approve: Boolean) {
        val input = EditText(this).apply {
            hint = getString(if (approve) R.string.legacy_evidence_hint else R.string.legacy_reject_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(if (approve) R.string.legacy_approve else R.string.legacy_reject))
            .setMessage("${row.username}\n${row.email}" + if (approve) "\n\n" + getString(R.string.legacy_review_help) else "")
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
            .create()

        // O botão só fecha o diálogo quando o motivo/evidência foi preenchido.
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val reference = input.text.toString().trim()
                if (reference.isBlank() || reference.length > 500) {
                    input.error = getString(
                        if (approve) R.string.legacy_evidence_required else R.string.legacy_rejection_required
                    )
                    return@setOnClickListener
                }
                saving = true
                dialog.dismiss()
                val call = if (approve) {
                    RetrofitClient.instance.approveLegacyLink(
                        row.id,
                        LegacyLinkApproval(true, reference, row.username, row.email)
                    )
                } else {
                    RetrofitClient.instance.rejectLegacyLink(row.id, LegacyLinkRejection(reference))
                }
                call.enqueue(callback({ _: LegacyLinkRequestInfo ->
                    saving = false
                    refresh()
                }, { message ->
                    saving = false
                    status.text = message
                }))
            }
        }
        dialog.show()
    }

    /** Ignora respostas que chegam depois de sair da tela ou de trocar de conta. */
    private fun <T> callback(success: (T) -> Unit, failure: (String) -> Unit): Callback<ApiResponse<T>> =
        object : Callback<ApiResponse<T>> {
            private fun stale() = isFinishing || isDestroyed ||
                FirebaseAuth.getInstance().currentUser?.uid != reviewerUid

            override fun onResponse(call: Call<ApiResponse<T>>, response: Response<ApiResponse<T>>) {
                if (stale()) return
                val payload = response.body()?.payload
                if (response.isSuccessful && response.body()?.success == true && payload != null) {
                    success(payload)
                } else {
                    failure(response.apiMessage(getString(R.string.legacy_error)))
                }
            }

            override fun onFailure(call: Call<ApiResponse<T>>, t: Throwable) {
                if (!stale()) failure(getString(R.string.legacy_error))
            }
        }

    private fun addText(parent: LinearLayout, text: String, size: Float = 16f): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(getColor(R.color.mainInk))
            setPadding(0, 12, 0, 12)
            parent.addView(this)
        }

    private fun addButton(parent: LinearLayout, text: String, action: () -> Unit): MaterialButton =
        MaterialButton(this).apply {
            this.text = text
            isAllCaps = false
            parent.addView(this, LinearLayout.LayoutParams(-1, -2))
            setOnClickListener { action() }
        }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
