package com.longynus.churrasco

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.LinearLayout
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.longynus.churrasco.data.ChurrascoRepository
import com.longynus.churrasco.data.model.Balance
import com.longynus.churrasco.data.model.Event
import com.longynus.churrasco.details.Money
import com.longynus.churrasco.invites.InviteLinkActivity
import com.longynus.churrasco.items.ContributionsActivity
import com.longynus.churrasco.reminders.ReminderScheduler
import com.longynus.churrasco.ui.ScreenActivity
import com.longynus.churrasco.ui.TextRole
import com.longynus.churrasco.ui.Todo
import com.longynus.churrasco.ui.emptyState
import com.longynus.churrasco.ui.eventCard
import com.longynus.churrasco.ui.nextEventHero
import com.longynus.churrasco.ui.section
import com.longynus.churrasco.ui.text
import com.longynus.churrasco.ui.textButton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Início: o painel do Manda Churras. Mostra o próximo churrasco com a contagem e o que ainda
 * falta você fazer, os convites para responder e o que vem depois.
 */
class MainActivity : ScreenActivity() {

    private var canReview = false
    private var started = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AuthSession.isReady(this)) {
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return
        }
        setupScreen(getString(R.string.nav_home), onRefresh = ::load, navItem = R.id.nav_home)
        started = true
        showFab(getString(R.string.fab_new_event), id = R.id.btnCreateChurrasco) { startActivity(Intent(this, EventFormActivity::class.java)) }

        requestNotificationPermissionIfNeeded()
        FcmTokenManager.syncCurrentToken(this)
        lifecycleScope.launch {
            // Só o administrador que revisa contas antigas vê o atalho de revisão.
            canReview = ChurrascoRepository.legacyStatus().getOrNull()?.canReview == true
            if (canReview) load()
        }
        // Link de convite aberto antes de entrar na conta.
        AuthSession.takePendingInvite(this)?.let { token ->
            startActivity(Intent(this, InviteLinkActivity::class.java).putExtra(InviteLinkActivity.EXTRA_TOKEN, token))
        }
    }

    override fun onResume() {
        super.onResume()
        if (started) load()
    }

    private data class Home(val upcoming: List<Event>, val invites: List<Event>, val balance: Balance?)

    private fun load() {
        call({
            runCatching {
                coroutineScope {
                    val events = async { ChurrascoRepository.events("active").getOrThrow() }
                    val invites = async { ChurrascoRepository.pendingInvites().getOrThrow() }
                    val upcoming = events.await().filter { it.isMember }.sortedBy { it.eventStartsAt ?: Long.MAX_VALUE }
                    val balance = upcoming.firstOrNull()?.let { ChurrascoRepository.balance(it.id).getOrNull() }
                    Home(upcoming, invites.await(), balance)
                }
            }
        }, retry = true, silent = content.childCount > 0) { home ->
            ReminderScheduler.sync(this, home.upcoming)
            draw(home)
        }
    }

    private fun draw(home: Home) = render {
        text(getString(R.string.main_greeting, AuthSession.displayName(this@MainActivity)), TextRole.Title).id = R.id.txtGreeting
        text(getString(R.string.main_subtitle), TextRole.Muted, top = 4)

        val next = home.upcoming.firstOrNull()
        if (next == null && home.invites.isEmpty()) {
            emptyState(R.drawable.ic_fire, getString(R.string.home_empty_title), getString(R.string.home_empty_body),
                action = getString(R.string.home_empty_action), top = 24) { startActivity(Intent(this@MainActivity, EventFormActivity::class.java)) }
        }
        next?.let { hero(it, home.balance) }

        if (home.invites.isNotEmpty()) {
            section(getString(R.string.home_invites_title), resources.getQuantityString(R.plurals.home_invites_subtitle, home.invites.size, home.invites.size))
            home.invites.forEach { event -> eventCard(event) { open(event) } }
        }
        val later = home.upcoming.drop(1)
        if (later.isNotEmpty()) {
            section(getString(R.string.home_later_title))
            later.forEach { event -> eventCard(event) { open(event) } }
        }
        if (canReview) {
            textButton(getString(R.string.legacy_review_accounts), top = 32) {
                startActivity(Intent(this@MainActivity, LegacyLinkReviewActivity::class.java))
            }.id = R.id.btnReviewLegacyMain
        }
        // Espaço para o botão flutuante não cobrir o último cartão.
        addView(android.widget.Space(context), LinearLayout.LayoutParams(1, resources.getDimensionPixelSize(R.dimen.fab_size) + resources.getDimensionPixelSize(R.dimen.space_32)))
    }

    private fun LinearLayout.hero(event: Event, balance: Balance?) {
        val me = AuthSession.uid(this@MainActivity)
        val owe = balance?.transfers?.firstOrNull { it.from.uid == me }
        val noItems = event.contributionsOf(me).isEmpty()
        val todos = buildList {
            if (noItems) add(Todo(R.drawable.ic_list_checks, getString(R.string.hero_todo_items)))
            owe?.let { add(Todo(R.drawable.ic_receipt, getString(R.string.hero_todo_pay, Money.format(it.amountCents), it.to.displayName))) }
            if (event.isOrganizer && event.pending.isNotEmpty()) {
                add(Todo(R.drawable.ic_hourglass_medium, resources.getQuantityString(R.plurals.hero_todo_pending, event.pending.size, event.pending.size)))
            }
            if (isEmpty()) add(Todo(R.drawable.ic_check_circle, getString(R.string.hero_todo_none)))
        }
        if (noItems) nextEventHero(event, todos, getString(R.string.hero_action_items), { openItems(event) }) { open(event) }
        else nextEventHero(event, todos, getString(R.string.hero_action_open), { open(event) }) { open(event) }
    }

    private fun open(event: Event) =
        startActivity(Intent(this, ActiveChurrascoDetailsActivity::class.java).putExtra("churrascoId", event.id))

    private fun openItems(event: Event) =
        startActivity(Intent(this, ContributionsActivity::class.java).putExtra(ContributionsActivity.EXTRA_ID, event.id))

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }
}
