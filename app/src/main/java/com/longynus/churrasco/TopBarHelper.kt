package com.longynus.churrasco

import android.app.Activity
import android.content.Intent
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar

object TopBarHelper {
    fun setup(activity: Activity, title: String) {
        val toolbar = activity.findViewById<MaterialToolbar>(R.id.topToolbar)
        applyStatusBarInset(toolbar)

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

    private fun applyStatusBarInset(toolbar: MaterialToolbar) {
        val baseHeight = toolbar.layoutParams.height
        val basePaddingTop = toolbar.paddingTop

        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(
                view.paddingLeft,
                basePaddingTop + topInset,
                view.paddingRight,
                view.paddingBottom
            )

            view.layoutParams = (view.layoutParams as ViewGroup.LayoutParams).apply {
                height = baseHeight + topInset
            }

            insets
        }

        ViewCompat.requestApplyInsets(toolbar)
    }
}
