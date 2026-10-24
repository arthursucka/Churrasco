package com.longynus.churrasco.ui

import android.content.Context
import android.content.res.ColorStateList
import android.os.Build
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StyleRes
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView
import com.longynus.churrasco.R
import com.longynus.churrasco.items.ItemCategory

// Kit de componentes das telas montadas em código, seguindo o manual de identidade do
// Manda Churras: tokens de cor (res/values/colors.xml), escala de tipo (styles.xml),
// espaços de 4/8 e os raios radius-sm, radius-md, radius-lg e pílula.

fun Context.dp(value: Int): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

private fun Context.color(@ColorRes id: Int) = ContextCompat.getColor(this, id)

private fun matchWidth(top: Int = 0, bottom: Int = 0) =
    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        topMargin = top; bottomMargin = bottom
    }

enum class TextRole(@StyleRes val style: Int) {
    Display(R.style.TextAppearance_MandaChurras_Display),
    Title(R.style.TextAppearance_MandaChurras_Title),
    Section(R.style.TextAppearance_MandaChurras_Heading),
    Body(R.style.TextAppearance_MandaChurras_Body),
    Muted(R.style.TextAppearance_MandaChurras_Muted),
    Strong(R.style.TextAppearance_MandaChurras_BodyStrong),
    Caption(R.style.TextAppearance_MandaChurras_Caption),
    Overline(R.style.TextAppearance_MandaChurras_Overline),
}

fun ViewGroup.text(value: CharSequence, role: TextRole = TextRole.Body, top: Int = 0): TextView =
    MaterialTextView(context).apply {
        TextViewCompat.setTextAppearance(this, role.style)
        text = value
        layoutParams = matchWidth(top = context.dp(top))
    }.also(::addView)

/** Dá ao cartão a sombra suave do manual (tingida de marrom no Android 9+). */
private fun View.raise() {
    elevation = context.dp(2).toFloat()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val tint = context.color(R.color.shadow_tint)
        outlineAmbientShadowColor = tint
        outlineSpotShadowColor = tint
    }
}

/** Cartão surface-raised, sem borda, padding 16 e sombra suave. Conteúdo vai em [block]. */
fun ViewGroup.card(top: Int = 16, @DrawableRes background: Int = R.drawable.bg_create_section, block: LinearLayout.() -> Unit = {}): LinearLayout =
    LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(background)
        val pad = context.dp(16)
        setPadding(pad, pad, pad, pad)
        layoutParams = matchWidth(top = context.dp(top)).apply {
            // Deixa espaço para a sombra não ser cortada nas laterais.
            marginStart = context.dp(2); marginEnd = context.dp(2)
        }
        if (background == R.drawable.bg_create_section || background == R.drawable.bg_empty_state) raise()
        block()
    }.also(::addView)

fun ViewGroup.section(title: String, subtitle: String? = null, top: Int = 32): LinearLayout =
    LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = matchWidth(top = context.dp(top))
        text(title, TextRole.Section)
        subtitle?.let { text(it, TextRole.Caption, top = 4) }
    }.also(::addView)

enum class Tone(@DrawableRes val background: Int, @DrawableRes val icon: Int, @ColorRes val ink: Int, @ColorRes val iconTint: Int) {
    Info(R.drawable.bg_status_info, R.drawable.ic_info, R.color.ink, R.color.info),
    Success(R.drawable.bg_status_success, R.drawable.ic_check_circle, R.color.success_ink, R.color.success),
    Warning(R.drawable.bg_status_warning, R.drawable.ic_hourglass_medium, R.color.warning_ink, R.color.warning),
    Error(R.drawable.bg_status_error, R.drawable.ic_x_circle, R.color.danger, R.color.danger),
    Brand(R.drawable.bg_main_header, R.drawable.ic_fire, R.color.brasa_strong, R.color.brasa),
}

