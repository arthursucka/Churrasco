package com.longynus.churrasco

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.messaging.FirebaseMessaging
import com.longynus.churrasco.model.LegacyLinkRequest
import com.longynus.churrasco.model.LegacyLinkRequestInfo
import com.longynus.churrasco.model.LegacyLinkStatus
import com.longynus.churrasco.model.LoginRequest
import com.longynus.churrasco.model.RegisterRequest
import com.longynus.churrasco.model.Usuario
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * Entrada em etapas: e-mail e senha (Firebase Auth) -> e-mail confirmado ->
 * perfil no backend (nome novo ou recuperação de um nome antigo).
 */
class LoginActivity : AppCompatActivity() {

    private enum class Step { CREDENTIALS, VERIFY, PROFILE, WAITING }

    private val auth = FirebaseAuth.getInstance()

    private lateinit var rootLayout: View
    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var groupCredentials: View
    private lateinit var groupVerify: View
    private lateinit var groupProfile: View
    private lateinit var groupWaiting: View
    private lateinit var edtEmail: EditText
    private lateinit var edtPassword: EditText
    private lateinit var edtUserName: EditText
    private lateinit var btnLegacyAccount: Button
    private lateinit var btnUseAnotherAccount: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (AuthSession.isReady(this)) {
            openMain()
            return
        }

        setContentView(R.layout.activity_login)
        KeyboardInsetsHelper.setup(this)
        auth.setLanguageCode("pt-BR")

        rootLayout = findViewById(R.id.rootLayout)
        tvTitle = findViewById(R.id.tvLoginTitle)
        tvSubtitle = findViewById(R.id.tvLoginSubtitle)
        groupCredentials = findViewById(R.id.groupCredentials)
        groupVerify = findViewById(R.id.groupVerify)
        groupProfile = findViewById(R.id.groupProfile)
        groupWaiting = findViewById(R.id.groupWaiting)
        edtEmail = findViewById(R.id.edtEmail)
        edtPassword = findViewById(R.id.edtPassword)
        edtUserName = findViewById(R.id.edtUserName)
        btnLegacyAccount = findViewById(R.id.btnLegacyAccount)
        btnUseAnotherAccount = findViewById(R.id.btnUseAnotherAccount)

        findViewById<Button>(R.id.btnSignIn).setOnClickListener { signIn() }
        findViewById<Button>(R.id.btnCreateAccount).setOnClickListener { createAccount() }
        findViewById<Button>(R.id.btnForgotPassword).setOnClickListener { sendPasswordReset() }
        findViewById<Button>(R.id.btnCheckVerified).setOnClickListener { continueSignedIn() }
        findViewById<Button>(R.id.btnResendVerification).setOnClickListener { resendVerification() }
        findViewById<Button>(R.id.btnSaveProfile).setOnClickListener { saveProfile() }
        findViewById<Button>(R.id.btnRefreshStatus).setOnClickListener { openSession() }
        btnLegacyAccount.setOnClickListener { confirmLegacyRequest() }
        btnUseAnotherAccount.setOnClickListener { useAnotherAccount() }

        // Nome digitado na versão sem login: sugere como nome do perfil.
        getSharedPreferences("ChurrascoApp", MODE_PRIVATE)
            .getString("userName", null)
            ?.takeIf { it.isNotBlank() }
            ?.let { edtUserName.setText(it) }

