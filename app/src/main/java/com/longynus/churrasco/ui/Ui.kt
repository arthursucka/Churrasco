package com.longynus.churrasco.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.longynus.churrasco.R

// Kit de componentes das telas montadas em código. Segue a escala de 8 pt e no
// máximo 4 tamanhos de texto (28 título, 20 seção, 16 corpo, 13 legenda).

fun Context.dp(value: Int): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

private fun Context.color(id: Int) = ContextCompat.getColor(this, id)

private fun matchWidth(top: Int = 0, bottom: Int = 0) =
    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        topMargin = top; bottomMargin = bottom
    }

enum class TextRole(val sizeSp: Float, val bold: Boolean, val muted: Boolean) {
    Title(28f, true, false), Section(20f, true, false), Body(16f, false, false),
    Muted(16f, false, true), Caption(13f, false, true), Strong(16f, true, false),
}

fun ViewGroup.text(value: CharSequence, role: TextRole = TextRole.Body, top: Int = 0): TextView =
    TextView(context).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, role.sizeSp)
        setTextColor(context.color(if (role.muted) R.color.mainMuted else R.color.mainInk))
        if (role.bold) setTypeface(typeface, Typeface.BOLD)
        setLineSpacing(context.dp(2).toFloat(), 1f)
        layoutParams = matchWidth(top = context.dp(top))
    }.also(::addView)

/** Cartão branco com borda suave, padding 24. Conteúdo vai em [block]. */
fun ViewGroup.card(top: Int = 16, @DrawableRes background: Int = R.drawable.bg_create_section, block: LinearLayout.() -> Unit = {}): LinearLayout =
    LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(background)
        val pad = context.dp(20)
        setPadding(pad, pad, pad, pad)
        layoutParams = matchWidth(top = context.dp(top))
        block()
    }.also(::addView)

fun ViewGroup.section(title: String, subtitle: String? = null, top: Int = 32): LinearLayout =
    LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = matchWidth(top = context.dp(top))
        text(title, TextRole.Section)
        subtitle?.let { text(it, TextRole.Caption, top = 4) }
    }.also(::addView)

enum class Tone(@DrawableRes val background: Int) {
    Info(R.drawable.bg_status_info), Success(R.drawable.bg_status_success),
    Warning(R.drawable.bg_status_warning), Error(R.drawable.bg_status_error),
}

fun ViewGroup.banner(title: String, body: String?, tone: Tone, top: Int = 0): LinearLayout =
    card(top = top, background = tone.background) {
        text(title, TextRole.Strong)
        body?.let { text(it, TextRole.Body, top = 4) }
    }

private fun ViewGroup.button(label: String, style: Int, top: Int, onClick: () -> Unit): MaterialButton =
    MaterialButton(context, null, style).apply {
        text = label
        isAllCaps = false
        minHeight = context.dp(56)
        layoutParams = matchWidth(top = context.dp(top))
        setOnClickListener { onClick() }
    }.also(::addView)

fun ViewGroup.primaryButton(label: String, top: Int = 16, onClick: () -> Unit) =
    button(label, com.google.android.material.R.attr.materialButtonStyle, top, onClick).apply {
        backgroundTintList = ColorStateList.valueOf(context.color(R.color.mainPrimary))
        setTextColor(context.color(R.color.md_on_primary))
        cornerRadius = context.dp(16)
        setTypeface(typeface, Typeface.BOLD)
    }

fun ViewGroup.secondaryButton(label: String, top: Int = 8, onClick: () -> Unit) =
    button(label, com.google.android.material.R.attr.materialButtonOutlinedStyle, top, onClick).apply {
        cornerRadius = context.dp(16)
        strokeColor = ColorStateList.valueOf(context.color(R.color.mainStroke))
        setTextColor(context.color(R.color.mainInk))
    }

fun ViewGroup.textButton(label: String, top: Int = 4, danger: Boolean = false, onClick: () -> Unit) =
    button(label, com.google.android.material.R.attr.borderlessButtonStyle, top, onClick).apply {
        setTextColor(context.color(if (danger) R.color.mainDanger else R.color.mainPrimary))
        minHeight = context.dp(48)
    }

