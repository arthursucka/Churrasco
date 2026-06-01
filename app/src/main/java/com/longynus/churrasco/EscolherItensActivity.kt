package com.longynus.churrasco

import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.messaging.FirebaseMessaging
import com.longynus.churrasco.model.Churrasco
import com.longynus.churrasco.model.PresencaRequest
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class EscolherItensActivity : AppCompatActivity() {

    private lateinit var rootLayout: View
    private lateinit var checkboxContainer: LinearLayout
    private lateinit var btnConfirmar: Button
    private lateinit var txtAvailableCount: TextView

    private lateinit var churrascoId: String
    private lateinit var userName: String
    private val checkboxes = mutableListOf<CheckBox>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_escolher_itens)
        TopBarHelper.setup(this, getString(R.string.choose_items_title))

        rootLayout = findViewById(R.id.rootLayout)
        checkboxContainer = findViewById(R.id.checkboxContainer)
        btnConfirmar = findViewById(R.id.btnConfirmar)
        txtAvailableCount = findViewById(R.id.txtAvailableCount)

        churrascoId = intent.getStringExtra("churrascoId")
            ?: run {
                finish()
                return
            }

        userName = getSharedPreferences("ChurrascoApp", MODE_PRIVATE)
            .getString("userName", "") ?: ""

        val itensNaoFornecidos = intent
            .getStringArrayListExtra("itensNaoFornecidos")
            .orEmpty()

        if (itensNaoFornecidos.isEmpty()) {
            showNoItemsAndConfirm()
            return
        }

        bindAvailableItems(itensNaoFornecidos)

        btnConfirmar.setOnClickListener {
            val selecionados = selectedItems()

            if (selecionados.isEmpty()) {
                confirmWithoutItems()
            } else {
                validateItemsAndSend(selecionados)
            }
        }
    }

    private fun bindAvailableItems(items: List<String>) {
        val uniqueItems = items.distinct()

        checkboxes.clear()
        checkboxContainer.removeAllViews()
        txtAvailableCount.text = if (uniqueItems.size == 1) {
            "1 item disponivel"
        } else {
            "${uniqueItems.size} itens disponiveis"
        }

        uniqueItems.forEach { label ->
            CheckBox(this).apply {
                text = label
                textSize = 16f
                setTextColor(getColor(R.color.mainInk))
                background = getDrawable(R.drawable.bg_create_chip)
                buttonTintList = ColorStateList.valueOf(getColor(R.color.mainPrimary))
                setPadding(14.dp(), 10.dp(), 14.dp(), 10.dp())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 8.dp()
                }
                checkboxes.add(this)
                checkboxContainer.addView(this)
            }
        }
    }

    private fun selectedItems(): List<String> =
        checkboxes
            .filter { it.isChecked }
            .map { it.text.toString() }
            .distinct()

    private fun validateItemsAndSend(selectedItems: List<String>) {
        rootLayout.showLoading()
        btnConfirmar.isEnabled = false

        RetrofitClient.instance
            .getChurrasco(churrascoId)
            .enqueue(object : Callback<ApiResponse<Churrasco>> {
                override fun onResponse(
                    call: Call<ApiResponse<Churrasco>>,
                    response: Response<ApiResponse<Churrasco>>
                ) {
                    val churrasco = response.body()?.churrasco
                    if (!response.isSuccessful || response.body()?.success != true || churrasco == null) {
                        rootLayout.hideLoading()
                        btnConfirmar.isEnabled = true
                        rootLayout.showErrorDialog("Nao conseguimos atualizar os itens disponiveis.")
                        return
                    }

                    val unavailableItems = churrasco.fornecidosAgregados.toSet()
                    val stillAvailableSelectedItems = selectedItems
                        .filterNot { it in unavailableItems }
                        .distinct()

                    if (selectedItems.isNotEmpty() && stillAvailableSelectedItems.isEmpty()) {
                        rootLayout.hideLoading()
                        btnConfirmar.isEnabled = true
                        refreshAvailableItems(unavailableItems)
                        rootLayout.showErrorDialog("Esses itens ja foram assumidos por outra pessoa. Escolha outro item disponivel.")
                        return
                    }

                    if (stillAvailableSelectedItems.size < selectedItems.size) {
                        rootLayout.showSnackbar("Alguns itens ja foram assumidos e foram removidos da sua selecao.")
                    }

                    enviarPresenca(stillAvailableSelectedItems)
                }

                override fun onFailure(call: Call<ApiResponse<Churrasco>>, t: Throwable) {
                    rootLayout.hideLoading()
                    btnConfirmar.isEnabled = true
                    rootLayout.showConnectionState(
                        "Nao conseguimos atualizar os itens disponiveis agora. Confira sua internet e tente de novo."
                    ) {
                        validateItemsAndSend(selectedItems)
                    }
                }
            })
    }

    private fun refreshAvailableItems(unavailableItems: Set<String>) {
        val availableItems = ItemConstants.listaCompletaDeItens
            .filterNot { it in unavailableItems }

        if (availableItems.isEmpty()) {
            showNoItemsAndConfirm()
        } else {
            bindAvailableItems(availableItems)
        }
    }

    private fun enviarPresenca(selectedItems: List<String>) {
        val request = PresencaRequest(
            name = userName,
            selectedItems = selectedItems
        )

        RetrofitClient.instance
            .confirmPresenca(churrascoId, request)
            .enqueue(object : Callback<ApiResponse<Any>> {
                override fun onResponse(
                    call: Call<ApiResponse<Any>>,
                    response: Response<ApiResponse<Any>>
                ) {
                    rootLayout.hideLoading()
                    btnConfirmar.isEnabled = true

                    if (response.isSuccessful && response.body()?.success == true) {
                        FirebaseMessaging.getInstance()
                            .unsubscribeFromTopic("churrasco_$churrascoId")

                        Toast.makeText(
                            this@EscolherItensActivity,
                            confirmationMessage(selectedItems),
                            Toast.LENGTH_LONG
                        ).show()

                        startActivity(
                            Intent(this@EscolherItensActivity, ActiveChurrascoDetailsActivity::class.java)
                                .putExtra("churrascoId", churrascoId)
                                .apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                        )
                    } else {
                        AlertDialog.Builder(this@EscolherItensActivity)
                            .setTitle("Ops")
                            .setMessage(response.body()?.message ?: "Nao conseguimos confirmar sua presenca agora. Tente novamente.")
                            .setPositiveButton("OK") { _, _ -> finish() }
                            .setCancelable(false)
                            .show()
                    }
                }

                override fun onFailure(call: Call<ApiResponse<Any>>, t: Throwable) {
                    rootLayout.hideLoading()
                    btnConfirmar.isEnabled = true
                    rootLayout.showConnectionState(
                        "Nao conseguimos confirmar sua presenca agora. Confira sua internet e tente de novo."
                    ) {
                        enviarPresenca(selectedItems)
                    }
                }
            })
    }

    private fun confirmWithoutItems() {
        AlertDialog.Builder(this)
            .setTitle("Confirmar sem item?")
            .setMessage("Voce pode confirmar presenca agora e combinar os detalhes pelo chat.")
            .setPositiveButton("Confirmar") { _, _ -> validateItemsAndSend(emptyList()) }
            .setNegativeButton("Voltar", null)
            .show()
    }

    private fun confirmationMessage(selectedItems: List<String>): String {
        if (selectedItems.isEmpty()) return "Convite aceito!"

        val itemsText = selectedItems.joinToString(", ")
        return "Voce escolheu levar: $itemsText"
    }

    private fun showNoItemsAndConfirm() {
        AlertDialog.Builder(this)
            .setTitle("Tudo ja foi combinado")
            .setMessage("Nao ha itens faltando agora. Quer confirmar sua presenca mesmo assim?")
            .setPositiveButton("Confirmar presenca") { _, _ -> enviarPresenca(emptyList()) }
            .setNegativeButton("Agora nao") { _, _ -> finish() }
            .show()
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
