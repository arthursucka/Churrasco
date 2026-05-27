package com.longynus.churrasco

import android.app.Activity
import android.content.Intent
import com.google.android.material.appbar.MaterialToolbar

object TopBarHelper {
    fun setup(activity: Activity, title: String) {
        val toolbar = activity.findViewById<MaterialToolbar>(R.id.topToolbar)
        toolbar.title = title
        toolbar.setNavigationOnClickListener {
            activity.finish()
        }
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId != R.id.action_home) return@setOnMenuItemClickListener false

            activity.startActivity(Intent(activity, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK
            })
            true
        }
    }
}
