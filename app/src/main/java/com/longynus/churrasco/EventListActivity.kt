package com.longynus.churrasco

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.longynus.churrasco.adapter.EventAdapter
import com.longynus.churrasco.data.ApiError
import com.longynus.churrasco.data.model.Event
import com.longynus.churrasco.reminders.ReminderScheduler
import kotlinx.coroutines.launch

/** Base das três listas da barra de baixo. Recarrega sempre que a tela volta ao primeiro plano. */
abstract class EventListActivity(
    @LayoutRes private val layout: Int,
    @LayoutRes private val itemLayout: Int,
    @IdRes private val listId: Int,
    @IdRes private val emptyId: Int,
    @IdRes private val navItem: Int,
) : AppCompatActivity() {

    private lateinit var root: View
    private lateinit var list: RecyclerView
    private lateinit var empty: View
    private val adapter = EventAdapter(itemLayout) { event ->
        startActivity(Intent(this, ActiveChurrascoDetailsActivity::class.java).putExtra("churrascoId", event.id))
    }
    private var loadedOnce = false

    protected abstract suspend fun fetch(): Result<List<Event>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(layout)
        root = findViewById(R.id.rootLayout)
        list = findViewById(listId)
        empty = findViewById(emptyId)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        BottomNavHelper.setup(this, navItem)
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            root.hideConnectionState()
            if (!loadedOnce) root.showLoading()
            val result = fetch()
            root.hideLoading()
            result.onSuccess { events ->
                loadedOnce = true
                adapter.submitList(events)
                empty.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
                list.visibility = if (events.isEmpty()) View.GONE else View.VISIBLE
                onLoaded(events)
            }.onFailure { error ->
                val api = error as? ApiError
                if (api?.isNetwork == true) {
                    root.showConnectionState(getString(R.string.error_network_retry)) { load() }
                } else {
                    AlertDialog.Builder(this@EventListActivity)
                        .setMessage(api?.serverMessage ?: getString(R.string.error_generic))
                        .setPositiveButton(R.string.ok, null).show()
                }
            }
        }
    }

    protected open fun onLoaded(events: List<Event>) = Unit
}

class ActiveChurrascosActivity : EventListActivity(
    R.layout.activity_active_churrascos, R.layout.item_churrasco, R.id.rvActiveChurrascos, R.id.txtEmptyActive, R.id.nav_active,
) {
    override suspend fun fetch() = com.longynus.churrasco.data.ChurrascoRepository.events("active")
    override fun onLoaded(events: List<Event>) = ReminderScheduler.sync(this, events)
}

class PastChurrascosActivity : EventListActivity(
    R.layout.activity_past_churrascos, R.layout.item_churrasco, R.id.rvPastChurrascos, R.id.txtEmptyPast, R.id.nav_history,
) {
    override suspend fun fetch() = com.longynus.churrasco.data.ChurrascoRepository.events("past")
}

class PendingInvitesActivity : EventListActivity(
    R.layout.activity_pending_invites, R.layout.item_pending_invite, R.id.rvInvites, R.id.txtEmptyInvites, R.id.nav_invites,
) {
    override suspend fun fetch() = com.longynus.churrasco.data.ChurrascoRepository.pendingInvites()
}
