package com.longynus.churrasco

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import com.longynus.churrasco.data.ChurrascoRepository
import com.longynus.churrasco.data.model.Event
import com.longynus.churrasco.reminders.ReminderScheduler
import com.longynus.churrasco.ui.ScreenActivity
import com.longynus.churrasco.ui.TextRole
import com.longynus.churrasco.ui.emptyState
import com.longynus.churrasco.ui.eventCard
import com.longynus.churrasco.ui.section
import com.longynus.churrasco.ui.tabs
import com.longynus.churrasco.ui.text
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Aba Churrascos: os próximos (com os convites para responder no topo) e os que já passaram.
 * Substitui as três listas da 2.1 (Convites, Ativos e Histórico).
 */
class EventsActivity : ScreenActivity() {

    companion object {
        const val EXTRA_TAB = "tab"
        const val TAB_PAST = 1
    }

    private var tab = 0
    private var upcoming: List<Event> = emptyList()
    private var invites: List<Event> = emptyList()
    private var past: List<Event>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tab = savedInstanceState?.getInt(EXTRA_TAB) ?: intent.getIntExtra(EXTRA_TAB, 0)
        setupScreen(getString(R.string.nav_events), onRefresh = ::load, navItem = R.id.nav_events)
        showFab(getString(R.string.fab_new_event), id = R.id.btnCreateChurrasco) { startActivity(Intent(this, EventFormActivity::class.java)) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(EXTRA_TAB, tab)
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        if (tab == TAB_PAST) {
            call({ ChurrascoRepository.events("past") }, retry = true, silent = past != null) { past = it; draw() }
            return
        }
        call({
            runCatching {
                coroutineScope {
                    val active = async { ChurrascoRepository.events("active").getOrThrow() }
                    val pending = async { ChurrascoRepository.pendingInvites().getOrThrow() }
                    active.await() to pending.await()
                }
            }
        }, retry = true, silent = upcoming.isNotEmpty() || invites.isNotEmpty()) { (active, pending) ->
            invites = pending
            upcoming = active.filter { event -> pending.none { it.id == event.id } }.sortedBy { it.eventStartsAt ?: Long.MAX_VALUE }
            ReminderScheduler.sync(this, active)
            draw()
        }
    }

    private fun draw(): Unit = render {
        text(getString(R.string.nav_events), TextRole.Title)
        tabs(listOf(getString(R.string.events_tab_upcoming), getString(R.string.events_tab_past)), tab) { picked ->
            if (picked != tab) { tab = picked; draw(); load() }
        }
        if (tab == TAB_PAST) drawPast() else drawUpcoming()
        addView(android.widget.Space(context), LinearLayout.LayoutParams(1, resources.getDimensionPixelSize(R.dimen.fab_size) + resources.getDimensionPixelSize(R.dimen.space_32)))
    }

    private fun LinearLayout.drawUpcoming() {
        if (invites.isNotEmpty()) {
            section(getString(R.string.home_invites_title), resources.getQuantityString(R.plurals.home_invites_subtitle, invites.size, invites.size), top = 24)
            invites.forEach { event -> eventCard(event) { open(event) } }
        }
        if (upcoming.isEmpty() && invites.isEmpty()) {
            emptyState(R.drawable.ic_fire, getString(R.string.home_empty_title), getString(R.string.home_empty_body),
                action = getString(R.string.home_empty_action), top = 24) { startActivity(Intent(this@EventsActivity, EventFormActivity::class.java)) }
            return
        }
        if (upcoming.isNotEmpty()) {
            section(getString(R.string.events_upcoming_title), top = if (invites.isEmpty()) 24 else 32)
            upcoming.forEach { event -> eventCard(event) { open(event) } }
        }
    }

    private fun LinearLayout.drawPast() {
        val list = past ?: return
        if (list.isEmpty()) {
            emptyState(R.drawable.ic_clock_counter_clockwise, getString(R.string.events_past_empty_title), getString(R.string.events_past_empty_body), top = 24)
            return
        }
        list.forEachIndexed { i, event -> eventCard(event, past = true, top = if (i == 0) 24 else 12) { open(event) } }
    }

    private fun open(event: Event) =
        startActivity(Intent(this, ActiveChurrascoDetailsActivity::class.java).putExtra("churrascoId", event.id))
}
