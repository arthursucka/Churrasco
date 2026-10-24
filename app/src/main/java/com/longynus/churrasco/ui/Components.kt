package com.longynus.churrasco.ui

import android.content.Context
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import com.google.android.material.textview.MaterialTextView
import com.longynus.churrasco.R
import com.longynus.churrasco.data.model.Event
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

// Componentes da 2.2 (manual: Tabs, DateBadge, StatusChip, Avatar em pilha, EventCard e
// NextEventHero). Só montam views com os tokens; quem chama decide o que mostrar.

private fun Context.color(@ColorRes id: Int) = ContextCompat.getColor(this, id)
private val PT_BR = Locale("pt", "BR")

private fun Context.ripple(borderless: Boolean = false): Int =
    TypedValue().also {
        theme.resolveAttribute(if (borderless) android.R.attr.selectableItemBackgroundBorderless else android.R.attr.selectableItemBackground, it, true)
    }.resourceId

private fun Context.label(value: CharSequence, role: TextRole): TextView = MaterialTextView(this).apply {
    TextViewCompat.setTextAppearance(this, role.style)
    text = value
}

/**
 * Abas em pílula: trilho surface-sunken e a aba atual em surface-raised. Rola na horizontal
 * quando não cabem. Devolve a função que troca a aba marcada sem chamar [onSelect].
 */
fun ViewGroup.tabs(options: List<String>, selected: Int, top: Int = 16, onSelect: (Int) -> Unit): (Int) -> Unit {
    val track = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setBackgroundResource(R.drawable.bg_pill_sunken)
        setPadding(context.dp(4), context.dp(4), context.dp(4), context.dp(4))
    }
    val items = options.mapIndexed { index, option ->
        context.label(option, TextRole.Caption).apply {
            gravity = Gravity.CENTER
            minHeight = context.dp(40)
            setPadding(context.dp(16), 0, context.dp(16), 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { onSelect(index) }
        }.also { track.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
    }
    fun mark(index: Int) = items.forEachIndexed { i, view ->
        val on = i == index
        view.isSelected = on
        TextViewCompat.setTextAppearance(view, if (on) TextRole.Strong.style else TextRole.Caption.style)
        view.textSize = 13f
        view.setTextColor(context.color(if (on) R.color.ink else R.color.ink_muted))
        view.setBackgroundResource(if (on) R.drawable.bg_tab_selected else context.ripple())
        view.elevation = if (on) context.dp(1).toFloat() else 0f
    }
    mark(selected)
    addView(HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        isFillViewport = true
        addView(track, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(top) })
    return ::mark
}

/** Dia, mês e dia da semana do churrasco, no fuso dele. */
data class EventDay(val weekday: String, val day: String, val month: String, val time: String, val daysFromToday: Long)

fun Event.day(now: Long = System.currentTimeMillis()): EventDay? {
    val start = eventStartsAt ?: return null
    val zone = TimeZone.getTimeZone(timeZone.ifBlank { "America/Sao_Paulo" })
    val at = Calendar.getInstance(zone, PT_BR).apply { timeInMillis = start }
    // Dias de calendário entre hoje e o churrasco, no fuso dele (sem java.time: o app roda no Android 6).
    fun dayNumber(c: Calendar) = (c.timeInMillis + c.get(Calendar.ZONE_OFFSET) + c.get(Calendar.DST_OFFSET)) / 86_400_000L
    val today = Calendar.getInstance(zone, PT_BR).apply { timeInMillis = now }
    val weekday = at.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, PT_BR).orEmpty().trimEnd('.')
    val month = at.getDisplayName(Calendar.MONTH, Calendar.SHORT, PT_BR).orEmpty().trimEnd('.')
    val hour = at.get(Calendar.HOUR_OF_DAY)
    val minute = at.get(Calendar.MINUTE)
    val time = if (minute == 0) "${hour}h" else "%dh%02d".format(hour, minute)
    return EventDay(weekday, at.get(Calendar.DAY_OF_MONTH).toString(), month, time, dayNumber(at) - dayNumber(today))
}

/** "É hoje, às 13h", "É amanhã", "Faltam 5 dias"; para churrascos passados, a data. */
fun Context.countdown(event: Event): String {
    val d = event.day() ?: return event.churrascoDate
    return when {
        d.daysFromToday == 0L -> getString(R.string.countdown_today, d.time)
        d.daysFromToday == 1L -> getString(R.string.countdown_tomorrow)
        d.daysFromToday > 1 -> resources.getQuantityString(R.plurals.countdown_days, d.daysFromToday.toInt(), d.daysFromToday.toInt())
        else -> getString(R.string.countdown_past, d.day, d.month)
    }
}

/** "Sáb, 14 de nov · 13h". */
fun Event.whenLine(): String = day()?.let { "${it.weekday.replaceFirstChar(Char::titlecase)}, ${it.day} de ${it.month} · ${it.time}" } ?: "$churrascoDate · $hora"

/** Selo de data: dia da semana, dia e mês. Passados em surface-sunken. */
fun Context.dateBadge(event: Event, past: Boolean = false): View = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    gravity = Gravity.CENTER_HORIZONTAL
    setPadding(0, dp(8), 0, dp(8))
    setBackgroundResource(R.drawable.bg_date_badge)
    backgroundTintList = ColorStateList.valueOf(color(if (past) R.color.surface_sunken else R.color.brasa_soft))
    val ink = color(if (past) R.color.ink_muted else R.color.brasa_strong)
    val d = event.day()
    addView(label(d?.weekday?.uppercase(PT_BR) ?: "", TextRole.Overline).apply { setTextColor(ink); gravity = Gravity.CENTER })
    addView(label(d?.day ?: "?", TextRole.Title).apply {
        setTextColor(ink); gravity = Gravity.CENTER; textSize = 24f; fontFeatureSettings = "tnum"
    })
    addView(label(d?.month?.uppercase(PT_BR) ?: "", TextRole.Overline).apply { setTextColor(ink); gravity = Gravity.CENTER })
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    layoutParams = ViewGroup.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT)
}

