package com.longynus.churrasco

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.longynus.churrasco.adapter.ChatAdapter
import com.longynus.churrasco.model.Churrasco
import com.longynus.churrasco.model.Message

class ActiveChurrascoDetailsActivity : AppCompatActivity() {

    private lateinit var churrascoId: String
    private lateinit var userName: String
    private lateinit var rootLayout: View
    private lateinit var btnCancelChurrasco: Button
    private lateinit var creatorActionsContainer: LinearLayout
    private lateinit var txtUserRole: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_active_churrasco_details)

        rootLayout = findViewById(R.id.rootLayout)
        creatorActionsContainer = findViewById(R.id.creatorActionsContainer)
        txtUserRole = findViewById(R.id.txtUserRole)

        churrascoId = intent.getStringExtra("churrascoId") ?: run {
            finish()
            return
        }

        userName = getSharedPreferences("ChurrascoApp", MODE_PRIVATE)
            .getString("userName", "") ?: ""

        btnCancelChurrasco = findViewById<Button>(R.id.btnCancelChurrasco).apply {
            setOnClickListener { deleteChurrasco() }
        }

        setupChat()
        fetchChurrascoDetails()
    }

    private fun setupChat() {
        val rvChat = findViewById<RecyclerView>(R.id.rvChat)
        val edtMessage = findViewById<EditText>(R.id.edtMessage)
        val btnSend = findViewById<Button>(R.id.btnSend)
        val chatRef = FirebaseDatabase
            .getInstance()
            .getReference("churrascos/$churrascoId/messages")
        val chatAdapter = ChatAdapter()

        rvChat.layoutManager = LinearLayoutManager(this)
        rvChat.adapter = chatAdapter

        chatRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                snapshot.getValue(Message::class.java)?.let { msg ->
                    chatAdapter.addMessage(msg)
                    rvChat.scrollToPosition(chatAdapter.itemCount - 1)
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}

            override fun onCancelled(error: DatabaseError) {
                rootLayout.showErrorDialog("Não conseguimos carregar a conversa agora.")
            }
        })

        btnSend.setOnClickListener {
            val text = edtMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                chatRef.push().setValue(Message(userName, text))
                edtMessage.text.clear()
            } else {
                rootLayout.showSnackbar("Digite uma mensagem antes de enviar.")
            }
        }
    }

    private fun deleteChurrasco() {
        rootLayout.showLoading()
        RetrofitClient.instance
            .deleteChurrasco(churrascoId)
            .enqueue(object : retrofit2.Callback<ApiResponse<Any>> {
                override fun onResponse(
                    call: retrofit2.Call<ApiResponse<Any>>,
                    response: retrofit2.Response<ApiResponse<Any>>
                ) {
                    rootLayout.hideLoading()
                    if (response.isSuccessful && response.body()?.success == true) {
                        rootLayout.showSnackbar("Churrasco cancelado.")
                        startActivity(
                            Intent(this@ActiveChurrascoDetailsActivity, ActiveChurrascosActivity::class.java)
                                .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
                        )
                    } else {
                        rootLayout.showErrorDialog("Não conseguimos cancelar o churrasco agora. Tente novamente.")
                    }
                }

                override fun onFailure(call: retrofit2.Call<ApiResponse<Any>>, t: Throwable) {
                    rootLayout.hideLoading()
                    rootLayout.showErrorDialog("Não conseguimos cancelar o churrasco agora. Confira sua internet e tente de novo.")
                }
            })
    }

    private fun fetchChurrascoDetails() {
        rootLayout.showLoading()
        RetrofitClient.instance
            .getChurrasco(churrascoId)
            .enqueue(object : retrofit2.Callback<ApiResponse<Churrasco>> {
                override fun onResponse(
                    call: retrofit2.Call<ApiResponse<Churrasco>>,
                    response: retrofit2.Response<ApiResponse<Churrasco>>
                ) {
                    rootLayout.hideLoading()
                    val body = response.body()
                    if (response.isSuccessful && body?.success == true && body.churrasco != null) {
                        populateDetails(body.churrasco)
                    } else {
                        rootLayout.showErrorDialog(body?.message ?: "Não conseguimos carregar os detalhes.")
                    }
                }

                override fun onFailure(call: retrofit2.Call<ApiResponse<Churrasco>>, t: Throwable) {
                    rootLayout.hideLoading()
                    rootLayout.showErrorDialog("Não conseguimos carregar os detalhes agora. Confira sua internet e tente de novo.")
                }
            })
    }

    private fun populateDetails(churrasco: Churrasco) {
        val isCreator = churrasco.createdBy == userName

        creatorActionsContainer.visibility = if (isCreator) View.VISIBLE else View.GONE
        txtUserRole.text = if (isCreator) {
            "Você é o organizador deste churrasco."
        } else {
            "Você está acompanhando este churrasco como participante."
        }

        findViewById<TextView>(R.id.txtDetalhesEvento).text = getString(
            R.string.evento_data_hora_local,
            churrasco.createdBy,
            ChurrascoDateUtils.normalizeDate(churrasco.churrascoDate),
            ChurrascoDateUtils.normalizeTime(churrasco.hora),
            churrasco.local
        )

        bindSimpleList(
            findViewById(R.id.fornecidosContainer),
            churrasco.fornecidosAgregados,
            "Nenhum item garantido ainda."
        )

        bindSimpleList(
            findViewById(R.id.containerConfirmed),
            churrasco.guestsConfirmed.map { "${it.name}: ${it.items.joinToString()}" },
            "Nenhuma confirmacao ainda."
        )

        bindSimpleList(
            findViewById(R.id.containerDeclined),
            churrasco.guestsDeclined,
            "Nenhuma recusa."
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

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
