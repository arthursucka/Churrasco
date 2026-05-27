package com.longynus.churrasco

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private lateinit var txtGreeting: TextView
    private lateinit var btnCreate: Button
    private lateinit var btnActive: Button
    private lateinit var btnPast: Button
    private lateinit var btnPendingInvites: Button
    private lateinit var btnSwitchUser: Button

    private lateinit var userName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("ChurrascoApp", MODE_PRIVATE)
        userName = prefs.getString("userName", null) ?: ""
        val registered = prefs.getBoolean("userRegistered", false)
        Log.d("MainActivity", "Recuperado de prefs: $userName")

        if (userName.isBlank() || !registered) {
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        txtGreeting = findViewById(R.id.txtGreeting)
        btnCreate = findViewById(R.id.btnCreateChurrasco)
        btnActive = findViewById(R.id.btnActiveChurrascos)
        btnPast = findViewById(R.id.btnPastChurrascos)
        btnPendingInvites = findViewById(R.id.btnPendingInvites)
        btnSwitchUser = findViewById(R.id.btnSwitchUser)

        txtGreeting.text = getString(R.string.main_greeting, userName)

        btnCreate.setOnClickListener {
            startActivity(Intent(this, CreateChurrascoActivity1::class.java))
        }

        btnPendingInvites.setOnClickListener {
            startActivity(Intent(this, PendingInvitesActivity::class.java))
        }

        btnActive.setOnClickListener {
            startActivity(Intent(this, ActiveChurrascosActivity::class.java))
        }

        btnPast.setOnClickListener {
            startActivity(Intent(this, PastChurrascosActivity::class.java))
        }

        btnSwitchUser.setOnClickListener {
            confirmSwitchUser()
        }

        requestNotificationPermissionIfNeeded()
        FcmTokenManager.syncCurrentToken(this)
    }

    private fun confirmSwitchUser() {
        AlertDialog.Builder(this)
            .setTitle("Trocar usuário?")
            .setMessage("Você vai voltar para a tela de entrada. Seus churrascos continuam salvos.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Trocar") { _, _ ->
                getSharedPreferences("ChurrascoApp", MODE_PRIVATE)
                    .edit()
                    .remove("userName")
                    .remove("userRegistered")
                    .remove("lastFcmToken")
                    .apply()

                startActivity(Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
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
