package com.longynus.churrasco

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.messaging.FirebaseMessaging
import com.longynus.churrasco.model.LoginRequest
import com.longynus.churrasco.model.RegisterRequest
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {

    private lateinit var rootLayout: View
    private lateinit var loadingOverlay: View
    private lateinit var edtUserName: EditText
    private lateinit var btnLogin: Button
    private lateinit var tvTitle: TextView
    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        KeyboardInsetsHelper.setup(this)

        rootLayout = findViewById(R.id.rootLayout)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        edtUserName = findViewById(R.id.edtUserName)
        btnLogin = findViewById(R.id.btnLogin)
        tvTitle = findViewById(R.id.tvPromptName)

        prefs = getSharedPreferences("ChurrascoApp", Context.MODE_PRIVATE)
        val existing = prefs.getString("userName", null)
        val registered = prefs.getBoolean("userRegistered", false)

        if (!existing.isNullOrBlank() && registered) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        if (!existing.isNullOrBlank()) {
            edtUserName.setText(existing)
        }

        btnLogin.setOnClickListener { attemptLogin() }
    }

    private fun attemptLogin() {
        val username = edtUserName.text.toString().trim()

        if (username.isBlank()) {
            rootLayout.showErrorDialog("Digite seu nome para continuar.")
            return
        }

        rootLayout.hideConnectionState()
        loadingOverlay.visibility = View.VISIBLE

        val registerRequest = RegisterRequest(username = username, displayName = username)

        RetrofitClient.instance.registerUser(registerRequest)
            .enqueue(object : Callback<ApiResponse<Any>> {
                override fun onResponse(
                    call: Call<ApiResponse<Any>>,
                    response: Response<ApiResponse<Any>>
                ) {
                    if (response.isSuccessful) {
                        loginUser(username)
                    } else {
                        loadingOverlay.visibility = View.GONE
                        rootLayout.showErrorDialog("Não conseguimos salvar seu nome agora. Tente novamente.")
                    }
                }

                override fun onFailure(call: Call<ApiResponse<Any>>, t: Throwable) {
                    loadingOverlay.visibility = View.GONE
                    rootLayout.showConnectionState(
                        "Não conseguimos entrar agora. Confira sua internet e tente de novo em instantes."
                    ) {
                        attemptLogin()
                    }
                }
            })
    }

    private fun loginUser(username: String) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                loadingOverlay.visibility = View.GONE
                rootLayout.showErrorDialog("Não conseguimos preparar as notificações agora. Tente novamente.")
                return@addOnCompleteListener
            }

            val fcmToken = task.result
            val loginRequest = LoginRequest(username = username, fcmToken = fcmToken)

            RetrofitClient.instance.login(loginRequest)
                .enqueue(object : Callback<ApiResponse<Any>> {
                    override fun onResponse(
                        call: Call<ApiResponse<Any>>,
                        response: Response<ApiResponse<Any>>
                    ) {
                        loadingOverlay.visibility = View.GONE

                        Log.d(
                            "LOGIN_DEBUG",
                            "response: ${response.body()?.success}, msg=${response.body()?.message}"
                        )

                        if (response.isSuccessful && response.body()?.success == true) {
                            prefs.edit()
                                .putString("userName", username)
                                .putBoolean("userRegistered", true)
                                .putString("lastFcmToken", fcmToken)
                                .apply()

                            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                            finish()
                        } else {
                            rootLayout.showErrorDialog("Não conseguimos entrar agora. Confira o nome e tente de novo.")
                        }
                    }

                    override fun onFailure(call: Call<ApiResponse<Any>>, t: Throwable) {
                        loadingOverlay.visibility = View.GONE
                        rootLayout.showConnectionState(
                            "Demorou um pouco para carregar. Tente novamente em instantes."
                        ) {
                            attemptLogin()
                        }
                    }
                })
        }
    }
}
