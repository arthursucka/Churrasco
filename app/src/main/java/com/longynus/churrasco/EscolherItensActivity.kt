package com.longynus.churrasco

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
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
            showNoItemsAndFinish()
            return
        }

        bindAvailableItems(itensNaoFornecidos)

        btnConfirmar.setOnClickListener {
            val selecionados = selectedItems()

            if (selecionados.isEmpty()) {
                rootLayout.showSnackbar("Escolha pelo menos um item para levar.")
            } else {
                validateItemsAndSend(selecionados)
            }
        }
    }

    private fun bindAvailableItems(items: List<String>) {
        checkboxes.clear()
        checkboxContainer.removeAllViews()

        items.distinct().forEach { label ->
            CheckBox(this).apply {
                text = label
                textSize = 16f
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
                        rootLayout.showErrorDialog("Não conseguimos atualizar os itens disponíveis.")
                        return
                    }

                    val unavailableItems = churrasco.fornecidosAgregados.toSet()
                    val stillAvailableSelectedItems = selectedItems
                        .filterNot { it in unavailableItems }
                        .distinct()

                    if (stillAvailableSelectedItems.isEmpty()) {
                        rootLayout.hideLoading()
                        btnConfirmar.isEnabled = true
                        refreshAvailableItems(unavailableItems)
                        rootLayout.showErrorDialog("Esses itens já foram assumidos por outra pessoa. Escolha outro item disponível.")
                        return
                    }

                    if (stillAvailableSelectedItems.size < selectedItems.size) {
                        rootLayout.showSnackbar("Alguns itens já foram assumidos e foram removidos da sua seleção.")
                    }

                    enviarPresenca(stillAvailableSelectedItems)
                }

                override fun onFailure(call: Call<ApiResponse<Churrasco>>, t: Throwable) {
                    rootLayout.hideLoading()
                    btnConfirmar.isEnabled = true
                    rootLayout.showConnectionState(
                        "Não conseguimos atualizar os itens disponíveis agora. Confira sua internet e tente de novo."
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
            showNoItemsAndFinish()
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

                        rootLayout.showSnackbar("Presença confirmada!")

                        startActivity(
                            Intent(this@EscolherItensActivity, ActiveChurrascosActivity::class.java)
                                .apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                        )
                    } else {
                        AlertDialog.Builder(this@EscolherItensActivity)
                            .setTitle("Ops")
                            .setMessage("Não conseguimos confirmar sua presença agora. Tente novamente.")
                            .setPositiveButton("OK") { _, _ -> finish() }
                            .setCancelable(false)
                            .show()
                    }
                }

                override fun onFailure(call: Call<ApiResponse<Any>>, t: Throwable) {
                    rootLayout.hideLoading()
                    btnConfirmar.isEnabled = true
                    rootLayout.showConnectionState(
                        "Não conseguimos confirmar sua presença agora. Confira sua internet e tente de novo."
                    ) {
                        enviarPresenca(selectedItems)
                    }
                }
            })
    }

    private fun showNoItemsAndFinish() {
        AlertDialog.Builder(this)
            .setTitle("Ops")
            .setMessage("Não há itens disponíveis para escolher agora.")
            .setPositiveButton("OK") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }
}
