package com.longynus.churrasco

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.longynus.churrasco.ui.TextRole
import com.longynus.churrasco.ui.animationsOff
import com.longynus.churrasco.ui.dp
import com.longynus.churrasco.ui.icon
import com.longynus.churrasco.ui.primaryButton
import com.longynus.churrasco.ui.text
import com.longynus.churrasco.ui.textButton

/**
 * Três telas de apresentação na primeira abertura, antes de entrar. As ilustrações ONB-01 a
 * ONB-03 entram no lugar dos círculos com ícone quando forem geradas.
 */
class OnboardingActivity : AppCompatActivity() {

    private data class Page(@DrawableRes val icon: Int, @StringRes val title: Int, @StringRes val body: Int, @DrawableRes val art: Int? = null)

    private val pages = listOf(
        Page(R.drawable.ic_share_network, R.string.onb_1_title, R.string.onb_1_body),
        Page(R.drawable.ic_list_checks, R.string.onb_2_title, R.string.onb_2_body),
        Page(R.drawable.ic_pix_logo, R.string.onb_3_title, R.string.onb_3_body),
    )
    private var index = 0
    private lateinit var column: LinearLayout

    companion object {
        private const val PREFS = "onboarding"
        private const val SEEN = "seen"
        fun seen(context: Context) = context.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(SEEN, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        index = savedInstanceState?.getInt("index") ?: 0
        column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface))
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        ViewCompat.setOnApplyWindowInsetsListener(column) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(dp(24), dp(24) + bars.top, dp(24), dp(24) + bars.bottom)
            insets
        }
        setContentView(column)
        draw()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("index", index)
    }

    private fun draw(): Unit = column.run {
        removeAllViews()
        val page = pages[index]
        // "Pular" no canto, a ilustração no meio, texto e botão na zona do polegar.
        addView(LinearLayout(context).apply {
            gravity = Gravity.END
            textButton(getString(R.string.onb_skip), top = 0) { finishOnboarding() }.layoutParams =
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val art = FrameLayout(context).apply {
            setBackgroundResource(R.drawable.bg_circle)
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.brasa_soft))
            addView(context.icon(page.icon, R.color.brasa_strong, 96), FrameLayout.LayoutParams(dp(96), dp(96), Gravity.CENTER))
        }
        addView(FrameLayout(context).apply {
            addView(art, FrameLayout.LayoutParams(dp(240), dp(240), Gravity.CENTER))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        text(getString(page.title), TextRole.Display)
        text(getString(page.body), TextRole.Muted, top = 12)
        addView(dots(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(24) })
        val last = index == pages.lastIndex
        primaryButton(getString(if (last) R.string.onb_start else R.string.onb_next), top = 24) {
            if (last) finishOnboarding() else { index++; draw() }
        }
        if (!animationsOff()) {
            art.alpha = 0f; art.translationY = dp(16).toFloat()
            art.animate().alpha(1f).translationY(0f).setDuration(250).start()
        }
    }

    private fun dots(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        contentDescription = getString(R.string.onb_page, index + 1, pages.size)
        pages.indices.forEach { i ->
            addView(View(context).apply {
                setBackgroundResource(R.drawable.bg_pill_sunken)
                backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, if (i == index) R.color.brasa else R.color.line))
            }, LinearLayout.LayoutParams(dp(if (i == index) 24 else 8), dp(8)).apply { if (i > 0) marginStart = dp(8) })
        }
    }

    private fun finishOnboarding() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(SEEN, true).apply()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
