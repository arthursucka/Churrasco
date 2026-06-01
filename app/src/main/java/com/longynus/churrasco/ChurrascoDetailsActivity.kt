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
    private lateinit var tvStatusDescription: TextView
    private lateinit var statusCard: LinearLayout
    private lateinit var containerProvided: LinearLayout
    private lateinit var containerConfirmed: LinearLayout
    private lateinit var containerAssignedItems: LinearLayout
    private lateinit var containerMissingItems: LinearLayout
    private lateinit var containerDeclined: LinearLayout
    private lateinit var inviteActionsContainer: LinearLayout
    private lateinit var tvActionsTitle: TextView
    private lateinit var btnAceitar: Button
    private lateinit var btnDeclinar: Button
    private lateinit var btnOpenChat: Button

    private lateinit var churrascoId: String
    private lateinit var userName: String
    private var currentChurrasco: Churrasco? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_churrasco_details)
        TopBarHelper.setup(this, getString(R.string.details_invite_title))
        BottomNavHelper.setup(this, R.id.nav_invites)

        rootLayout = findViewById(R.id.rootLayout)
        tvInfoBasic = findViewById(R.id.tvInfoBasic)
        tvInviteStatus = findViewById(R.id.tvInviteStatus)
        tvStatusDescription = findViewById(R.id.tvStatusDescription)
        statusCard = findViewById(R.id.statusCard)
        containerProvided = findViewById(R.id.containerProvided)
        containerConfirmed = findViewById(R.id.containerConfirmed)
        containerAssignedItems = findViewById(R.id.containerAssignedItems)
        containerMissingItems = findViewById(R.id.containerMissingItems)
        containerDeclined = findViewById(R.id.containerDeclined)
        inviteActionsContainer = findViewById(R.id.inviteActionsContainer)
        tvActionsTitle = findViewById(R.id.tvActionsTitle)
        btnAceitar = findViewById(R.id.btnAceitar)
        btnDeclinar = findViewById(R.id.btnDeclinar)
        btnOpenChat = findViewById(R.id.btnOpenChat)

        churrascoId = intent.getStringExtra("churrascoId") ?: run {
            finish()
            return
        }

        userName = getSharedPreferences("ChurrascoApp", MODE_PRIVATE)
            .getString("userName", "") ?: ""

        btnAceitar.setOnClickListener { openItemSelection() }
        btnDeclinar.setOnClickListener { declinePresenca() }
        btnOpenChat.setOnClickListener { openActiveDetails() }

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
                        showErrorAndFinish(body?.message ?: "Nao conseguimos carregar os detalhes.")
                    }
                }

                override fun onFailure(call: Call<ApiResponse<Churrasco>>, t: Throwable) {
                    rootLayout.hideLoading()
                    showErrorAndFinish("Nao conseguimos carregar os detalhes agora. Confira sua internet e tente de novo.")
                }
            })
    }

    private fun bindDetails(churrasco: Churrasco) {
        currentChurrasco = churrasco

        tvInfoBasic.text = buildString {
            append("Criado por: ${churrasco.createdBy}\n")
            append("Quando: ${ChurrascoDateUtils.eventDateTime(churrasco.churrascoDate, churrasco.hora)}\n")
            append("Onde: ${churrasco.local}")
        }

        bindSimpleList(
            containerProvided,
            buildProvidedByCreatorRows(churrasco),
            "Nenhum item garantido pelo organizador."
        )
        bindSimpleList(
            containerConfirmed,
            churrasco.guestsConfirmed.map { it.name },
            "Ninguem confirmou ainda."
        )
        bindSimpleList(
            containerAssignedItems,
            buildAssignedItemRows(churrasco),
            "Ninguem assumiu itens ainda."
        )
        bindSimpleList(
            containerMissingItems,
            buildMissingItems(churrasco),
            "Tudo certo por enquanto."
        )
        bindSimpleList(containerDeclined, churrasco.guestsDeclined, "Nenhuma recusa.")

        val isCreator = churrasco.createdBy == userName
        val alreadyConfirmed = churrasco.guestsConfirmed.any { it.name == userName }
        val alreadyDeclined = churrasco.guestsDeclined.contains(userName)

        when {
            isCreator -> {
                statusCard.setBackgroundResource(R.drawable.bg_status_info)
                tvInviteStatus.text = "Voce e o organizador"
                tvStatusDescription.text = "Acompanhe quem confirmou, o que cada pessoa leva e converse com o grupo."
                showChatAction()
            }

            alreadyConfirmed -> {
                statusCard.setBackgroundResource(R.drawable.bg_status_success)
                tvInviteStatus.text = "Voce confirmou presenca"
                tvStatusDescription.text = "Agora voce pode acompanhar os itens e conversar com o grupo."
                showChatAction()
            }

            alreadyDeclined -> {
                statusCard.setBackgroundResource(R.drawable.bg_status_error)
                tvInviteStatus.text = "Voce recusou este convite"
                tvStatusDescription.text = "Este churrasco fica aqui apenas para consulta."
                inviteActionsContainer.visibility = View.GONE
            }

            else -> {
                statusCard.setBackgroundResource(R.drawable.bg_status_warning)
                tvInviteStatus.text = "Aguardando resposta"
                tvStatusDescription.text = "Aceite para escolher o que vai levar ou recuse se nao puder ir."
                showInviteActions()
            }
        }
    }

    private fun showInviteActions() {
        inviteActionsContainer.visibility = View.VISIBLE
        tvActionsTitle.text = "Sua resposta"
        btnOpenChat.visibility = View.GONE
        btnAceitar.visibility = View.VISIBLE
        btnDeclinar.visibility = View.VISIBLE
    }

    private fun showChatAction() {
        inviteActionsContainer.visibility = View.VISIBLE
        tvActionsTitle.text = "Conversa"
        btnOpenChat.visibility = View.VISIBLE
        btnAceitar.visibility = View.GONE
        btnDeclinar.visibility = View.GONE
    }

    private fun openActiveDetails() {
        startActivity(
            Intent(this, ActiveChurrascoDetailsActivity::class.java)
                .putExtra("churrascoId", churrascoId)
        )
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

    private fun buildAssignedItemRows(churrasco: Churrasco): List<String> {
        return churrasco.guestsConfirmed.flatMap { guest ->
            guest.items.map { item -> "$item - ${guest.name}" }
        }
    }

    private fun buildProvidedByCreatorRows(churrasco: Churrasco): List<String> {
        val assignedItems = churrasco.guestsConfirmed
            .flatMap { it.items }
            .toSet()
        return churrasco.fornecidosAgregados.filter { it !in assignedItems }
    }

    private fun buildMissingItems(churrasco: Churrasco): List<String> {
        val provided = churrasco.fornecidosAgregados.toSet()
        return ItemConstants.listaCompletaDeItens.filter { it !in provided }
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
                        fetchChurrascoDetails()
                    } else {
                        rootLayout.showErrorDialog("Nao conseguimos recusar o convite agora. Tente novamente.")
                    }
                }

                override fun onFailure(call: Call<ApiResponse<Any>>, t: Throwable) {
                    rootLayout.hideLoading()
                    rootLayout.showErrorDialog("Nao conseguimos recusar o convite agora. Confira sua internet e tente de novo.")
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
