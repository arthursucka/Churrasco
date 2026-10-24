package com.longynus.churrasco.items

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.ViewModel
import com.google.android.material.textfield.TextInputEditText
import com.longynus.churrasco.AuthSession
import com.longynus.churrasco.R
import com.longynus.churrasco.data.ChurrascoRepository
import com.longynus.churrasco.data.model.ContributionInput
import com.longynus.churrasco.data.model.Event
import com.longynus.churrasco.ui.ScreenActivity
import com.longynus.churrasco.ui.TextRole
import com.longynus.churrasco.ui.card
import com.longynus.churrasco.ui.categoryBadge
import com.longynus.churrasco.ui.celebrate
import com.longynus.churrasco.ui.chips
import com.longynus.churrasco.ui.dp
import com.longynus.churrasco.ui.primaryButton
import com.longynus.churrasco.ui.section
import com.longynus.churrasco.ui.text
import com.longynus.churrasco.ui.textButton
import com.longynus.churrasco.ui.textField

/** Um item sendo montado na tela. Vira [ContributionInput] ao confirmar. */
class Draft(
    val category: ItemCategory,
    val id: String? = null,
    val values: MutableMap<String, String> = mutableMapOf(),
    var quantity: Double? = null,
    var note: String = "",
) {
    fun toInput(): ContributionInput {
        val details = values.filterValues { it.isNotBlank() }.toMutableMap()
        category.fields.filterIsInstance<ItemField.Quantity>().firstOrNull()?.let { q ->
            quantity?.takeIf { it > 0 }?.let { details[q.key] = q.format(it) }
        }
        return ContributionInput(id, category.name, details, note.trim())
    }
}

class ContributionsViewModel : ViewModel() {
    var event: Event? = null
    val drafts = mutableListOf<Draft>()
    var loaded = false
}

/**
 * "O que você vai levar?": escolhe categorias, detalha cada uma e confirma presença.
 * Várias pessoas podem levar a mesma categoria; "Outro" aceita qualquer coisa.
 */
class ContributionsActivity : ScreenActivity() {

    companion object { const val EXTRA_ID = "churrascoId" }