enum class Status(@ColorRes val background: Int, @ColorRes val ink: Int, @DrawableRes val icon: Int, val label: Int) {
    Organizer(R.color.brasa_soft, R.color.brasa_strong, R.drawable.ic_fire, R.string.status_chip_organizer),
    Confirmed(R.color.success_soft, R.color.success_ink, R.drawable.ic_check_circle, R.string.status_chip_confirmed),
    Invited(R.color.warning_soft, R.color.warning_ink, R.drawable.ic_hourglass_medium, R.string.status_chip_invited),
    Declined(R.color.danger_soft, R.color.danger, R.drawable.ic_x_circle, R.string.status_chip_declined);

    companion object {
        fun of(event: Event): Status? = when (event.myStatus) {
            "organizer" -> Organizer
            "confirmed" -> Confirmed
            "invited" -> Invited
            "declined" -> Declined
            else -> null
        }
    }
}

/** Chip de status: sempre ícone e palavra, nunca só cor. */
fun Context.statusChip(status: Status): View = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    setBackgroundResource(R.drawable.bg_pill_sunken)
    backgroundTintList = ColorStateList.valueOf(color(status.background))
    setPadding(dp(8), 0, dp(12), 0)
    minimumHeight = dp(28)
    addView(icon(status.icon, status.ink, 16), LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginEnd = dp(4) })
    addView(label(getString(status.label), TextRole.Caption).apply { setTextColor(color(status.ink)); typeface = android.graphics.Typeface.create(typeface, android.graphics.Typeface.BOLD) })
    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28))
}

/** Até três avatares sobrepostos e o restante em texto ("+2"). */
fun Context.avatarStack(people: List<Pair<String, String?>>, caption: String?): View = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    people.take(3).forEachIndexed { i, (name, uid) ->
        addView(avatar(name, uid, 32).apply {
            // Anel na cor do cartão, para os avatares se separarem.
            foreground = ContextCompat.getDrawable(context, R.drawable.ring_card)
        }, LinearLayout.LayoutParams(dp(32), dp(32)).apply { if (i > 0) marginStart = -dp(8) })
    }
    caption?.let { addView(label(it, TextRole.Caption), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) }) }
}

/** Cartão de churrasco: selo de data, nome, hora e local, quem vai e o seu estado. */
fun ViewGroup.eventCard(event: Event, past: Boolean = false, top: Int = 12, onClick: () -> Unit): View =
    card(top = top) {
        orientation = LinearLayout.HORIZONTAL
        isClickable = true
        isFocusable = true
        foreground = ContextCompat.getDrawable(context, context.ripple())
        setOnClickListener { onClick() }
        addView(context.dateBadge(event, past), LinearLayout.LayoutParams(context.dp(56), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = context.dp(16) })
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        addView(body, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val head = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
        body.addView(head)
        head.addView(context.label(event.local, TextRole.Section).apply { maxLines = 2 }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (!past) Status.of(event)?.let { head.addView(context.statusChip(it), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, context.dp(28)).apply { marginStart = context.dp(8) }) }
        val where = event.address.substringBefore(',').ifBlank { null }
        body.text(listOfNotNull(event.day()?.time ?: event.hora, where).joinToString(" · "), TextRole.Caption, top = 4)
        val going = buildList {
            add(event.organizer.displayName to event.organizer.uid)
            event.confirmed.forEach { add(it.displayName to it.uid) }
        }
        val caption = if (event.myStatus == "invited") context.getString(R.string.card_invite_from, event.organizer.displayName)
        else context.resources.getQuantityString(R.plurals.card_going, going.size, going.size)
        body.addView(context.avatarStack(going, caption), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(12) })
    }

/** Uma pendência no cartão do próximo churrasco: ícone e frase. */
data class Todo(@DrawableRes val icon: Int, val text: String)

/** O cartão do topo do Início: o próximo churrasco, quanto falta e o que ainda falta fazer. */
fun ViewGroup.nextEventHero(event: Event, todos: List<Todo>, action: String, onAction: () -> Unit, onOpen: () -> Unit): View =
    card(top = 24, background = R.drawable.bg_main_header) {
        setPadding(context.dp(24), context.dp(24), context.dp(24), context.dp(24))
        isClickable = true
        isFocusable = true
        foreground = ContextCompat.getDrawable(context, context.ripple())
        setOnClickListener { onOpen() }
        text(context.getString(R.string.hero_overline), TextRole.Overline).setTextColor(context.color(R.color.brasa_strong))
        text(context.countdown(event), TextRole.Display, top = 12)
        text(event.local, TextRole.Title, top = 4)
        text(listOfNotNull(event.whenLine(), event.address.substringBefore(',').ifBlank { null }).joinToString(" · "), TextRole.Caption, top = 4)
        todos.take(3).forEachIndexed { i, todo ->
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(context.icon(todo.icon, R.color.brasa_strong, 20), LinearLayout.LayoutParams(context.dp(20), context.dp(20)).apply { marginEnd = context.dp(8) })
                addView(context.label(todo.text, TextRole.Body), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(if (i == 0) 16 else 8) })
        }
        primaryButton(action, top = 16, onClick = onAction)
    }
