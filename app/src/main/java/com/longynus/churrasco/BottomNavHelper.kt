package com.longynus.churrasco

import android.app.Activity
import android.content.Intent
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView

object BottomNavHelper {
    fun setup(activity: Activity, selectedItemId: Int) {
        // Android 15+ desenha atrás da barra de status: o conteúdo desce a altura dela.
        activity.findViewById<View>(R.id.rootLayout)?.let { root ->
            val baseTop = root.paddingTop
            ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
                val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                view.setPadding(view.paddingLeft, baseTop + top, view.paddingRight, view.paddingBottom)
                insets
            }
            ViewCompat.requestApplyInsets(root)
        }
        val bottomNav = activity.findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav.selectedItemId = selectedItemId

        bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId == selectedItemId) return@setOnItemSelectedListener true

            val target = when (item.itemId) {
                R.id.nav_home -> MainActivity::class.java
                R.id.nav_invites -> PendingInvitesActivity::class.java
                R.id.nav_active -> ActiveChurrascosActivity::class.java
                R.id.nav_history -> PastChurrascosActivity::class.java
                else -> return@setOnItemSelectedListener false
            }

            openMainSection(activity, target, finishCurrent = true)
            true
        }
    }

    fun openMainSection(
        activity: Activity,
        target: Class<out Activity>,
        finishCurrent: Boolean = false
    ) {
        activity.startActivity(Intent(activity, target).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        })
        activity.overridePendingTransition(0, 0)

        if (finishCurrent && activity::class.java != target) {
            activity.finish()
            activity.overridePendingTransition(0, 0)
        }
    }
}
