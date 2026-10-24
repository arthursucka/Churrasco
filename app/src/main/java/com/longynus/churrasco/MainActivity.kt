package com.longynus.churrasco

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.longynus.churrasco.data.ChurrascoRepository
import com.longynus.churrasco.friends.FriendsActivity
import com.longynus.churrasco.friends.ProfileActivity
import com.longynus.churrasco.invites.InviteLinkActivity
import com.longynus.churrasco.reminders.ReminderScheduler
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var rootLayout: View
    private lateinit var txtGreeting: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AuthSession.isReady(this)) {
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        rootLayout = findViewById(R.id.rootLayout)
        txtGreeting = findViewById(R.id.txtGreeting)
        BottomNavHelper.setup(this, R.id.nav_home)

        findViewById<Button>(R.id.btnCreateChurrasco).setOnClickListener {
            startActivity(Intent(this, EventFormActivity::class.java))
        }
        findViewById<View>(R.id.btnPendingInvites).setOnClickListener {
            BottomNavHelper.openMainSection(this, PendingInvitesActivity::class.java, finishCurrent = true)
        }
        findViewById<View>(R.id.btnActiveChurrascos).setOnClickListener {
            BottomNavHelper.openMainSection(this, ActiveChurrascosActivity::class.java, finishCurrent = true)
        }
        findViewById<View>(R.id.btnPastChurrascos).setOnClickListener {
            BottomNavHelper.openMainSection(this, PastChurrascosActivity::class.java, finishCurrent = true)
        }
        findViewById<Button>(R.id.btnFriends).setOnClickListener { startActivity(Intent(this, FriendsActivity::class.java)) }
        findViewById<Button>(R.id.btnProfile).setOnClickListener { startActivity(Intent(this, ProfileActivity::class.java)) }
        findViewById<Button>(R.id.btnSwitchUser).setOnClickListener { confirmSignOut() }

        val reviewButton = findViewById<Button>(R.id.btnReviewLegacyMain)
        reviewButton.setOnClickListener { startActivity(Intent(this, LegacyLinkReviewActivity::class.java)) }

        requestNotificationPermissionIfNeeded()
        FcmTokenManager.syncCurrentToken(this)

        lifecycleScope.launch {
            // Só o administrador que revisa contas antigas vê este botão.
            reviewButton.visibility = if (ChurrascoRepository.legacyStatus().getOrNull()?.canReview == true) View.VISIBLE else View.GONE
            ChurrascoRepository.events("active").onSuccess { ReminderScheduler.sync(this@MainActivity, it) }
        }

        // Link de convite aberto antes de entrar na conta.
        AuthSession.takePendingInvite(this)?.let { token ->
            startActivity(Intent(this, InviteLinkActivity::class.java).putExtra(InviteLinkActivity.EXTRA_TOKEN, token))
        }
    }

    override fun onResume() {
        super.onResume()
        if (::txtGreeting.isInitialized) txtGreeting.text = getString(R.string.main_greeting, AuthSession.displayName(this))
    }

    private fun confirmSignOut() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.signout_title)
            .setMessage(R.string.signout_body)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.signout_action) { _, _ ->
                lifecycleScope.launch {
                    rootLayout.showLoading()
                    AuthSession.signOut(this@MainActivity)
                    startActivity(Intent(this@MainActivity, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                }
            }
            .show()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }
}
