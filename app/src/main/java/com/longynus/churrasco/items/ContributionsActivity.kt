package com.longynus.churrasco.items

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModel
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.longynus.churrasco.AuthSession
import com.longynus.churrasco.R
import com.longynus.churrasco.data.ChurrascoRepository
import com.longynus.churrasco.data.model.ContributionInput
import com.longynus.churrasco.data.model.Event
import com.longynus.churrasco.ui.ScreenActivity
import com.longynus.churrasco.ui.TextRole
import com.longynus.churrasco.ui.buttonRow
import com.longynus.churrasco.ui.card
import com.longynus.churrasco.ui.chips
import com.longynus.churrasco.ui.dp
import com.longynus.churrasco.ui.primaryButton
import com.longynus.churrasco.ui.secondaryButton
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
            addView(ChipGroup(context).apply {
                chipSpacingHorizontal = context.dp(8)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(12) }
                ItemCatalog.categories.forEach { category ->
                    val count = othersCount[category.name] ?: 0
                    addView(Chip(context).apply {
                        text = buildString {
                            append(category.emoji).append(' ').append(ItemCatalog.label(category.name))
                            if (count > 0) append(" · ").append(count)
                        }
                        contentDescription = if (count > 0) getString(R.string.items_chip_with_others, ItemCatalog.label(category.name), count) else ItemCatalog.label(category.name)
                        setOnClickListener {
                            vm.drafts += Draft(category)
                            draw()
                            scroll.post { scroll.fullScroll(android.view.View.FOCUS_DOWN) }
                        }
                    })
                }
            })
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

    private fun LinearLayout.draftCard(draft: Draft) = card(top = 12) {
        val header = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        addView(header)
        header.addView(TextView(context).apply {
            text = "${draft.category.emoji}  ${ItemCatalog.label(draft.category.name)}"
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(getColor(R.color.mainInk))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.textButton(getString(R.string.items_remove), top = 0, danger = true) {
            vm.drafts.remove(draft); draw()
        }

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

    private fun LinearLayout.quantityStepper(draft: Draft, field: ItemField.Quantity) {
        lateinit var value: TextView
        fun refresh() { value.text = draft.quantity?.let(field::format) ?: getString(R.string.items_no_quantity) }
        buttonRow(top = 8) {
            secondaryButton("−", top = 0) {
                draft.quantity = ((draft.quantity ?: 0.0) - field.step).takeIf { it > 0 }
                refresh()
            }
            value = TextView(context).apply {
                gravity = Gravity.CENTER
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(getColor(R.color.mainInk))
                minHeight = context.dp(56)
            }
            addView(value)
            secondaryButton("+", top = 0) {
                draft.quantity = (draft.quantity ?: 0.0) + field.step
                refresh()
            }
        }
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
            call({ ChurrascoRepository.rsvp(id, "confirmed", inputs) }) {
                toast(getString(if (inputs.isEmpty()) R.string.items_confirmed_empty else R.string.items_confirmed))
                setResult(RESULT_OK)
                finish()
            }
        }
        if (inputs.isEmpty() && vm.event?.isOrganizer != true) {
            AlertDialog.Builder(this).setTitle(R.string.items_none_title).setMessage(R.string.items_none_body)
                .setNegativeButton(R.string.items_none_back, null)
                .setPositiveButton(R.string.items_none_confirm) { _, _ -> send() }.show()
        } else send()
    }
}