        if (auth.currentUser != null) continueSignedIn() else showStep(Step.CREDENTIALS)
    }

    private fun showStep(step: Step, subtitle: String? = null) {
        groupCredentials.visibility = if (step == Step.CREDENTIALS) View.VISIBLE else View.GONE
        groupVerify.visibility = if (step == Step.VERIFY) View.VISIBLE else View.GONE
        groupProfile.visibility = if (step == Step.PROFILE) View.VISIBLE else View.GONE
        groupWaiting.visibility = if (step == Step.WAITING) View.VISIBLE else View.GONE
        btnUseAnotherAccount.visibility = if (step == Step.CREDENTIALS) View.GONE else View.VISIBLE

        tvTitle.setText(
            when (step) {
                Step.CREDENTIALS -> R.string.login_credentials_title
                Step.VERIFY -> R.string.login_verify_title
                Step.PROFILE -> R.string.login_title
                Step.WAITING -> R.string.login_waiting_title
            }
        )
        tvSubtitle.text = subtitle ?: getString(
            when (step) {
                Step.CREDENTIALS -> R.string.login_credentials_subtitle
                Step.VERIFY -> R.string.login_verify_subtitle
                Step.PROFILE -> R.string.login_subtitle
                Step.WAITING -> R.string.login_waiting_subtitle
            }
        )
    }

    // Etapa 1: e-mail e senha

    private fun readCredentials(): Pair<String, String>? {
        val email = edtEmail.text.toString().trim()
        val password = edtPassword.text.toString()

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            rootLayout.showErrorDialog("Digite um e-mail válido.")
            return null
        }
        if (password.length < 6) {
            rootLayout.showErrorDialog("A senha precisa ter pelo menos 6 caracteres.")
            return null
        }
        return email to password
    }

    private fun signIn() {
        val (email, password) = readCredentials() ?: return
        rootLayout.showLoading()
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { continueSignedIn() }
            .addOnFailureListener {
                rootLayout.hideLoading()
                rootLayout.showErrorDialog(authErrorMessage(it))
            }
    }

    private fun createAccount() {
        val (email, password) = readCredentials() ?: return
        rootLayout.showLoading()
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                result.user?.sendEmailVerification()
                continueSignedIn()
            }
            .addOnFailureListener {
                rootLayout.hideLoading()
                rootLayout.showErrorDialog(authErrorMessage(it))
            }
    }

    private fun sendPasswordReset() {
        val email = edtEmail.text.toString().trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            rootLayout.showErrorDialog("Digite seu e-mail acima para receber o link de nova senha.")
            return
        }
        rootLayout.showLoading()
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener {
                rootLayout.hideLoading()
                // Mesma resposta com ou sem conta, para não revelar quais e-mails existem.
                AlertDialog.Builder(this)
                    .setTitle("Confira seu e-mail")
                    .setMessage("Se houver uma conta com $email, você vai receber um link para criar uma nova senha.")
                    .setPositiveButton("OK", null)
                    .show()
            }
    }

    // Etapa 2: e-mail confirmado

    private fun continueSignedIn() {
        val user = auth.currentUser ?: return showStep(Step.CREDENTIALS)
        rootLayout.showLoading()
        user.reload()
            .addOnSuccessListener {
                val refreshed = auth.currentUser ?: return@addOnSuccessListener showStep(Step.CREDENTIALS)
                if (refreshed.isEmailVerified) {
                    // Token novo, já com o e-mail marcado como confirmado.
                    refreshed.getIdToken(true).addOnCompleteListener { openSession() }
                } else {
                    rootLayout.hideLoading()
                    showVerifyStep(refreshed)
                }
            }
            .addOnFailureListener {
                rootLayout.hideLoading()
                if (it is FirebaseAuthInvalidUserException) {
                    auth.signOut()
                    showStep(Step.CREDENTIALS)
                    rootLayout.showErrorDialog("Sua sessão expirou. Entre de novo.")
                } else {
                    rootLayout.showConnectionState(authErrorMessage(it)) { continueSignedIn() }
                }
            }
    }

    private fun showVerifyStep(user: FirebaseUser) {
        showStep(Step.VERIFY, getString(R.string.login_verify_subtitle, user.email.orEmpty()))
    }

    private fun resendVerification() {
        val user = auth.currentUser ?: return
        rootLayout.showLoading()
        user.sendEmailVerification()
            .addOnSuccessListener {
                rootLayout.hideLoading()
                rootLayout.showSnackbar("E-mail reenviado para ${user.email}.")
            }
            .addOnFailureListener {
                rootLayout.hideLoading()
                rootLayout.showErrorDialog(authErrorMessage(it))
            }
    }

    // Etapa 3: perfil no backend

    private fun openSession() {
        rootLayout.showLoading()
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            // Sem token de notificação o login continua; ele é sincronizado depois.
            val fcmToken = if (task.isSuccessful) task.result else null
            RetrofitClient.instance.login(LoginRequest(fcmToken = fcmToken))
                .enqueue(object : Callback<ApiResponse<Usuario>> {
                    override fun onResponse(
                        call: Call<ApiResponse<Usuario>>,
                        response: Response<ApiResponse<Usuario>>
                    ) {
                        val profile = response.body()?.payload
                        when {
                            response.isSuccessful && response.body()?.success == true && profile != null -> {
                                val uid = auth.currentUser?.uid ?: return showStep(Step.CREDENTIALS)
                                AuthSession.saveProfile(this@LoginActivity, uid, profile, fcmToken)
                                openMain()
                            }
                            // Conta Firebase sem perfil, ou perfil antigo ainda sendo vinculado.
                            response.code() == 403 -> loadLegacyStatus()
                            response.code() == 401 -> {
                                rootLayout.hideLoading()
                                auth.signOut()
                                showStep(Step.CREDENTIALS)
                                rootLayout.showErrorDialog(response.apiMessage("Sua sessão expirou. Entre de novo."))
                            }
                            else -> {
                                rootLayout.hideLoading()
                                rootLayout.showConnectionState(
                                    response.apiMessage("Não conseguimos abrir sua conta agora. Tente de novo em instantes.")
                                ) { openSession() }
                            }
                        }
                    }

                    override fun onFailure(call: Call<ApiResponse<Usuario>>, t: Throwable) {
                        rootLayout.hideLoading()
                        rootLayout.showConnectionState(
                            "Demorou um pouco para carregar. O servidor pode estar acordando; tente de novo em instantes."
                        ) { openSession() }
                    }
                })
        }
    }

    private fun loadLegacyStatus() {
        RetrofitClient.instance.legacyLinkStatus()
            .enqueue(object : Callback<ApiResponse<LegacyLinkStatus>> {
                override fun onResponse(
                    call: Call<ApiResponse<LegacyLinkStatus>>,
                    response: Response<ApiResponse<LegacyLinkStatus>>
                ) {
                    rootLayout.hideLoading()
                    val status = response.body()?.payload
                    if (!response.isSuccessful || status == null) {
                        // Sem a consulta, ainda dá para criar um perfil novo.
                        showProfileStep(requestsOpen = false, request = null)
                        return
                    }
                    showProfileStep(status.requestsOpen, status.request)
                }

                override fun onFailure(call: Call<ApiResponse<LegacyLinkStatus>>, t: Throwable) {
                    rootLayout.hideLoading()
                    rootLayout.showConnectionState(
                        "Não conseguimos carregar sua conta agora. Confira sua internet e tente de novo."
                    ) { openSession() }
                }
            })
    }

    private fun showProfileStep(requestsOpen: Boolean, request: LegacyLinkRequestInfo?) {
        when (request?.status) {
            "pending", "activating", "approved" -> {
                showStep(Step.WAITING, getString(R.string.login_waiting_subtitle, request.username))
                return
            }
            "rejected" -> {
                showStep(Step.PROFILE)
                rootLayout.showErrorDialog(
                    "O pedido para recuperar \"${request.username}\" não foi aprovado." +
                        (request.reason?.let { "\n\nMotivo: $it" } ?: "") +
                        "\n\nVocê pode escolher outro nome ou falar com o administrador."
                )
            }
            else -> showStep(Step.PROFILE)
        }
        btnLegacyAccount.visibility = if (requestsOpen) View.VISIBLE else View.GONE
    }

    private fun readProfileName(): String? {
        val name = edtUserName.text.toString().trim()
        if (name.isBlank()) {
            rootLayout.showErrorDialog("Digite seu nome para continuar.")
            return null
        }
        return name
    }

    private fun saveProfile() {
        val name = readProfileName() ?: return
        rootLayout.showLoading()
        RetrofitClient.instance.registerUser(RegisterRequest(username = name, displayName = name))
            .enqueue(object : Callback<ApiResponse<Usuario>> {
                override fun onResponse(
                    call: Call<ApiResponse<Usuario>>,
                    response: Response<ApiResponse<Usuario>>
                ) {
                    if (response.isSuccessful && response.body()?.success == true) {
                        openSession()
                        return
                    }
                    rootLayout.hideLoading()
                    rootLayout.showErrorDialog(
                        response.apiMessage("Não conseguimos salvar seu nome agora. Tente novamente.")
                    )
                }

                override fun onFailure(call: Call<ApiResponse<Usuario>>, t: Throwable) {
                    rootLayout.hideLoading()
                    rootLayout.showConnectionState(
                        "Não conseguimos salvar seu nome agora. Confira sua internet e tente de novo."
                    ) { saveProfile() }
                }
            })
    }

    private fun confirmLegacyRequest() {
        val name = readProfileName() ?: return
        AlertDialog.Builder(this)
            .setTitle("Recuperar \"$name\"?")
            .setMessage(
                "O administrador vai conferir se esse nome é mesmo seu antes de ligar ele a esta conta. " +
                    "Depois da aprovação, seus churrascos antigos aparecem aqui."
            )
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Enviar pedido") { _, _ -> requestLegacyLink(name) }
            .show()
    }

    private fun requestLegacyLink(name: String) {
        rootLayout.showLoading()
        RetrofitClient.instance.requestLegacyLink(LegacyLinkRequest(username = name))
            .enqueue(object : Callback<ApiResponse<LegacyLinkRequestInfo>> {
                override fun onResponse(
                    call: Call<ApiResponse<LegacyLinkRequestInfo>>,
                    response: Response<ApiResponse<LegacyLinkRequestInfo>>
                ) {
                    rootLayout.hideLoading()
                    if (response.isSuccessful && response.body()?.success == true) {
                        showStep(Step.WAITING, getString(R.string.login_waiting_subtitle, name))
                    } else {
                        rootLayout.showErrorDialog(
                            response.apiMessage("Não conseguimos enviar o pedido agora. Tente novamente.")
                        )
                    }
                }

                override fun onFailure(call: Call<ApiResponse<LegacyLinkRequestInfo>>, t: Throwable) {
                    rootLayout.hideLoading()
                    rootLayout.showErrorDialog(
                        "Não conseguimos enviar o pedido agora. Confira sua internet e tente de novo."
                    )
                }
            })
    }

    private fun useAnotherAccount() {
        rootLayout.hideConnectionState()
        rootLayout.showLoading()
        AuthSession.signOut(this) {
            rootLayout.hideLoading()
            edtPassword.text.clear()
            showStep(Step.CREDENTIALS)
        }
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun authErrorMessage(error: Exception): String = when (error) {
        is FirebaseNetworkException ->
            "Sem conexão com a internet. Confira e tente de novo."
        is FirebaseTooManyRequestsException ->
            "Muitas tentativas seguidas. Espere alguns minutos e tente de novo."
        is FirebaseAuthWeakPasswordException ->
            "Essa senha é fraca. Use pelo menos 6 caracteres, misturando letras e números."
        is FirebaseAuthUserCollisionException ->
            "Já existe uma conta com esse e-mail. Toque em Entrar ou em Esqueci minha senha."
        is FirebaseAuthInvalidUserException,
        is FirebaseAuthInvalidCredentialsException ->
            "E-mail ou senha incorretos."
        is FirebaseAuthException ->
            if (error.errorCode == "ERROR_OPERATION_NOT_ALLOWED") {
                "O login por e-mail ainda não foi ativado no Firebase deste app."
            } else {
                "Não conseguimos entrar agora. Tente de novo."
            }
        else -> "Não conseguimos entrar agora. Tente de novo."
    }
}