/** Aviso de status: ícone e título no tom, sempre com palavra além da cor. */
fun ViewGroup.banner(title: String, body: String?, tone: Tone, top: Int = 0): LinearLayout =
    card(top = top, background = tone.background) {
        val head = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        addView(head)
        head.addView(context.icon(tone.icon, tone.iconTint, 24), LinearLayout.LayoutParams(context.dp(24), context.dp(24)).apply { marginEnd = context.dp(8) })
        head.addView(MaterialTextView(context).apply {
            TextViewCompat.setTextAppearance(this, TextRole.Strong.style)
            text = title
            setTextColor(context.color(tone.ink))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        body?.let { text(it, TextRole.Body, top = 8) }
    }

private fun ViewGroup.button(label: String, styleAttr: Int, top: Int, @DrawableRes icon: Int?, onClick: () -> Unit): MaterialButton =
    MaterialButton(context, null, styleAttr).apply {
        text = label
        isAllCaps = false
        icon?.let { setIconResource(it) }
        layoutParams = matchWidth(top = context.dp(top))
        setOnClickListener { onClick() }
    }.also(::addView)

/** A ação principal da tela: pílula brasa de 52 dp. Uma por tela. */
fun ViewGroup.primaryButton(label: String, top: Int = 16, @DrawableRes icon: Int? = null, onClick: () -> Unit) =
    button(label, com.google.android.material.R.attr.materialButtonStyle, top, icon, onClick)

fun ViewGroup.secondaryButton(label: String, top: Int = 8, @DrawableRes icon: Int? = null, onClick: () -> Unit) =
    button(label, com.google.android.material.R.attr.materialButtonOutlinedStyle, top, icon, onClick)

fun ViewGroup.textButton(label: String, top: Int = 4, danger: Boolean = false, @DrawableRes icon: Int? = null, onClick: () -> Unit) =
    button(label, com.google.android.material.R.attr.borderlessButtonStyle, top, icon, onClick).apply {
        if (danger) {
            setTextColor(context.color(R.color.danger))
            iconTint = ColorStateList.valueOf(context.color(R.color.danger))
        }
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
    @DrawableRes icon: Int? = null,
): TextInputEditText {
    val layout = TextInputLayout(context, null, com.google.android.material.R.attr.textInputStyle).apply {
        this.hint = hint
        helper?.let { helperText = it }
        // O contador só aparece perto do limite (ver abaixo), para não poluir a tela.
        maxLength?.let { counterMaxLength = it }
        icon?.let { setStartIconDrawable(it) }
        layoutParams = matchWidth(top = context.dp(top))
    }
    val edit = TextInputEditText(layout.context).apply {
        this.inputType = inputType
        setText(value)
        maxLength?.let { max ->
            filters = arrayOf(android.text.InputFilter.LengthFilter(max))
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: android.text.Editable?) { layout.isCounterEnabled = (s?.length ?: 0) >= max * 0.8 }
            })
            layout.isCounterEnabled = value.length >= max * 0.8
        }
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
    chipSpacingVertical = context.dp(4)
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

// --- Elementos à esquerda das linhas: ícone, avatar ou categoria ------------------------

/** Ícone Phosphor pintado com um token. */
fun Context.icon(@DrawableRes res: Int, @ColorRes tint: Int = R.color.ink, sizeDp: Int = 24): ImageView =
    ImageView(this).apply {
        setImageResource(res)
        imageTintList = ColorStateList.valueOf(color(tint))
        layoutParams = ViewGroup.LayoutParams(dp(sizeDp), dp(sizeDp))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

/** Ícone num círculo surface-sunken de 40 dp. */
fun Context.iconBadge(@DrawableRes res: Int, @ColorRes tint: Int = R.color.ink, @ColorRes background: Int = R.color.surface_sunken): View =
    FrameLayout(this).apply {
        setBackgroundResource(R.drawable.bg_circle)
        backgroundTintList = ColorStateList.valueOf(color(background))
        addView(icon(res, tint, 20), FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
        layoutParams = ViewGroup.LayoutParams(dp(40), dp(40))
    }

private val AVATAR_COLORS = intArrayOf(R.color.avatar_1, R.color.avatar_2, R.color.avatar_3, R.color.avatar_4, R.color.avatar_5, R.color.avatar_6)

/** Avatar sem foto: inicial em on-avatar sobre uma das seis cores, escolhida pela chave (UID). */
fun Context.avatar(name: String, key: String? = null, sizeDp: Int = 40): TextView =
    MaterialTextView(this).apply {
        TextViewCompat.setTextAppearance(this, if (sizeDp >= 72) TextRole.Title.style else TextRole.Strong.style)
        text = name.trim().take(1).uppercase().ifEmpty { "?" }
        gravity = Gravity.CENTER
        setTextColor(color(R.color.on_avatar))
        setBackgroundResource(R.drawable.bg_circle)
        val index = Math.floorMod((key ?: name).hashCode(), AVATAR_COLORS.size)
        backgroundTintList = ColorStateList.valueOf(color(AVATAR_COLORS[index]))
        contentDescription = name
        layoutParams = ViewGroup.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

/**
 * A categoria do item: a ilustração no círculo da família dela. Enquanto a ilustração
 * não existe, o círculo mostra o ícone de talheres no lugar.
 */
fun Context.categoryBadge(category: ItemCategory?, sizeDp: Int = 40): View =
    FrameLayout(this).apply {
        setBackgroundResource(R.drawable.bg_circle)
        backgroundTintList = ColorStateList.valueOf(color(category?.family?.color ?: R.color.cat_doce))
        val art = category?.illustration
        if (art != null) {
            addView(ImageView(context).apply { setImageResource(art); scaleType = ImageView.ScaleType.FIT_CENTER },
                FrameLayout.LayoutParams(dp(sizeDp * 8 / 10), dp(sizeDp * 8 / 10), Gravity.CENTER))
        } else {
            val glyph = sizeDp * 4 / 10
            addView(icon(R.drawable.ic_fork_knife, R.color.ink_muted, glyph), FrameLayout.LayoutParams(dp(glyph), dp(glyph), Gravity.CENTER))
        }
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        layoutParams = ViewGroup.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

/** Linha de lista: um elemento à esquerda (ícone, avatar ou categoria), título, legenda e um valor à direita. */
fun ViewGroup.row(leading: View?, title: String, subtitle: String? = null, trailing: String? = null, top: Int = 12, onClick: (() -> Unit)? = null): LinearLayout =
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
        leading?.let { view ->
            val size = view.layoutParams?.width?.takeIf { it > 0 } ?: context.dp(40)
            addView(view, LinearLayout.LayoutParams(size, view.layoutParams?.height?.takeIf { it > 0 } ?: size).apply { marginEnd = context.dp(12) })
        }
        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            text(title, TextRole.Strong)
            subtitle?.takeIf { it.isNotBlank() }?.let { text(it, TextRole.Caption, top = 2) }
        }
        addView(texts)
        trailing?.let {
            addView(MaterialTextView(context).apply {
                TextViewCompat.setTextAppearance(this, TextRole.Strong.style)
                text = it
                fontFeatureSettings = "tnum"
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = context.dp(8) }
            })
        }
    }.also(::addView)

/**
 * Estado vazio: ilustração, título, uma frase e, se fizer sentido, a ação. Sem a
 * ilustração (prompts VAZIO-xx ainda por gerar), mostra o [icon] num círculo de 96 dp.
 */
fun ViewGroup.emptyState(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    action: String? = null,
    top: Int = 16,
    @DrawableRes illustration: Int? = null,
    onAction: (() -> Unit)? = null,
) = card(top = top, background = R.drawable.bg_empty_state) {
    gravity = Gravity.CENTER_HORIZONTAL
    setPadding(context.dp(24), context.dp(32), context.dp(24), context.dp(32))
    if (illustration != null) {
        addView(ImageView(context).apply { setImageResource(illustration); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO },
            LinearLayout.LayoutParams(context.dp(160), context.dp(160)))
    } else {
        addView(FrameLayout(context).apply {
            setBackgroundResource(R.drawable.bg_circle)
            backgroundTintList = ColorStateList.valueOf(context.color(R.color.surface_sunken))
            addView(context.icon(icon, R.color.ink_muted, 40), FrameLayout.LayoutParams(context.dp(40), context.dp(40), Gravity.CENTER))
        }, LinearLayout.LayoutParams(context.dp(96), context.dp(96)))
    }
    text(title, TextRole.Section, top = 16).gravity = Gravity.CENTER
    text(body, TextRole.Muted, top = 8).gravity = Gravity.CENTER
    if (action != null && onAction != null) primaryButton(action, top = 24, onClick = onAction)
}

fun View.gone() { visibility = View.GONE }
fun View.visible(show: Boolean = true) { visibility = if (show) View.VISIBLE else View.GONE }
