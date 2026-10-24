package com.longynus.churrasco.ui

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.longynus.churrasco.R
import kotlin.random.Random

// O pico do app (manual: Movimento): confirmar presença e fechar as contas ganham uma tela
// de comemoração com confete em brasa, ouro e brasa-soft, um pequeno salto e uma vibração.

/** true quando a pessoa desligou as animações do Android ("Remover animações"). */
fun Context.animationsOff(): Boolean =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** Confete chapado caindo por 1,2 s. Papéis em retângulos e círculos, nas cores da marca. */
class ConfettiView(context: Context) : View(context) {
    private data class Piece(var x: Float, var y: Float, val speed: Float, val drift: Float, val size: Float, val spin: Float, val color: Int, val round: Boolean)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val colors = intArrayOf(R.color.brasa, R.color.ouro, R.color.brasa_soft, R.color.ouro).map { ContextCompat.getColor(context, it) }
    private var pieces = emptyList<Piece>()
    private var progress = 0f
    private val rect = RectF()

    fun burst() {
        val w = width.toFloat().takeIf { it > 0 } ?: resources.displayMetrics.widthPixels.toFloat()
        val d = resources.displayMetrics.density
        pieces = List(70) {
            Piece(Random.nextFloat() * w, -Random.nextFloat() * 200 * d, (500 + Random.nextFloat() * 700) * d,
                (Random.nextFloat() - 0.5f) * 160 * d, (6 + Random.nextFloat() * 6) * d, Random.nextFloat() * 720f,
                colors[it % colors.size], it % 3 == 0)
        }
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1200
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (pieces.isEmpty() || progress >= 1f) return
        val fade = if (progress > 0.75f) (1f - progress) / 0.25f else 1f
        pieces.forEach { p ->
            val y = p.y + p.speed * progress
            val x = p.x + p.drift * progress
            paint.color = p.color
            paint.alpha = (255 * fade).toInt()
            canvas.save()
            canvas.rotate(p.spin * progress, x, y)
            if (p.round) canvas.drawCircle(x, y, p.size / 2, paint)
            else { rect.set(x - p.size / 2, y - p.size / 4, x + p.size / 2, y + p.size / 4); canvas.drawRoundRect(rect, p.size / 8, p.size / 8, paint) }
            canvas.restore()
        }
    }
}

/**
 * Tela de comemoração por cima da atual. [art] é a ilustração (CELEB-01/02); sem ela, um
 * círculo brasa-soft com o ícone de confete. [onDone] roda ao tocar no botão.
 */
fun Activity.celebrate(title: String, body: String, action: String, @DrawableRes art: Int? = null, onDone: () -> Unit) {
    val dialog = Dialog(this, R.style.Theme_Churrasco)
    val still = animationsOff()
    val confetti = ConfettiView(this)
    val column = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(24), dp(48), dp(24), dp(48))
    }
    val picture: View = if (art != null) ImageView(this).apply { setImageResource(art) } else FrameLayout(this).apply {
        setBackgroundResource(R.drawable.bg_circle)
        backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.brasa_soft))
        addView(icon(R.drawable.ic_confetti, R.color.brasa_strong, 72), FrameLayout.LayoutParams(dp(72), dp(72), Gravity.CENTER))
    }
    column.addView(picture, LinearLayout.LayoutParams(dp(160), dp(160)))
    column.text(title, TextRole.Display, top = 32).gravity = Gravity.CENTER
    column.text(body, TextRole.Muted, top = 12).gravity = Gravity.CENTER
    column.primaryButton(action, top = 32) { dialog.dismiss(); onDone() }
    val rootView = FrameLayout(this).apply {
        setBackgroundColor(ContextCompat.getColor(context, R.color.surface))
        addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        if (!still) addView(confetti, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }
    dialog.setContentView(rootView)
    dialog.setCancelable(false)
    dialog.show()
    if (!still) {
        picture.scaleX = 0.9f; picture.scaleY = 0.9f
        picture.animate().scaleX(1f).scaleY(1f).setDuration(400).setInterpolator(OvershootInterpolator(3f)).start()
        rootView.post { confetti.burst() }
    }
    rootView.performHapticFeedback(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
}
