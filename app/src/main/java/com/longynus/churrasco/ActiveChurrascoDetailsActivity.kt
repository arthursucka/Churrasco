package com.longynus.churrasco

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.longynus.churrasco.adapter.ChatAdapter
import com.longynus.churrasco.model.ChatMessageRequest
import com.longynus.churrasco.model.Churrasco
import com.longynus.churrasco.model.Message
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ActiveChurrascoDetailsActivity : AppCompatActivity() {

    private lateinit var churrascoId: String
    private lateinit var userName: String
    private lateinit var rootLayout: View
    private lateinit var btnCancelChurrasco: Button
    private lateinit var creatorActionsContainer: LinearLayout
    private lateinit var txtUserRole: TextView
    private lateinit var tvStatusDescription: TextView
    private lateinit var statusCard: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContentView(R.layout.activity_active_churrasco_details)
        TopBarHelper.setup(this, getString(R.string.details_event_title))
        BottomNavHelper.setup(this, R.id.nav_active)

        rootLayout = findViewById(R.id.rootLayout)
        creatorActionsContainer = findViewById(R.id.creatorActionsContainer)
        txtUserRole = findViewById(R.id.txtUserRole)
        tvStatusDescription = findViewById(R.id.tvStatusDescription)
        statusCard = findViewById(R.id.statusCard)

        churrascoId = intent.getStringExtra("churrascoId") ?: run {
            finish()
            return
        }

        userName = getSharedPreferences("ChurrascoApp", MODE_PRIVATE)
            .getString("userName", "") ?: ""

        btnCancelChurrasco = findViewById<Button>(R.id.btnCancelChurrasco).apply {
            setOnClickListener { deleteChurrasco() }
        }
        findViewById<Button>(R.id.btnJumpToChat).setOnClickListener {
            scrollToChatSection()
        }

        setupChat()
        setupKeyboardBehavior()
        fetchChurrascoDetails()
    }

    private fun setupChat() {
        val scrollView = findViewById<ScrollView>(R.id.detailsScrollView)
        val rvChat = findViewById<RecyclerView>(R.id.rvChat)
        val txtChatEmpty = findViewById<TextView>(R.id.txtChatEmpty)
        val edtMessage = findViewById<EditText>(R.id.edtMessage)
        val btnSend = findViewById<Button>(R.id.btnSend)
        val chatRef = FirebaseDatabase
            .getInstance()
            .getReference("churrascos/$churrascoId/messages")
        val chatAdapter = ChatAdapter(userName)

        rvChat.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        rvChat.adapter = chatAdapter
        txtChatEmpty.visibility = View.VISIBLE

        edtMessage.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) scrollToChatInput(scrollView)
        }
        edtMessage.setOnClickListener {
            scrollToChatInput(scrollView)
        }

        chatRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                snapshot.getValue(Message::class.java)?.let { msg ->
                    chatAdapter.addMessage(msg)
                    txtChatEmpty.visibility = if (chatAdapter.isEmpty) View.VISIBLE else View.GONE
                    rvChat.post {
                        rvChat.smoothScrollToPosition(chatAdapter.itemCount - 1)
                    }
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}

            override fun onCancelled(error: DatabaseError) {
                rootLayout.showErrorDialog("Nao conseguimos carregar a conversa agora.")
            }
        })

        btnSend.setOnClickListener {
            val text = edtMessage.text.toString().trim()
            if (text.isEmpty()) {
                rootLayout.showSnackbar("Digite uma mensagem antes de enviar.")
                return@setOnClickListener
            }

            sendChatMessage(text, btnSend, edtMessage)
        }
    }

    private fun sendChatMessage(text: String, btnSend: Button, edtMessage: EditText) {
        btnSend.isEnabled = false
        RetrofitClient.instance
            .sendChatMessage(churrascoId, ChatMessageRequest(text))
            .enqueue(object : Callback<ApiResponse<Any>> {
                override fun onResponse(
                    call: Call<ApiResponse<Any>>,
                    response: Response<ApiResponse<Any>>
                ) {
                    btnSend.isEnabled = true
                    if (response.isSuccessful && response.body()?.success == true) {
                        edtMessage.text.clear()
                        rootLayout.showSnackbar("Mensagem enviada.")
                    } else {
                        rootLayout.showErrorDialog(
                            response.body()?.message
                                ?: "Nao conseguimos enviar a mensagem agora. Tente de novo."
                        )
                    }
                }

                override fun onFailure(call: Call<ApiResponse<Any>>, t: Throwable) {
                    btnSend.isEnabled = true
                    rootLayout.showErrorDialog(
                        "Nao conseguimos enviar a mensagem agora. Confira sua internet e tente de novo."
                    )
                }
            })
    }

    private fun setupKeyboardBehavior() {
        val bottomNavigation = findViewById<View>(R.id.bottomNavigation)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val keyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            bottomNavigation.visibility = if (keyboardVisible) View.GONE else View.VISIBLE
            insets
        }
        ViewCompat.requestApplyInsets(rootLayout)
    }

    private fun scrollToChatInput(scrollView: ScrollView) {
        scrollView.postDelayed({
            scrollView.smoothScrollTo(0, scrollView.getChildAt(0).bottom)
        }, 250)
    }

    private fun scrollToChatSection() {
        val scrollView = findViewById<ScrollView>(R.id.detailsScrollView)
        val chatSection = findViewById<View>(R.id.chatSection)
        scrollView.post {
            scrollView.smoothScrollTo(0, chatSection.top)
        }
    }

    private fun deleteChurrasco() {
        rootLayout.showLoading()
        RetrofitClient.instance
            .deleteChurrasco(churrascoId)
            .enqueue(object : Callback<ApiResponse<Any>> {
                override fun onResponse(
                    call: Call<ApiResponse<Any>>,
                    response: Response<ApiResponse<Any>>
                ) {
                    rootLayout.hideLoading()
                    if (response.isSuccessful && response.body()?.success == true) {
                        rootLayout.showSnackbar("Churrasco cancelado.")
                        startActivity(
                            Intent(this@ActiveChurrascoDetailsActivity, ActiveChurrascosActivity::class.java)
                                .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
                        )
                    } else {
                        rootLayout.showErrorDialog(
                            "Nao conseguimos cancelar o churrasco agora. Tente novamente."
                        )
                    }
                }

                override fun onFailure(call: Call<ApiResponse<Any>>, t: Throwable) {
                    rootLayout.hideLoading()
                    rootLayout.showErrorDialog(
                        "Nao conseguimos cancelar o churrasco agora. Confira sua internet e tente de novo."
                    )
                }
            })
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
                        populateDetails(body.churrasco)
                    } else {
                        rootLayout.showErrorDialog(
                            body?.message ?: "Nao conseguimos carregar os detalhes."
                        )
                    }
                }

                override fun onFailure(call: Call<ApiResponse<Churrasco>>, t: Throwable) {
                    rootLayout.hideLoading()
                    rootLayout.showErrorDialog(
                        "Nao conseguimos carregar os detalhes agora. Confira sua internet e tente de novo."
                    )
                }
            })
    }

    private fun populateDetails(churrasco: Churrasco) {
        val isCreator = churrasco.createdBy == userName
        val isConfirmed = churrasco.guestsConfirmed.any { it.name == userName }

        creatorActionsContainer.visibility = if (isCreator) View.VISIBLE else View.GONE
        if (isCreator) {
            statusCard.setBackgroundResource(R.drawable.bg_status_info)
            txtUserRole.text = "Voce e o organizador"
            tvStatusDescription.text = "Acompanhe quem vai, quem leva cada item e converse com o grupo."
        } else if (isConfirmed) {
            statusCard.setBackgroundResource(R.drawable.bg_status_success)
            txtUserRole.text = "Voce confirmou presenca"
            tvStatusDescription.text = "Confira os combinados e use o chat para alinhar detalhes."
        } else {
            statusCard.setBackgroundResource(R.drawable.bg_status_warning)
            txtUserRole.text = "Aguardando resposta"
            tvStatusDescription.text = "Responda ao convite antes de participar da conversa."
        }

        findViewById<TextView>(R.id.txtDetalhesEvento).text = buildString {
            append("Criado por: ${churrasco.createdBy}\n")
            append("Quando: ${ChurrascoDateUtils.eventDateTime(churrasco.churrascoDate, churrasco.hora)}\n")
            append("Onde: ${churrasco.local}")
        }

        bindSimpleList(
            findViewById(R.id.containerProvided),
            buildProvidedByCreatorRows(churrasco),
            "Nenhum item garantido pelo organizador."
        )

        bindSimpleList(
            findViewById(R.id.containerConfirmed),
            churrasco.guestsConfirmed.map { it.name },
            "Ninguem confirmou ainda."
        )

        bindSimpleList(
            findViewById(R.id.containerAssignedItems),
            buildAssignedItemRows(churrasco),
            "Ninguem assumiu itens ainda."
        )

        bindSimpleList(
            findViewById(R.id.containerMissingItems),
            buildMissingItems(churrasco),
            "Tudo certo por enquanto."
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

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
