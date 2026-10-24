package com.longynus.churrasco

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.longynus.churrasco.data.ApiError
import com.longynus.churrasco.data.ChurrascoRepository
import com.longynus.churrasco.data.model.LegacyLinkRequestInfo
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Entrada em etapas: e-mail e senha (ou Google) -> e-mail confirmado -> perfil no
 * backend (nome novo ou recuperação de um nome da versão antiga).
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
    private var legacyRequestsOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (AuthSession.isReady(this)) return openMain()

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

        findViewById<Button>(R.id.btnSignIn).setOnClickListener { signIn(create = false) }
        findViewById<Button>(R.id.btnCreateAccount).setOnClickListener { signIn(create = true) }
        findViewById<Button>(R.id.btnGoogle).setOnClickListener { signInWithGoogle() }
        findViewById<Button>(R.id.btnForgotPassword).setOnClickListener { sendPasswordReset() }
        findViewById<Button>(R.id.btnCheckVerified).setOnClickListener { continueSignedIn() }
        findViewById<Button>(R.id.btnResendVerification).setOnClickListener { resendVerification() }
        findViewById<Button>(R.id.btnSaveProfile).setOnClickListener { saveProfile() }
        findViewById<Button>(R.id.btnRefreshStatus).setOnClickListener { openSession() }
        btnLegacyAccount.setOnClickListener { confirmLegacyRequest() }
        btnUseAnotherAccount.setOnClickListener { useAnotherAccount() }

        // Nome digitado na v1 (sem login): sugere como nome do perfil.
        getSharedPreferences("ChurrascoApp", MODE_PRIVATE).getString("userName", null)
            ?.takeIf { it.isNotBlank() }?.let { edtUserName.setText(it) }

        if (auth.currentUser != null) continueSignedIn() else showStep(Step.CREDENTIALS)
    }

    private fun showStep(step: Step, subtitle: String? = null) {
        groupCredentials.visibility = if (step == Step.CREDENTIALS) View.VISIBLE else View.GONE
        groupVerify.visibility = if (step == Step.VERIFY) View.VISIBLE else View.GONE
        groupProfile.visibility = if (step == Step.PROFILE) View.VISIBLE else View.GONE
        groupWaiting.visibility = if (step == Step.WAITING) View.VISIBLE else View.GONE
        btnUseAnotherAccount.visibility = if (step == Step.CREDENTIALS) View.GONE else View.VISIBLE
        btnLegacyAccount.visibility = if (step == Step.PROFILE && legacyRequestsOpen) View.VISIBLE else View.GONE
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
                Step.PROFILE -> R.string.login_subtitle
                else -> R.string.login_credentials_subtitle
            }
        )
    }

    /** Roda [block] com o carregando na tela; erros viram mensagens claras. */
    private fun busy(block: suspend () -> Unit) {
        lifecycleScope.launch {
            rootLayout.showLoading()
            try {
                block()
            } catch (_: AuthSession.CancelledException) {
                // A pessoa fechou a escolha de conta Google.
            } catch (e: ApiError) {
                showMessage(e.serverMessage ?: getString(if (e.isNetwork) R.string.error_server_waking else R.string.error_generic))
            } catch (e: Exception) {
                showMessage(authErrorMessage(e))
            } finally {
                rootLayout.hideLoading()
            }
        }
    }

    private fun showMessage(message: String) = rootLayout.showErrorDialog(message)

    // Etapa 1: e-mail e senha, ou Google

    private fun signIn(create: Boolean) {
        val email = edtEmail.text.toString().trim()
        val password = edtPassword.text.toString()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) return showMessage(getString(R.string.login_invalid_email))
        if (password.length < 6) return showMessage(getString(R.string.login_short_password))
        busy {
            if (create) {
                auth.createUserWithEmailAndPassword(email, password).await().user?.sendEmailVerification()
            } else {
                auth.signInWithEmailAndPassword(email, password).await()
            }
            continueSignedInNow()
        }
    }

    private fun signInWithGoogle() = busy {
        AuthSession.signInWithGoogle(this).getOrThrow()
        continueSignedInNow()
    }

    private fun sendPasswordReset() {
        val email = edtEmail.text.toString().trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) return showMessage(getString(R.string.login_reset_needs_email))
        busy {
            runCatching { auth.sendPasswordResetEmail(email).await() }
            // Mesma resposta com ou sem conta, para não revelar quais e-mails existem.
            MaterialAlertDialogBuilder(this).setTitle(R.string.login_reset_title)
                .setMessage(getString(R.string.login_reset_body, email))
                .setPositiveButton(R.string.ok, null).show()
        }
    }

    // Etapa 2: e-mail confirmado

    private fun continueSignedIn() = busy { continueSignedInNow() }

    private suspend fun continueSignedInNow() {
        val user = auth.currentUser ?: return showStep(Step.CREDENTIALS)
        try {
            user.reload().await()
        } catch (e: FirebaseAuthInvalidUserException) {
            auth.signOut()
            showStep(Step.CREDENTIALS)
            return showMessage(getString(R.string.login_session_expired))
        }
        if (!AuthSession.emailConfirmed()) {
            return showStep(Step.VERIFY, getString(R.string.login_verify_subtitle, user.email.orEmpty()))
        }
        // Token novo, já com o e-mail marcado como confirmado.
        user.getIdToken(true).await()
        openSessionNow()
    }

    private fun resendVerification() = busy {
        val user = auth.currentUser ?: return@busy
        user.sendEmailVerification().await()
        rootLayout.showSnackbar(getString(R.string.login_verify_resent, user.email.orEmpty()))
    }

    // Etapa 3: perfil no backend

    private fun openSession() = busy { openSessionNow() }

    private suspend fun openSessionNow() {
        val token = AuthSession.currentFcmToken()
        ChurrascoRepository.login(token)
            .onSuccess { profile ->
                AuthSession.saveProfile(this, profile, token)
                return openMain()
            }
            .onFailure { error ->
                val api = error as? ApiError
                when {
                    api?.status == 403 -> return loadLegacyStatus()
                    api?.status == 401 -> {
                        auth.signOut()
                        showStep(Step.CREDENTIALS)
                        return showMessage(api.serverMessage ?: getString(R.string.login_session_expired))
                    }
                    else -> throw error
                }
            }
    }

    private suspend fun loadLegacyStatus() {
        val status = ChurrascoRepository.legacyStatus().getOrNull()
        legacyRequestsOpen = status?.requestsOpen == true
        showProfileStep(status?.request)
    }

    private fun showProfileStep(request: LegacyLinkRequestInfo?) {
        when (request?.status) {
            "pending", "activating", "approved" ->
                showStep(Step.WAITING, getString(R.string.login_waiting_subtitle, request.username))
            "rejected" -> {
                showStep(Step.PROFILE)
                showMessage(getString(R.string.login_legacy_rejected, request.username, request.reason ?: "-"))
            }
            else -> showStep(Step.PROFILE)
        }
    }

    private fun readProfileName(): String? =
        edtUserName.text.toString().trim().takeIf { it.isNotBlank() } ?: run {
            showMessage(getString(R.string.login_name_required)); null
        }

    private fun saveProfile() {
        val name = readProfileName() ?: return
        busy {
            ChurrascoRepository.register(name)
                .onSuccess { openSessionNow() }
                .onFailure { error ->
                    val api = error as? ApiError
                    if (api?.code == "LEGACY_NAME") {
                        legacyRequestsOpen = ChurrascoRepository.legacyStatus().getOrNull()?.requestsOpen == true
                        showStep(Step.PROFILE)
                    }
                    throw error
                }
        }
    }

    private fun confirmLegacyRequest() {
        val name = readProfileName() ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.login_legacy_confirm_title, name))
            .setMessage(R.string.login_legacy_confirm_body)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.login_legacy_send) { _, _ ->
                busy {
                    ChurrascoRepository.requestLegacy(name).getOrThrow()
                    showStep(Step.WAITING, getString(R.string.login_waiting_subtitle, name))
                }
            }
            .show()
    }

    private fun useAnotherAccount() = busy {
        rootLayout.hideConnectionState()
        AuthSession.signOut(this)
        edtPassword.text.clear()
        showStep(Step.CREDENTIALS)
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun authErrorMessage(error: Exception): String = getString(
        when (error) {
            is FirebaseNetworkException -> R.string.error_network
            is FirebaseTooManyRequestsException -> R.string.login_too_many
            is FirebaseAuthWeakPasswordException -> R.string.login_weak_password
            is FirebaseAuthUserCollisionException -> R.string.login_email_in_use
            is FirebaseAuthInvalidUserException, is FirebaseAuthInvalidCredentialsException -> R.string.login_wrong_password
            is FirebaseAuthException -> if (error.errorCode == "ERROR_OPERATION_NOT_ALLOWED") R.string.login_provider_disabled else R.string.login_generic
            is IllegalStateException -> R.string.login_google_not_configured
            else -> R.string.login_generic
        }
    )
}
