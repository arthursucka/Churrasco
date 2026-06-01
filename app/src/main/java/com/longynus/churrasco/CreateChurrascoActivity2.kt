package com.longynus.churrasco

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.longynus.churrasco.model.CreateChurrascoRequest
import com.longynus.churrasco.model.CreateChurrascoResponse
import com.longynus.churrasco.model.Usuario
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class CreateChurrascoActivity2 : AppCompatActivity() {

    private lateinit var rootLayout: View
    private lateinit var btnConfirmar: Button
    private lateinit var convidadosContainer: LinearLayout
    private lateinit var checkMarcarTodos: CheckBox
    private lateinit var txtConvidadosStatus: TextView
    private lateinit var txtSelectedGuests: TextView

    private val checkboxes = mutableListOf<CheckBox>()
    private lateinit var userName: String

    private lateinit var churrascoDate: String
    private lateinit var hora: String
    private lateinit var local: String
    private lateinit var fornecidos: ArrayList<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_churrasco2)
        TopBarHelper.setup(this, getString(R.string.create_review_title))

        rootLayout = findViewById(R.id.rootLayout)
        btnConfirmar = findViewById(R.id.btnConfirmarChurrasco)
        convidadosContainer = findViewById(R.id.convidadosContainer)
        checkMarcarTodos = findViewById(R.id.checkMarcarTodos)
        txtConvidadosStatus = findViewById(R.id.txtConvidadosStatus)
        txtSelectedGuests = findViewById(R.id.txtSelectedGuests)

        churrascoDate = intent.getStringExtra("churrascoDate") ?: ""
        hora = intent.getStringExtra("hora") ?: ""
        local = intent.getStringExtra("local") ?: ""
        fornecidos = intent.getStringArrayListExtra("fornecidos") ?: arrayListOf()

        userName = getSharedPreferences("ChurrascoApp", MODE_PRIVATE)
            .getString("userName", "") ?: ""

        bindResumo()
        setupActions()
        fetchUsuariosConvidaveis()
    }

    private fun bindResumo() {
        val txtDetalhes = findViewById<TextView>(R.id.txtDetalhes)
        val fornecidosContainer = findViewById<LinearLayout>(R.id.fornecidosContainer)

        txtDetalhes.text = getString(
            R.string.detalhes_churrasco_fmt,
            ChurrascoDateUtils.normalizeDate(churrascoDate),
            ChurrascoDateUtils.normalizeTime(hora),
            local
        )

        fornecidosContainer.removeAllViews()
        fornecidos.forEach { item ->
            fornecidosContainer.addView(TextView(this).apply {
                text = item
                textSize = 16f
                setTextColor(getColor(R.color.mainInk))
                background = getDrawable(R.drawable.bg_create_chip)
                setPadding(14.dp(), 10.dp(), 14.dp(), 10.dp())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 8.dp()
                }
            })
        }
    }

    private fun setupActions() {
        checkMarcarTodos.setOnCheckedChangeListener { _, isChecked ->
            checkboxes.forEach { it.isChecked = isChecked }
            updateSelectedGuestsState()
        }

        btnConfirmar.setOnClickListener {
            val convidadosSelecionados = selectedGuests()

            if (convidadosSelecionados.isEmpty()) {
                rootLayout.showErrorDialog("Escolha pelo menos um convidado.")
                return@setOnClickListener
            }

            if (churrascoDate.isBlank() || hora.isBlank() || local.isBlank()) {
                rootLayout.showErrorDialog("Faltou preencher alguma informação do churrasco.")
                return@setOnClickListener
            }

            createChurrasco(convidadosSelecionados)
        }
    }

    private fun fetchUsuariosConvidaveis() {
        rootLayout.hideConnectionState()
        rootLayout.showLoading()
        btnConfirmar.isEnabled = false
        checkMarcarTodos.visibility = View.GONE
        txtSelectedGuests.visibility = View.GONE
        txtConvidadosStatus.visibility = View.VISIBLE
        txtConvidadosStatus.text = "Carregando convidados..."
        convidadosContainer.removeAllViews()
        checkboxes.clear()

        RetrofitClient.instance.getAllUsers()
            .enqueue(object : Callback<ApiResponse<List<Usuario>>> {
                override fun onResponse(
                    call: Call<ApiResponse<List<Usuario>>>,
                    response: Response<ApiResponse<List<Usuario>>>
                ) {
                    rootLayout.hideLoading()

                    val body = response.body()
                    if (!response.isSuccessful || body?.success != true) {
                        txtConvidadosStatus.text = body?.message ?: "Não conseguimos carregar os convidados."
                        return
                    }

                    val users = body.payload.orEmpty()
                        .filter { it.username != userName }
                        .sortedBy { it.displayName.lowercase() }

                    bindConvidados(users)
                }

                override fun onFailure(call: Call<ApiResponse<List<Usuario>>>, t: Throwable) {
                    rootLayout.hideLoading()
                    rootLayout.showConnectionState(
                        "Não conseguimos carregar a lista de convidados agora. Confira sua internet e tente de novo."
                    ) {
                        fetchUsuariosConvidaveis()
                    }
                }
            })
    }

    private fun bindConvidados(users: List<Usuario>) {
        checkboxes.clear()
        convidadosContainer.removeAllViews()

        if (users.isEmpty()) {
            checkMarcarTodos.visibility = View.GONE
            txtSelectedGuests.visibility = View.GONE
            btnConfirmar.isEnabled = false
            txtConvidadosStatus.visibility = View.VISIBLE
            txtConvidadosStatus.text = "Ainda não há outras pessoas cadastradas para convidar."
            return
        }

        txtConvidadosStatus.visibility = View.VISIBLE
        txtConvidadosStatus.text = "Escolha quem receberá o convite."
        checkMarcarTodos.visibility = View.VISIBLE
        txtSelectedGuests.visibility = View.VISIBLE

        users.forEach { user ->
            val checkBox = CheckBox(this).apply {
                text = guestLabel(user)
                tag = user.username
                textSize = 16f
                setTextColor(getColor(R.color.mainInk))
                buttonTintList = ColorStateList.valueOf(getColor(R.color.mainPrimary))
                background = getDrawable(R.drawable.bg_create_chip)
                setPadding(14.dp(), 10.dp(), 14.dp(), 10.dp())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 8.dp()
                }
                setOnCheckedChangeListener { _, _ ->
                    syncSelectAllState()
                    updateSelectedGuestsState()
                }
            }
            convidadosContainer.addView(checkBox)
            checkboxes.add(checkBox)
        }

        updateSelectedGuestsState()
    }

    private fun guestLabel(user: Usuario): String =
        if (user.displayName == user.username) {
            user.displayName
        } else {
            "${user.displayName} (${user.username})"
        }

    private fun syncSelectAllState() {
        val allSelected = checkboxes.isNotEmpty() && checkboxes.all { it.isChecked }
        if (checkMarcarTodos.isChecked != allSelected) {
            checkMarcarTodos.setOnCheckedChangeListener(null)
            checkMarcarTodos.isChecked = allSelected
            checkMarcarTodos.setOnCheckedChangeListener { _, isChecked ->
                checkboxes.forEach { it.isChecked = isChecked }
                updateSelectedGuestsState()
            }
        }
    }

    private fun updateSelectedGuestsState() {
        val count = selectedGuests().size
        txtSelectedGuests.text = when (count) {
            0 -> "Nenhum convidado selecionado"
            1 -> "1 convidado selecionado"
            else -> "$count convidados selecionados"
        }
        btnConfirmar.isEnabled = count > 0
    }

    private fun selectedGuests(): List<String> =
        checkboxes
            .filter { it.isChecked }
            .map { it.tag.toString() }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    private fun createChurrasco(invitedUsers: List<String>) {
        rootLayout.showLoading()
        btnConfirmar.isEnabled = false

        val request = CreateChurrascoRequest(
            churrascoDate = ChurrascoDateUtils.normalizeDate(churrascoDate),
            hora = ChurrascoDateUtils.normalizeTime(hora),
            local = local,
            fornecidos = fornecidos,
            userName = userName,
            invitedUsers = invitedUsers
        )

        RetrofitClient.instance.createChurrasco(request)
            .enqueue(object : Callback<CreateChurrascoResponse> {
                override fun onResponse(
                    call: Call<CreateChurrascoResponse>,
                    response: Response<CreateChurrascoResponse>
                ) {
                    rootLayout.hideLoading()
                    btnConfirmar.isEnabled = selectedGuests().isNotEmpty()

                    Log.d("CREATE_CHURRASCO", "Raw response: $response")
                    Log.d("CREATE_CHURRASCO", "Body: ${response.body()}")
                    Log.d("CREATE_CHURRASCO", "Code: ${response.code()}")

                    val resp = response.body()
                    if (response.isSuccessful && resp?.success == true && !resp.id.isNullOrBlank()) {
                        Toast.makeText(
                            this@CreateChurrascoActivity2,
                            "Churrasco criado!",
                            Toast.LENGTH_LONG
                        ).show()
                        val intent = Intent(this@CreateChurrascoActivity2, ActiveChurrascoDetailsActivity::class.java)
                        intent.putExtra("churrascoId", resp.id)
                        intent.putExtra("createdBy", userName)
                        startActivity(intent)
                        finish()
                    } else {
                        rootLayout.showErrorDialog(resp?.message ?: "Não conseguimos criar o churrasco.")
                    }
                }

                override fun onFailure(call: Call<CreateChurrascoResponse>, t: Throwable) {
                    rootLayout.hideLoading()
                    btnConfirmar.isEnabled = selectedGuests().isNotEmpty()
                    rootLayout.showConnectionState(
                        "Não conseguimos criar o churrasco agora. Confira sua internet e tente de novo."
                    ) {
                        createChurrasco(invitedUsers)
                    }
                }
            })
    }
}
