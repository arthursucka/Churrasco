package com.longynus.churrasco

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.messaging.FirebaseMessaging
import com.longynus.churrasco.model.Churrasco
import com.longynus.churrasco.model.DeclineRequest
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ChurrascoDetailsActivity : AppCompatActivity() {

    private lateinit var rootLayout: View
    private lateinit var tvInfoBasic: TextView
    private lateinit var tvInviteStatus: TextView
    private lateinit var containerProvided: LinearLayout
    private lateinit var containerConfirmed: LinearLayout
    private lateinit var containerDeclined: LinearLayout
    private lateinit var inviteActionsContainer: LinearLayout
    private lateinit var btnAceitar: Button
    private lateinit var btnDeclinar: Button

    private lateinit var churrascoId: String
    private lateinit var userName: String
    private var currentChurrasco: Churrasco? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_churrasco_details)
        TopBarHelper.setup(this, getString(R.string.details_invite_title))

        rootLayout = findViewById(R.id.rootLayout)
        tvInfoBasic = findViewById(R.id.tvInfoBasic)
        tvInviteStatus = findViewById(R.id.tvInviteStatus)
        containerProvided = findViewById(R.id.containerProvided)
        containerConfirmed = findViewById(R.id.containerConfirmed)
        containerDeclined = findViewById(R.id.containerDeclined)
        inviteActionsContainer = findViewById(R.id.inviteActionsContainer)
        btnAceitar = findViewById(R.id.btnAceitar)
        btnDeclinar = findViewById(R.id.btnDeclinar)

        churrascoId = intent.getStringExtra("churrascoId") ?: run {
            finish()
            return
        }

        userName = getSharedPreferences("ChurrascoApp", MODE_PRIVATE)
            .getString("userName", "") ?: ""

        btnAceitar.setOnClickListener { openItemSelection() }
        btnDeclinar.setOnClickListener { declinePresenca() }

        fetchChurrascoDetails()
    }

    private fun fetchChurrascoDetails() {
        rootLayout.showLoading()

        RetrofitClient.instance
            .getChurrasco(churrascoId)
            .enqueue(object : Callback<ApiResponse<Churrasco>> {
                override fun onResponse(
                    call: Call<ApiResponse<Churrasco>>,
                    response: Response<ApiResponse<Churrasco>>
                ) {
                    rootLayout.hideLoading()
                    val body = response.body()
                    if (response.isSuccessful && body?.success == true && body.churrasco != null) {
                        bindDetails(body.churrasco)
                    } else {
                        showErrorAndFinish(body?.message ?: "Não conseguimos carregar os detalhes.")
                    }
                }

                override fun onFailure(call: Call<ApiResponse<Churrasco>>, t: Throwable) {
                    rootLayout.hideLoading()
                    showErrorAndFinish("Não conseguimos carregar os detalhes agora. Confira sua internet e tente de novo.")
                }
            })
    }

    private fun bindDetails(churrasco: Churrasco) {
        currentChurrasco = churrasco

        tvInfoBasic.text = buildString {
            append("Convite de ${churrasco.createdBy}\n")
            append("${ChurrascoDateUtils.eventDateTime(churrasco.churrascoDate, churrasco.hora)}\n")
            append("Local: ${churrasco.local}")
        }

        bindSimpleList(containerProvided, churrasco.fornecidosAgregados, "Nenhum item garantido ainda.")
        bindSimpleList(
            containerConfirmed,
            churrasco.guestsConfirmed.map { "${it.name}: ${it.items.joinToString()}" },
            "Nenhuma presença confirmada ainda."
        )
        bindSimpleList(containerDeclined, churrasco.guestsDeclined, "Nenhuma recusa.")

        val alreadyConfirmed = churrasco.guestsConfirmed.any { it.name == userName }
        val alreadyDeclined = churrasco.guestsDeclined.contains(userName)

        when {
            alreadyConfirmed -> {
                tvInviteStatus.text = "Você já confirmou presença neste churrasco."
                inviteActionsContainer.visibility = View.GONE
            }
            alreadyDeclined -> {
                tvInviteStatus.text = "Você recusou este convite."
                inviteActionsContainer.visibility = View.GONE
            }
            else -> {
                tvInviteStatus.text = "Você foi convidado. Aceite para escolher o que vai levar."
                inviteActionsContainer.visibility = View.VISIBLE
            }
        }
    }

    private fun bindSimpleList(container: LinearLayout, items: List<String>, emptyMessage: String) {
        container.removeAllViews()
        if (items.isEmpty()) {
            container.addView(TextView(this).apply {
                text = emptyMessage
                setTextColor(getColor(R.color.mainMuted))
                setPadding(14.dp(), 10.dp(), 14.dp(), 10.dp())
                textSize = 15f
            })
            return
        }

        items.forEach { item ->
            container.addView(TextView(this).apply {
                text = item
                setTextColor(getColor(R.color.mainInk))
                background = getDrawable(R.drawable.bg_create_chip)
                setPadding(14.dp(), 10.dp(), 14.dp(), 10.dp())
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 8.dp()
                }
            })
        }
    }

    private fun openItemSelection() {
        val churrasco = currentChurrasco ?: return
        val naoFornecidos = ItemConstants.listaCompletaDeItens
            .filter { it !in churrasco.fornecidosAgregados }

        Intent(this, EscolherItensActivity::class.java).also {
            it.putExtra("churrascoId", churrascoId)
            it.putStringArrayListExtra("itensNaoFornecidos", ArrayList(naoFornecidos))
            startActivity(it)
        }
    }

    private fun declinePresenca() {
        rootLayout.showLoading()
        val req = DeclineRequest(name = userName)

        RetrofitClient.instance
            .declinePresenca(churrascoId, req)
            .enqueue(object : Callback<ApiResponse<Any>> {
                override fun onResponse(
                    call: Call<ApiResponse<Any>>,
                    response: Response<ApiResponse<Any>>
                ) {
                    rootLayout.hideLoading()
                    if (response.isSuccessful && response.body()?.success == true) {
                        FirebaseMessaging.getInstance()
                            .unsubscribeFromTopic("churrasco_$churrascoId")
                        rootLayout.showSnackbar("Convite recusado.")
                        finish()
                    } else {
                        rootLayout.showErrorDialog("Não conseguimos recusar o convite agora. Tente novamente.")
                    }
                }

                override fun onFailure(call: Call<ApiResponse<Any>>, t: Throwable) {
                    rootLayout.hideLoading()
                    rootLayout.showErrorDialog("Não conseguimos recusar o convite agora. Confira sua internet e tente de novo.")
                }
            })
    }

    private fun showErrorAndFinish(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Ops")
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
