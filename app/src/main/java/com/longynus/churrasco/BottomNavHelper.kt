package com.longynus.churrasco

import android.app.Activity
import android.content.Intent
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.longynus.churrasco.friends.FriendsActivity
import com.longynus.churrasco.friends.ProfileActivity

/** A barra de baixo: Início, Churrascos, Amigos e Perfil. Trocar de destino não empilha telas. */
object BottomNavHelper {
    fun setup(activity: Activity, bottomNav: BottomNavigationView, selectedItemId: Int) {
        bottomNav.selectedItemId = selectedItemId
        bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId == selectedItemId) return@setOnItemSelectedListener true
            val target = when (item.itemId) {
                R.id.nav_home -> MainActivity::class.java
                R.id.nav_events -> EventsActivity::class.java
                R.id.nav_friends -> FriendsActivity::class.java
                R.id.nav_profile -> ProfileActivity::class.java
                else -> return@setOnItemSelectedListener false
            }
            openMainSection(activity, target, finishCurrent = true)
            true
        }
    }

    fun openMainSection(activity: Activity, target: Class<out Activity>, finishCurrent: Boolean = false) {
        activity.startActivity(Intent(activity, target).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        })
        @Suppress("DEPRECATION") activity.overridePendingTransition(0, 0)
        if (finishCurrent && activity::class.java != target) {
            activity.finish()
            @Suppress("DEPRECATION") activity.overridePendingTransition(0, 0)
        }
    }
}
