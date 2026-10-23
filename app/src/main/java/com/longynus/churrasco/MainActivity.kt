package com.longynus.churrasco

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.longynus.churrasco.model.LegacyLinkStatus
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity() {

    private lateinit var txtGreeting: TextView
    private lateinit var btnCreate: Button
    private lateinit var btnActive: View
    private lateinit var btnPast: View
    private lateinit var btnPendingInvites: View
    private lateinit var btnSwitchUser: Button

    private lateinit var userName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!AuthSession.isReady(this)) {
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return
        }

        userName = AuthSession.userName(this)
        setContentView(R.layout.activity_main)

        txtGreeting = findViewById(R.id.txtGreeting)
        btnCreate = findViewById(R.id.btnCreateChurrasco)
        btnActive = findViewById(R.id.btnActiveChurrascos)
        btnPast = findViewById(R.id.btnPastChurrascos)
        btnPendingInvites = findViewById(R.id.btnPendingInvites)
        btnSwitchUser = findViewById(R.id.btnSwitchUser)

        BottomNavHelper.setup(this, R.id.nav_home)

        txtGreeting.text = getString(R.string.main_greeting, userName)

        btnCreate.setOnClickListener {
            startActivity(Intent(this, CreateChurrascoActivity1::class.java))
        }

        btnPendingInvites.setOnClickListener {
            BottomNavHelper.openMainSection(this, PendingInvitesActivity::class.java, finishCurrent = true)
        }

        btnActive.setOnClickListener {
            BottomNavHelper.openMainSection(this, ActiveChurrascosActivity::class.java, finishCurrent = true)
        }

        btnPast.setOnClickListener {
            BottomNavHelper.openMainSection(this, PastChurrascosActivity::class.java, finishCurrent = true)
        }

        btnSwitchUser.setOnClickListener {
            confirmSwitchUser()
        }

        setupLegacyReviewButton()
        requestNotificationPermissionIfNeeded()
        FcmTokenManager.syncCurrentToken(this)
    }

    /** O botão só aparece para o administrador que revisa contas antigas. */
    private fun setupLegacyReviewButton() {
        val reviewButton = findViewById<Button>(R.id.btnReviewLegacyMain)
        reviewButton.setOnClickListener {
            startActivity(Intent(this, LegacyLinkReviewActivity::class.java))
        }
        RetrofitClient.instance.legacyLinkStatus()
            .enqueue(object : Callback<ApiResponse<LegacyLinkStatus>> {
                override fun onResponse(
                    call: Call<ApiResponse<LegacyLinkStatus>>,
                    response: Response<ApiResponse<LegacyLinkStatus>>
                ) {
                    if (isFinishing || isDestroyed) return
                    val canReview = response.body()?.payload?.canReview == true
                    reviewButton.visibility = if (canReview) View.VISIBLE else View.GONE
                }

                override fun onFailure(call: Call<ApiResponse<LegacyLinkStatus>>, t: Throwable) = Unit
            })
    }

    private fun confirmSwitchUser() {
        AlertDialog.Builder(this)
            .setTitle("Sair da conta?")
            .setMessage("Você vai voltar para a tela de entrada e deixa de receber notificações neste celular. Seus churrascos continuam salvos.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Sair") { _, _ ->
                AuthSession.signOut(this) {
                    startActivity(Intent(this, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                }
            }
            .show()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1001
            )
        }
    }
}
