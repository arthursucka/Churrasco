package com.longynus.churrasco

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.CheckBox
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.longynus.churrasco.data.ChurrascoRepository
import com.longynus.churrasco.data.model.Event
import com.longynus.churrasco.data.model.EventRequest
import com.longynus.churrasco.data.model.Friend
import com.longynus.churrasco.friends.FriendsActivity
import com.longynus.churrasco.ui.ScreenActivity
import com.longynus.churrasco.ui.TextRole
import com.longynus.churrasco.ui.buttonRow
import com.longynus.churrasco.ui.card
import com.longynus.churrasco.ui.dp
import com.longynus.churrasco.ui.emptyState
import com.longynus.churrasco.ui.primaryButton
import com.longynus.churrasco.ui.secondaryButton
import com.longynus.churrasco.ui.section
import com.longynus.churrasco.ui.text
import com.longynus.churrasco.ui.textField
import java.util.Calendar

/**
 * Criar ou editar um churrasco. Na edição (organizador ou admin) dá para mudar data,
 * hora, local, endereço e chamar mais amigos; quem já estava convidado continua.
 */
class EventFormActivity : ScreenActivity() {

    companion object { const val EXTRA_ID = "churrascoId" }

    private var editingId: String? = null
    private var date = ""
    private var time = ""
    private var friends: List<Friend> = emptyList()
    private var existing: Event? = null
    private val selectedFriends = mutableSetOf<String>()

    private lateinit var local: TextInputEditText
    private lateinit var address: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editingId = intent.getStringExtra(EXTRA_ID)
        setupScreen(getString(if (editingId == null) R.string.form_create_title else R.string.form_edit_title))
        draw()
        call({
            ChurrascoRepository.friends().mapCatching { f ->
                friends = f.friends
                editingId?.let { id -> existing = ChurrascoRepository.event(id).getOrThrow() }
            }
        }, retry = true) {
            existing?.let { e ->
                date = e.churrascoDate; time = e.hora
                local.setText(e.local); address.setText(e.address)
            }
            draw(keepText = true)
        }
    }

    private fun draw(keepText: Boolean = false) {
        val typedLocal = if (keepText && ::local.isInitialized) local.text.toString() else existing?.local.orEmpty()
        val typedAddress = if (keepText && ::address.isInitialized) address.text.toString() else existing?.address.orEmpty()
        render(bottom = {
            primaryButton(getString(if (editingId == null) R.string.form_create_action else R.string.save)) { submit() }
        }) {
            text(getString(if (editingId == null) R.string.form_create_title else R.string.form_edit_title), TextRole.Title)
            text(getString(R.string.form_subtitle), TextRole.Muted, top = 8)

            section(getString(R.string.form_when), getString(R.string.form_when_hint), top = 24)
            buttonRow(top = 12) {
                lateinit var dateButton: MaterialButton
                lateinit var timeButton: MaterialButton
                dateButton = secondaryButton(date.ifBlank { getString(R.string.form_pick_date) }, top = 0) { pickDate { dateButton.text = it } }
                timeButton = secondaryButton(time.ifBlank { getString(R.string.form_pick_time) }, top = 0) { pickTime { timeButton.text = it } }
            }
            text(getString(R.string.form_timezone_note), TextRole.Caption, top = 8)

            section(getString(R.string.form_where), getString(R.string.form_where_hint))
            local = textField(getString(R.string.form_local_hint), typedLocal, maxLength = 120)
            address = textField(
                getString(R.string.form_address_hint), typedAddress, maxLength = 200,
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS or InputType.TYPE_TEXT_FLAG_CAP_WORDS,
                helper = getString(R.string.form_address_helper),
            )

            val alreadyIn = existing?.participants?.mapNotNull { it.uid }?.toSet().orEmpty() + listOfNotNull(existing?.organizer?.uid)
            val invitable = friends.filter { it.uid != null && it.uid !in alreadyIn }
            section(
                getString(if (editingId == null) R.string.form_guests else R.string.form_more_guests),
                getString(R.string.form_guests_hint),
            )
            if (invitable.isEmpty()) {
                emptyState(
                    "🤝",
                    getString(if (friends.isEmpty()) R.string.form_no_friends_title else R.string.form_all_invited_title),
                    getString(R.string.form_no_friends_body),
                    action = getString(R.string.form_manage_friends),
                ) { startActivity(Intent(this@EventFormActivity, FriendsActivity::class.java)) }
            } else {
                card(top = 12) {
                    invitable.forEach { friend ->
                        addView(CheckBox(context).apply {
                            text = friend.displayName
                            textSize = 16f
                            minHeight = context.dp(48)
                            isChecked = friend.uid in selectedFriends
                            setOnCheckedChangeListener { _, checked ->
                                if (checked) selectedFriends += friend.uid!! else selectedFriends -= friend.uid!!
                            }
                        })
                    }
                }
            }
        }
    }

    private fun pickDate(onPicked: (String) -> Unit) {
        val now = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            date = ChurrascoDateUtils.formatDate(d, m, y); onPicked(date)
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).apply {
            datePicker.minDate = ChurrascoDateUtils.startOfToday().timeInMillis
        }.show()
    }

    private fun pickTime(onPicked: (String) -> Unit) {
        val now = Calendar.getInstance()
        TimePickerDialog(this, { _, h, min ->
            time = ChurrascoDateUtils.formatTime(h, min); onPicked(time)
        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show()
    }

    private fun submit() {
        val place = local.text.toString().trim()
        val start = ChurrascoDateUtils.eventDateTimeMillis(date, time)
        when {
            date.isBlank() || time.isBlank() -> return toast(getString(R.string.form_need_datetime))
            start == null -> return toast(getString(R.string.form_need_future))
            // Na edição, só exige futuro se a data/hora mudou: dá para corrigir o endereço com o churrasco rolando.
            start <= System.currentTimeMillis() && (existing == null || date != existing?.churrascoDate || time != existing?.hora) ->
                return toast(getString(R.string.form_need_future))
            place.length < 3 -> return toast(getString(R.string.form_need_local))
        }
        val request = EventRequest(date, time, place, address.text.toString().trim(), selectedFriends.toList())
        val id = editingId
        if (id == null) {
            call({ ChurrascoRepository.createEvent(request) }) { newId ->
                startActivity(
                    Intent(this, ActiveChurrascoDetailsActivity::class.java)
                        .putExtra("churrascoId", newId)
                        .putExtra(ActiveChurrascoDetailsActivity.EXTRA_JUST_CREATED, true)
                )
                finish()
            }
        } else {
            call({ ChurrascoRepository.updateEvent(id, request) }) {
                toast(getString(R.string.form_saved))
                setResult(RESULT_OK)
                finish()
            }
        }
    }
}