    private val vm: ContributionsViewModel by viewModels()
    private lateinit var id: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        id = intent.getStringExtra(EXTRA_ID) ?: return finish()
        setupScreen(getString(R.string.items_title))
        if (vm.loaded) draw() else call({ ChurrascoRepository.event(id) }, retry = true) { event ->
            vm.event = event
            vm.loaded = true
            val myUid = AuthSession.uid(this)
            event.contributionsOf(myUid).forEach { c ->
                val category = ItemCatalog.find(c.category) ?: ItemCatalog.find(ItemCatalog.OTHER)!!
                val quantityField = category.fields.filterIsInstance<ItemField.Quantity>().firstOrNull()
                val values = c.details.toMutableMap()
                val quantity = quantityField?.let { values.remove(it.key)?.substringBefore(' ')?.replace(',', '.')?.toDoubleOrNull() }
                // Itens da v1 ("Queijo Coalho") viram a categoria nova com o tipo preenchido.
                if (category.name != c.category && category.fields.any { it.key == "tipo" }) {
                    values.putIfAbsent("tipo", c.category.substringAfter(' ', c.category))
                }
                val note = if (category.isOther && c.note.isBlank()) c.category else c.note
                vm.drafts += Draft(category, c.id, values, quantity, note)
            }
            draw()
        }
    }

    private fun draw() {
        val event = vm.event ?: return
        val alreadyConfirmed = event.isMember
        render(bottom = {
            primaryButton(getString(if (alreadyConfirmed) R.string.items_save else R.string.items_confirm)) { submit() }
        }) {
            text(getString(R.string.items_title), TextRole.Title)
            text(getString(R.string.items_subtitle), TextRole.Muted, top = 8)

            section(getString(R.string.items_pick_title), getString(R.string.items_pick_subtitle), top = 24)
            val othersCount = event.contributions.filter { it.uid != AuthSession.uid(this@ContributionsActivity) }
                .groupingBy { ItemCatalog.find(it.category)?.name ?: it.category }.eachCount()
            categoryGrid(othersCount) { category ->
                vm.drafts += Draft(category)
                draw()
                scroll.post { scroll.fullScroll(android.view.View.FOCUS_DOWN) }
            }
            text(getString(R.string.items_count_hint), TextRole.Caption, top = 8)

            if (vm.drafts.isEmpty()) {
                card(top = 24, background = R.drawable.bg_empty_state) {
                    text(getString(R.string.items_empty), TextRole.Muted).gravity = Gravity.CENTER
                }
            } else {
                section(getString(R.string.items_mine_title, vm.drafts.size))
                vm.drafts.forEach { draft -> draftCard(draft) }
            }
        }
    }

    /** Grade de categorias: a ilustração no círculo da família, o nome e quantos outros já levam. */
    private fun LinearLayout.categoryGrid(othersCount: Map<String, Int>, onPick: (ItemCategory) -> Unit) {
        ItemCatalog.categories.chunked(3).forEachIndexed { index, rowItems ->
            val line = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = context.dp(if (index == 0) 16 else 8) }
            }
            addView(line)
            rowItems.forEach { category ->
                val label = ItemCatalog.label(category.name)
                val count = othersCount[category.name] ?: 0
                line.addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(context.dp(4), context.dp(8), context.dp(4), context.dp(8))
                    isClickable = true
                    isFocusable = true
                    val ripple = TypedValue().also { context.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true) }
                    setBackgroundResource(ripple.resourceId)
                    contentDescription = if (count > 0) getString(R.string.items_chip_with_others, label, count) else label
                    setOnClickListener { onPick(category) }
                    addView(context.categoryBadge(category, 72))
                    text(label, TextRole.Strong, top = 8).apply {
                        gravity = Gravity.CENTER; maxLines = 2; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }
                    if (count > 0) text(resources.getQuantityString(R.plurals.items_others_bring, count, count), TextRole.Caption, top = 2).apply {
                        gravity = Gravity.CENTER; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
            repeat(3 - rowItems.size) { line.addView(Space(context), LinearLayout.LayoutParams(0, 0, 1f)) }
        }
    }

    private fun LinearLayout.draftCard(draft: Draft) = card(top = 12) {
        val header = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        addView(header)
        header.addView(context.categoryBadge(draft.category, 48), LinearLayout.LayoutParams(context.dp(48), context.dp(48)).apply { marginEnd = context.dp(12) })
        header.addView(TextView(context).apply {
            TextViewCompat.setTextAppearance(this, TextRole.Section.style)
            text = ItemCatalog.label(draft.category.name)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.textButton(getString(R.string.items_remove), top = 0, danger = true) {
            vm.drafts.remove(draft); draw()
        }.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        if (draft.category.isOther) {
            textField(getString(R.string.items_other_hint), draft.note, maxLength = 200).watch { draft.note = it }
            return@card
        }

        draft.category.fields.forEach { field ->
            when (field) {
                is ItemField.Choice -> {
                    text(field.label, TextRole.Caption, top = 16)
                    val custom = draft.values[field.key]?.takeIf { it !in field.options }
                    var customField: TextInputEditText? = null
                    chips(field.options, setOfNotNull(draft.values[field.key]), single = true) { picked ->
                        picked.firstOrNull()?.let { draft.values[field.key] = it; customField?.setText("") }
                            ?: draft.values.remove(field.key)
                    }
                    if (field.allowCustom) {
                        customField = textField(getString(R.string.items_custom_hint, field.label.lowercase()), custom.orEmpty(), maxLength = 80, top = 8)
                            .also { it.watch { typed -> if (typed.isNotBlank()) draft.values[field.key] = typed.trim() } }
                    }
                }
                is ItemField.Quantity -> {
                    text(field.label, TextRole.Caption, top = 16)
                    quantityStepper(draft, field)
                }
            }
        }
        textField(getString(R.string.items_note_hint), draft.note, maxLength = 200, top = 16).watch { draft.note = it }
    }

    /** Contador compacto: pílula surface-sunken com − valor +. */
    private fun LinearLayout.quantityStepper(draft: Draft, field: ItemField.Quantity) {
        lateinit var value: TextView
        fun refresh() { value.text = draft.quantity?.let(field::format) ?: getString(R.string.items_no_quantity) }
        fun stepButton(symbol: String, description: String, onClick: () -> Unit) = TextView(context).apply {
            TextViewCompat.setTextAppearance(this, TextRole.Section.style)
            text = symbol
            gravity = Gravity.CENTER
            contentDescription = description
            isClickable = true
            isFocusable = true
            val ripple = TypedValue().also { context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true) }
            setBackgroundResource(ripple.resourceId)
            setOnClickListener { onClick(); refresh() }
        }
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_pill_sunken)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, context.dp(48)).apply { topMargin = context.dp(8) }
            addView(stepButton("−", getString(R.string.items_less)) { draft.quantity = ((draft.quantity ?: 0.0) - field.step).takeIf { it > 0 } },
                LinearLayout.LayoutParams(context.dp(48), context.dp(48)))
            value = TextView(context).apply {
                TextViewCompat.setTextAppearance(this, TextRole.Strong.style)
                fontFeatureSettings = "tnum"
                gravity = Gravity.CENTER
                minWidth = context.dp(96)
            }
            addView(value)
            addView(stepButton("+", getString(R.string.items_more)) { draft.quantity = (draft.quantity ?: 0.0) + field.step },
                LinearLayout.LayoutParams(context.dp(48), context.dp(48)))
        })
        refresh()
        chips(field.presets.map(field::format), top = 8) { picked ->
            picked.firstOrNull()?.let { label -> draft.quantity = field.presets.firstOrNull { field.format(it) == label }; refresh() }
        }.isSingleSelection = true
    }

    private fun TextInputEditText.watch(onChange: (String) -> Unit) = addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = onChange(s?.toString().orEmpty())
    })

    private fun submit() {
        if (vm.drafts.any { it.category.isOther && it.note.isBlank() }) return toast(getString(R.string.items_other_required))
        val inputs = vm.drafts.map { it.toInput() }
        val send = {
            val firstTime = vm.event?.isMember != true
            call({ ChurrascoRepository.rsvp(id, "confirmed", inputs) }) {
                setResult(RESULT_OK)
                if (firstTime) {
                    // O pico do app: confirmar presença ganha a comemoração.
                    celebrate(getString(R.string.items_confirmed), getString(R.string.celebrate_confirmed_body),
                        getString(R.string.hero_action_open)) { finish() }
                } else {
                    toast(getString(if (inputs.isEmpty()) R.string.items_confirmed_empty else R.string.items_saved))
                    finish()
                }
            }
        }
        if (inputs.isEmpty() && vm.event?.isOrganizer != true) {
            MaterialAlertDialogBuilder(this).setTitle(R.string.items_none_title).setMessage(R.string.items_none_body)
                .setNegativeButton(R.string.items_none_back, null)
                .setPositiveButton(R.string.items_none_confirm) { _, _ -> send() }.show()
        } else send()
    }
}