/** Dois botões lado a lado (ex.: "Abrir no Maps" | "Abrir no Waze"). */
fun ViewGroup.buttonRow(top: Int = 8, block: LinearLayout.() -> Unit): LinearLayout =
    LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = matchWidth(top = context.dp(top))
        block()
        for (i in 0 until childCount) {
            getChildAt(i).layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (i > 0) marginStart = context.dp(8)
            }
        }
    }.also(::addView)

fun ViewGroup.textField(
    hint: String,
    value: String = "",
    inputType: Int = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
    maxLength: Int? = null,
    helper: String? = null,
    top: Int = 12,
): TextInputEditText {
    val layout = TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
        this.hint = hint
        helper?.let { helperText = it }
        maxLength?.let { isCounterEnabled = true; counterMaxLength = it }
        setBoxCornerRadii(context.dp(16).toFloat(), context.dp(16).toFloat(), context.dp(16).toFloat(), context.dp(16).toFloat())
        boxStrokeColor = context.color(R.color.mainPrimary)
        layoutParams = matchWidth(top = context.dp(top))
    }
    val edit = TextInputEditText(layout.context).apply {
        this.inputType = inputType
        setText(value)
        minHeight = context.dp(56)
        maxLength?.let { filters = arrayOf(android.text.InputFilter.LengthFilter(it)) }
    }
    layout.addView(edit)
    addView(layout)
    return edit
}

/** Grupo de chips. Com [single] funciona como escolha única; devolve o ChipGroup. */
fun ViewGroup.chips(
    options: List<String>,
    selected: Set<String> = emptySet(),
    single: Boolean = false,
    top: Int = 8,
    onChange: (Set<String>) -> Unit,
): ChipGroup = ChipGroup(context).apply {
    val group = this
    isSingleSelection = single
    chipSpacingHorizontal = context.dp(8)
    layoutParams = matchWidth(top = context.dp(top))
    options.forEach { option ->
        addView(Chip(context).apply {
            text = option
            isCheckable = true
            isChecked = option in selected
            setOnCheckedChangeListener { _, _ ->
                val now = (0 until group.childCount).map { group.getChildAt(it) as Chip }
                    .filter { it.isChecked }.map { it.text.toString() }.toSet()
                onChange(now)
            }
        })
    }
}.also(::addView)

/** Linha de lista: emoji/ícone à esquerda, título, legenda e um texto à direita opcional. */
fun ViewGroup.row(leading: String, title: String, subtitle: String? = null, trailing: String? = null, top: Int = 12, onClick: (() -> Unit)? = null): LinearLayout =
    LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = matchWidth(top = context.dp(top))
        minimumHeight = context.dp(48)
        if (onClick != null) {
            isClickable = true
            isFocusable = true
            val ripple = TypedValue().also { context.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true) }
            setBackgroundResource(ripple.resourceId)
            setOnClickListener { onClick() }
        }
        addView(TextView(context).apply {
            text = leading
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_create_chip)
            layoutParams = LinearLayout.LayoutParams(context.dp(40), context.dp(40)).apply { marginEnd = context.dp(12) }
        })
        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            text(title, TextRole.Strong)
            subtitle?.takeIf { it.isNotBlank() }?.let { text(it, TextRole.Caption, top = 2) }
        }
        addView(texts)
        trailing?.let {
            addView(TextView(context).apply {
                text = it
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(context.color(R.color.mainInk))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = context.dp(8) }
            })
        }
    }.also(::addView)

/** Estado vazio com orientação e, se fizer sentido, uma ação. */
fun ViewGroup.emptyState(emoji: String, title: String, body: String, action: String? = null, top: Int = 16, onAction: (() -> Unit)? = null) =
    card(top = top, background = R.drawable.bg_empty_state) {
        gravity = Gravity.CENTER_HORIZONTAL
        text(emoji, TextRole.Title).gravity = Gravity.CENTER
        text(title, TextRole.Strong, top = 8).gravity = Gravity.CENTER
        text(body, TextRole.Muted, top = 4).gravity = Gravity.CENTER
        if (action != null && onAction != null) secondaryButton(action, top = 16, onClick = onAction)
    }

fun View.gone() { visibility = View.GONE }
fun View.visible(show: Boolean = true) { visibility = if (show) View.VISIBLE else View.GONE }
