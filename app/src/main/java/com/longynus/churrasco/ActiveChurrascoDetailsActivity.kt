package com.longynus.churrasco

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.longynus.churrasco.data.ApiError
import com.longynus.churrasco.data.ChurrascoRepository
import com.longynus.churrasco.data.model.Balance
import com.longynus.churrasco.data.model.ChatMessage
import com.longynus.churrasco.data.model.Event
import com.longynus.churrasco.data.model.SharedLocation
import com.longynus.churrasco.data.model.Transfer
import com.longynus.churrasco.details.EventDetailsViewModel
import com.longynus.churrasco.details.Money
import com.longynus.churrasco.invites.InviteLinkActivity
import com.longynus.churrasco.items.ContributionsActivity
import com.longynus.churrasco.items.ItemCatalog
import com.longynus.churrasco.location.LocationShareService
import com.longynus.churrasco.pix.PixPayload
import com.longynus.churrasco.reminders.ReminderScheduler
import com.longynus.churrasco.ui.ScreenActivity
import com.longynus.churrasco.ui.TextRole
import com.longynus.churrasco.ui.Tone
import com.longynus.churrasco.ui.avatar
import com.longynus.churrasco.ui.banner
import com.longynus.churrasco.ui.buttonRow
import com.longynus.churrasco.ui.card
import com.longynus.churrasco.ui.categoryBadge
import com.longynus.churrasco.ui.countdown
import com.longynus.churrasco.ui.dp
import com.longynus.churrasco.ui.iconBadge
import com.longynus.churrasco.ui.primaryButton
import com.longynus.churrasco.ui.row
import com.longynus.churrasco.ui.secondaryButton
import com.longynus.churrasco.ui.section
import com.longynus.churrasco.ui.tabs
import com.longynus.churrasco.ui.text
import com.longynus.churrasco.ui.textButton
import com.longynus.churrasco.ui.textField
import com.longynus.churrasco.ui.visible
import com.longynus.churrasco.ui.whenLine
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Centro do churrasco: convite, quem vai, quem leva o quê, contas com Pix, mapa ao vivo
 * e conversa. Cada seção é atualizada sozinha, então o mapa e o texto do chat não se
 * perdem quando os dados mudam.
 */
class ActiveChurrascoDetailsActivity : ScreenActivity() {

    companion object {
        const val EXTRA_JUST_CREATED = "justCreated"
        private const val HOUR = 3_600_000L
    }

    /** As abas da tela; Contas, Chat e Mapa só para quem vai. */
    private enum class Tab(val label: Int, val membersOnly: Boolean) {
        Summary(R.string.tab_summary, false), Items(R.string.tab_items, false), Costs(R.string.tab_costs, true),
        Chat(R.string.tab_chat, true), Map(R.string.tab_map, true),
    }

    private val vm: EventDetailsViewModel by viewModels()
    private lateinit var churrascoId: String
    private val myUid by lazy { AuthSession.uid(this) }

    private lateinit var headerSection: LinearLayout
    private lateinit var tabsSection: LinearLayout
    private lateinit var whereSection: LinearLayout
    private var tab = Tab.Summary
    private var availableTabs: List<Tab> = emptyList()
    private var markTab: ((Int) -> Unit)? = null
    private var chatInput: com.google.android.material.textfield.TextInputEditText? = null
    private lateinit var statusSection: LinearLayout
    private lateinit var peopleSection: LinearLayout
    private lateinit var itemsSection: LinearLayout
    private lateinit var costsSection: LinearLayout
    private lateinit var mapSection: LinearLayout
    private lateinit var chatSection: LinearLayout
    private lateinit var adminSection: LinearLayout
    private lateinit var messagesBox: LinearLayout
    private lateinit var mapView: MapView
    private lateinit var mapStatus: TextView
    private lateinit var shareLocationButton: com.google.android.material.button.MaterialButton
    private var askedToShare = false

    private val refreshOnReturn = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.refresh() }

    private val locationPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true || granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            startSharing()
        } else {
            showMessage(getString(R.string.map_permission_denied))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        churrascoId = intent.getStringExtra("churrascoId") ?: return finish()
        Configuration.getInstance().apply {
            load(this@ActiveChurrascoDetailsActivity, getSharedPreferences("osmdroid", MODE_PRIVATE))
            // Política de uso dos mapas do OpenStreetMap: identificar o app.
            userAgentValue = "Churrasco/${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})"
        }
        setupScreen(getString(R.string.details_event_title), onRefresh = { val job = vm.refresh(); lifecycleScope.launch { job?.join(); stopRefreshing() } })
        buildSkeleton()

        collect(vm.event) { it?.let(::bindEvent) }
        collect(vm.balance) { bindCosts(vm.event.value, it); bindBottom() }
        collect(vm.messages) { bindMessages(it) }
        collect(vm.locations) { bindLocations(it) }
        collect(vm.loading) { if (it) root.showLoading() else root.hideLoading() }
        collect(vm.errors) { error ->
            stopRefreshing()
            if ((error as? ApiError)?.status == 404) {
                MaterialAlertDialogBuilder(this).setMessage(R.string.details_not_found).setPositiveButton(R.string.ok) { _, _ -> finish() }.show()
            } else showError(error) { vm.refresh() }
        }
        collect(LocationShareService.state) { bindSharingState() }
        vm.start(churrascoId)
    }

    override fun onResume() {
        super.onResume()
        if (::mapView.isInitialized) mapView.onResume()
    }

    override fun onPause() {
        if (::mapView.isInitialized) mapView.onPause()
        super.onPause()
    }

    // --- Esqueleto ---------------------------------------------------------------

    private fun buildSkeleton() = render {
        headerSection = holder()
        tabsSection = holder()
        statusSection = holder()
        whereSection = holder()
        adminSection = holder()
        peopleSection = holder()
        itemsSection = holder()
        costsSection = holder()
        mapSection = holder().apply { visible(false) }
        chatSection = holder().apply { visible(false) }

        mapSection.apply {
            section(getString(R.string.map_title), getString(R.string.map_subtitle), top = 24)
            card(top = 12) {
                mapView = MapView(context).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    isHorizontalMapRepetitionEnabled = false
                    isVerticalMapRepetitionEnabled = false
                    minZoomLevel = 3.0
                    controller.setZoom(4.0)
                    controller.setCenter(GeoPoint(-14.235, -51.925))
                    // Dentro de um ScrollView: o mapa fica com os gestos enquanto o dedo está nele.
                    setOnTouchListener { v, _ -> v.parent.requestDisallowInterceptTouchEvent(true); false }
                }
                addView(FrameLayout(context).apply {
                    clipToOutline = true
                    setBackgroundResource(R.drawable.bg_create_section)
                    // O mapa ocupa boa parte da tela: a aba Mapa existe para ele.
                    addView(mapView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, (resources.displayMetrics.heightPixels * 0.5).toInt()))
                })
                mapStatus = text("", TextRole.Caption, top = 12)
                shareLocationButton = primaryButton(getString(R.string.map_share), icon = R.drawable.ic_navigation_arrow) { toggleSharing() }
            }
        }

        chatSection.apply {
            section(getString(R.string.details_chat_title), getString(R.string.chat_subtitle), top = 24)
            card(top = 12) {
                messagesBox = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                addView(messagesBox)
            }
        }
    }

    /** Troca de aba: mostra as seções dela e a ação fixa certa no rodapé. */
    private fun showTab(next: Tab) {
        tab = next
        markTab?.invoke(availableTabs.indexOf(next))
        val sections = mapOf(
            Tab.Summary to listOf(statusSection, whereSection, peopleSection, adminSection),
            Tab.Items to listOf(itemsSection), Tab.Costs to listOf(costsSection),
            Tab.Chat to listOf(chatSection), Tab.Map to listOf(mapSection),
        )
        sections.forEach { (t, views) -> views.forEach { it.visible(t == next) } }
        bindBottom()
        scroll.post { if (next == Tab.Chat) scroll.fullScroll(View.FOCUS_DOWN) else scroll.scrollTo(0, 0) }
        if (next == Tab.Map && ::mapView.isInitialized) mapView.invalidate()
    }

    private fun bindTabs(event: Event) = tabsSection.apply {
        removeAllViews()
        val available = Tab.values().filter { !it.membersOnly || event.isMember }
        if (tab !in available) tab = Tab.Summary
        availableTabs = available
        markTab = tabs(available.map { getString(it.label) }, available.indexOf(tab)) { showTab(available[it]) }
    }

    /**
     * A ação principal fica presa no rodapé e muda com o seu estado: responder o convite,
     * pagar a sua parte, escolher o que levar ou chamar a galera. Na aba Chat, é o campo de mensagem.
     */
    private fun bindBottom() {
        val event = vm.event.value ?: return setBottom(null)
        if (tab == Tab.Chat) {
            if (chatInput?.isAttachedToWindow != true) setBottom { chatBar() }
            return
        }
        chatInput = null
        if (tab == Tab.Map) return setBottom(null)
        val owe = vm.balance.value?.transfers?.firstOrNull { it.from.uid == myUid && !it.to.pixKey.isNullOrBlank() }
        when {
            event.myStatus == "invited" -> setBottom {
                primaryButton(getString(R.string.status_accept), top = 0) { openContributions() }
                textButton(getString(R.string.status_decline), top = 4) { decline(event) }
            }
            event.myStatus == "declined" -> setBottom { primaryButton(getString(R.string.status_changed_mind), top = 0) { openContributions() } }
            owe != null && tab != Tab.Costs -> setBottom {
                primaryButton(getString(R.string.sticky_pay, Money.format(owe.amountCents)), top = 0, icon = R.drawable.ic_pix_logo) { showPix(owe, owe.to.pixKey!!) }
            }
            event.isMember && event.contributionsOf(myUid).isEmpty() && tab != Tab.Items -> setBottom {
                primaryButton(getString(R.string.hero_action_items), top = 0, icon = R.drawable.ic_list_checks) { openContributions() }
            }
            event.isOrganizer && event.confirmed.isEmpty() -> setBottom {
                primaryButton(getString(R.string.sticky_call_friends), top = 0, icon = R.drawable.ic_share_network) { shareInvite(event) }
            }
            else -> setBottom(null)
        }
    }

    /** Campo de mensagem preso no rodapé da aba Chat; o texto digitado sobrevive às atualizações. */
    private fun LinearLayout.chatBar() {
        val typed = chatInput?.text?.toString().orEmpty()
        val line = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        addView(line)
        val input = line.textField(getString(R.string.details_chat_hint), typed, maxLength = 500, top = 0)
        (input.parent.parent as View).layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        chatInput = input
        line.primaryButton(getString(R.string.cd_send_message), top = 0, icon = R.drawable.ic_paper_plane_right) {
            val text = input.text.toString().trim()
            if (text.isEmpty()) return@primaryButton
            call({ ChurrascoRepository.sendMessage(churrascoId, text) }, silent = true) { input.text?.clear() }
        }.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = context.dp(8) }
    }

    private fun LinearLayout.holder(): LinearLayout =
        LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }.also(::addView)

    // --- Dados -----------------------------------------------------------------------

    private fun bindEvent(event: Event) {
        stopRefreshing()
        setTitleText(if (event.myStatus == "invited") getString(R.string.details_invite_title) else getString(R.string.details_event_title))
        bindHeader(event)
        bindTabs(event)
        bindStatus(event)
        bindAdmin(event)
        bindPeople(event)
        bindItems(event)
        bindCosts(event, vm.balance.value)
        showTab(tab)
        bindSharingState()
        ReminderScheduler.sync(this, listOf(event))

        if (intent.getBooleanExtra(EXTRA_JUST_CREATED, false) && !askedToShare) {
            askedToShare = true
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.created_title)
                .setMessage(R.string.created_body)
                .setNegativeButton(R.string.later, null)
                .setPositiveButton(R.string.invite_share_action) { _, _ -> shareInvite(event) }
                .show()
        }
    }

    private fun whenText(event: Event) = ChurrascoDateUtils.eventDateTime(event.churrascoDate, event.hora)

    private fun bindHeader(event: Event) = headerSection.apply {
        removeAllViews()
        text(getString(R.string.details_heading, event.organizer.displayName), TextRole.Caption).setTextColor(getColor(R.color.brasa_strong))
        text(event.local, TextRole.Title, top = 4)
        text(countdown(event), TextRole.Muted, top = 4)
        bindWhere(event)
    }

    private fun bindWhere(event: Event) = whereSection.apply {
        removeAllViews()
        card(top = 16) {
            row(context.iconBadge(R.drawable.ic_calendar_blank), event.whenLine(), getString(R.string.details_timezone), top = 0)
            row(context.iconBadge(R.drawable.ic_map_pin), event.local, event.address.ifBlank { null })
            val query = event.address.ifBlank { event.local }
            buttonRow(top = 16) {
                secondaryButton(getString(R.string.details_open_maps), top = 0, icon = R.drawable.ic_map_trifold) { openMaps(query) }
                secondaryButton(getString(R.string.details_open_waze), top = 0, icon = R.drawable.ic_car) { openWaze(query) }
            }
        }
    }

    private fun bindStatus(event: Event) = statusSection.apply {
        removeAllViews()
        when (event.myStatus) {
            "invited" -> banner(getString(R.string.status_invited_title), getString(R.string.status_invited_body), Tone.Warning, top = 16)
            "declined" -> banner(getString(R.string.status_declined_title), getString(R.string.status_declined_body), Tone.Error, top = 16)
            "confirmed" -> banner(getString(R.string.status_confirmed_title), getString(R.string.status_confirmed_body), Tone.Success, top = 16).apply {
                secondaryButton(getString(R.string.status_change_items)) { openContributions() }
                textButton(getString(R.string.status_cancel_presence), danger = true) { decline(event) }
            }
            "organizer" -> banner(getString(R.string.status_organizer_title), getString(R.string.status_organizer_body), Tone.Brand, top = 16).apply {
                secondaryButton(getString(R.string.status_organizer_items)) { openContributions() }
            }
            else -> banner(getString(R.string.status_admin_title), null, Tone.Info, top = 16)
        }
    }

    private fun bindAdmin(event: Event) = adminSection.apply {
        removeAllViews()
        if (!event.isMember && !event.canEdit) return@apply
        card(top = 16) {
            if (event.isMember) primaryButton(getString(R.string.invite_share_action), top = 0, icon = R.drawable.ic_share_network) { shareInvite(event) }
            if (event.canEdit) {
                secondaryButton(getString(R.string.details_edit), top = if (event.isMember) 8 else 0) {
                    refreshOnReturn.launch(Intent(this@ActiveChurrascoDetailsActivity, EventFormActivity::class.java).putExtra(EventFormActivity.EXTRA_ID, event.id))
                }
                textButton(getString(R.string.details_cancel_event), danger = true) { cancelEvent() }
            }
        }
    }

    private fun bindPeople(event: Event) = peopleSection.apply {
        removeAllViews()
        val going = 1 + event.confirmed.size
        section(getString(R.string.people_title, going), getString(R.string.people_subtitle, event.pending.size, event.declined.size), top = 24)
        card(top = 12) {
            row(context.avatar(event.organizer.displayName, event.organizer.uid), event.organizer.displayName, getString(R.string.people_organizer), top = 0)
            event.confirmed.forEach { row(context.avatar(it.displayName, it.uid), it.displayName, getString(R.string.people_confirmed)) }
            event.pending.forEach { row(context.avatar(it.displayName, it.uid), it.displayName, getString(R.string.people_pending)) }
            event.declined.forEach { row(context.avatar(it.displayName, it.uid), it.displayName, getString(R.string.people_declined)) }
        }
    }

    private fun bindItems(event: Event) = itemsSection.apply {
        removeAllViews()
        section(getString(R.string.items_who_brings), getString(R.string.items_who_brings_subtitle), top = 24)
        if (event.contributions.isEmpty()) {
            card(top = 12, background = R.drawable.bg_empty_state) {
                text(getString(R.string.items_nobody_yet), TextRole.Muted).gravity = Gravity.CENTER
            }
            return@apply
        }
        event.contributions.groupBy { ItemCatalog.find(it.category)?.name ?: it.category }
            .toSortedMap(compareBy { name -> ItemCatalog.categories.indexOfFirst { it.name == name }.let { if (it < 0) Int.MAX_VALUE else it } })
            .forEach { (category, list) ->
                card(top = 12) {
                    row(context.categoryBadge(ItemCatalog.find(category), 48), ItemCatalog.label(category), top = 0)
                    list.forEach { c ->
                        val label = ItemCatalog.summary(c).ifBlank { if (ItemCatalog.find(c.category)?.name != c.category) c.category else getString(R.string.items_no_details) }
                        row(context.avatar(c.displayName, c.uid, 32), c.displayName, label, top = 12)
                    }
                }
            }
    }

    // --- Contas -------------------------------------------------------------------------

    private fun bindCosts(event: Event?, balance: Balance?) {
        costsSection.removeAllViews()
        if (event == null || !event.isMember) return
        costsSection.apply {
            section(getString(R.string.costs_title), getString(R.string.costs_subtitle), top = 24)
            card(top = 12) {
                if (balance == null || balance.totalCents == 0L) {
                    text(getString(R.string.costs_empty), TextRole.Muted)
                } else {
                    text(Money.format(balance.totalCents), TextRole.Display)
                    text(getString(R.string.costs_total, balance.members, Money.format(balance.shareCents)), TextRole.Caption, top = 4)
                    val mine = balance.transfers.filter { it.from.uid == myUid }
                    val toMe = balance.transfers.filter { it.to.uid == myUid }
                    when {
                        mine.isNotEmpty() -> mine.forEach { transferRow(it, payer = true) }
                        toMe.isNotEmpty() -> toMe.forEach { transferRow(it, payer = false) }
                        else -> text(getString(R.string.costs_you_are_even), TextRole.Strong, top = 16)
                    }
                    val others = balance.transfers.filter { it.from.uid != myUid && it.to.uid != myUid }
                    if (others.isNotEmpty()) {
                        text(getString(R.string.costs_others), TextRole.Caption, top = 16)
                        others.forEach { row(context.avatar(it.from.displayName, it.from.uid, 32), "${it.from.displayName} → ${it.to.displayName}", null, Money.format(it.amountCents), top = 8) }
                    }
                }
                primaryButton(getString(R.string.costs_add)) { addExpense() }
            }
            if (event.expenses.isNotEmpty()) {
                card(top = 12) {
                    text(getString(R.string.costs_expenses_title), TextRole.Strong)
                    event.expenses.forEach { expense ->
                        val canDelete = expense.uid == myUid || event.canEdit
                        row(context.avatar(expense.displayName, expense.uid, 32), expense.description, expense.displayName, Money.format(expense.amountCents), top = 12,
                            onClick = if (canDelete) ({ removeExpense(expense.id, expense.description) }) else null)
                    }
                    text(getString(R.string.costs_delete_hint), TextRole.Caption, top = 8)
                }
            }
        }
    }

    private fun LinearLayout.transferRow(transfer: Transfer, payer: Boolean) {
        if (payer) {
            text(getString(R.string.costs_you_owe, Money.format(transfer.amountCents), transfer.to.displayName), TextRole.Strong, top = 16)
            val key = transfer.to.pixKey
            if (key.isNullOrBlank()) {
                text(getString(R.string.costs_no_pix_key, transfer.to.displayName), TextRole.Caption, top = 4)
            } else {
                primaryButton(getString(R.string.costs_pay_pix), icon = R.drawable.ic_pix_logo) { showPix(transfer, key) }
            }
        } else {
            text(getString(R.string.costs_owes_you, transfer.from.displayName, Money.format(transfer.amountCents)), TextRole.Strong, top = 16)
            if (AuthSession.pixKey(this@ActiveChurrascoDetailsActivity).isNullOrBlank()) {
                text(getString(R.string.costs_add_your_key), TextRole.Caption, top = 4)
            }
        }
    }

    private fun addExpense() {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        val description = form.textField(getString(R.string.costs_description_hint), maxLength = 80)
        val amount = form.textField(getString(R.string.costs_amount_hint)).apply {
            // Teclado numérico, mas aceitando vírgula: é como se digita dinheiro no Brasil.
            keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789,.")
            setRawInputType(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        }
        val dialog = MaterialAlertDialogBuilder(this).setTitle(R.string.costs_add).setView(form)
            .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val cents = Money.parse(amount.text.toString())
                val text = description.text.toString().trim()
                when {
                    text.isBlank() -> description.error = getString(R.string.costs_need_description)
                    cents == null -> amount.error = getString(R.string.costs_need_amount)
                    else -> {
                        dialog.dismiss()
                        call({ ChurrascoRepository.addExpense(churrascoId, cents, text) }) { vm.apply(it); toast(getString(R.string.costs_added)) }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun removeExpense(expenseId: String, description: String) =
        confirm(getString(R.string.costs_remove_title), description, getString(R.string.costs_remove_action), danger = true) {
            call({ ChurrascoRepository.removeExpense(churrascoId, expenseId) }) { vm.apply(it) }
        }

    private fun showPix(transfer: Transfer, key: String) {
        val code = PixPayload.build(key, transfer.to.displayName, transfer.amountCents, txid = "CHURRASCO")
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(16), dp(24), 0)
            addView(ImageView(context).apply { setImageBitmap(qr(code, dp(240))) }, LinearLayout.LayoutParams(dp(240), dp(240)))
            text(getString(R.string.pix_to, transfer.to.displayName, Money.format(transfer.amountCents)), TextRole.Strong, top = 16).gravity = Gravity.CENTER
            text(getString(R.string.pix_key, key), TextRole.Caption, top = 4).gravity = Gravity.CENTER
        }
        MaterialAlertDialogBuilder(this).setTitle(R.string.pix_title).setView(view)
            .setNegativeButton(R.string.close, null)
            .setPositiveButton(R.string.pix_copy) { _, _ ->
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Pix", code))
                toast(getString(R.string.pix_copied))
            }.show()
    }

    private fun qr(content: String, size: Int): Bitmap {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565)
    }

    // --- Mapa --------------------------------------------------------------------------

    private fun windowOpen(event: Event): Boolean {
        val start = event.eventStartsAt ?: return false
        val now = System.currentTimeMillis()
        return now >= start - HOUR && now < start + 4 * HOUR
    }

    private fun bindSharingState() {
        if (!::shareLocationButton.isInitialized) return
        val event = vm.event.value ?: return
        val state = LocationShareService.state.value
        val sharingHere = state.active && state.churrascoId == event.id
        shareLocationButton.text = getString(if (sharingHere) R.string.map_stop else R.string.map_share)
        shareLocationButton.isEnabled = sharingHere || windowOpen(event)
        mapStatus.text = when {
            sharingHere -> getString(R.string.map_sharing_until, SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date(state.expiresAt)))
            state.endedMessage != null && !state.active -> state.endedMessage
            windowOpen(event) -> getString(R.string.map_window_open)
            else -> getString(R.string.map_window_closed)
        }
    }

    private fun bindLocations(locations: List<SharedLocation>) {
        if (!::mapView.isInitialized) return
        mapView.overlays.removeAll { it is Marker }
        locations.forEach { loc ->
            mapView.overlays.add(Marker(mapView).apply {
                position = GeoPoint(loc.latitude, loc.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                // Texto puro: o nome vem de outras pessoas.
                title = loc.displayName
                snippet = seenText(loc.updatedAt)
            })
        }
        when (locations.size) {
            0 -> Unit
            1 -> { mapView.controller.setZoom(15.0); mapView.controller.setCenter(GeoPoint(locations[0].latitude, locations[0].longitude)) }
            else -> mapView.post {
                val box = BoundingBox.fromGeoPointsSafe(locations.map { GeoPoint(it.latitude, it.longitude) })
                mapView.zoomToBoundingBox(box.increaseByScale(1.4f), false)
            }
        }
        mapView.invalidate()
        if (locations.isNotEmpty() && !LocationShareService.state.value.active) {
            mapStatus.text = locations.joinToString("\n") { "${it.displayName} · ${seenText(it.updatedAt)}" }
        }
    }

    private fun seenText(updatedAt: Long): String {
        val minutes = ((System.currentTimeMillis() - updatedAt) / 60_000).toInt()
        return when {
            minutes <= 0 -> getString(R.string.map_seen_now)
            minutes < 60 -> getString(R.string.map_seen_minutes, minutes)
            else -> getString(R.string.map_seen_hours, minutes / 60)
        }
    }

    private fun toggleSharing() {
        val state = LocationShareService.state.value
        if (state.active && state.churrascoId == churrascoId) {
            LocationShareService.stop(this)
            return
        }
        val needed = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val hasLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasLocation) startSharing() else locationPermissions.launch(needed.toTypedArray())
    }

    private fun startSharing() {
        val event = vm.event.value ?: return
        call({ ChurrascoRepository.startLocationSession(churrascoId) }) { session ->
            val sessionId = session.sessionId ?: return@call
            LocationShareService.start(this, churrascoId, sessionId, session.expiresAt ?: return@call, event.local)
        }
    }

    // --- Chat ----------------------------------------------------------------------------

    private fun bindMessages(messages: List<ChatMessage>) {
        if (!::messagesBox.isInitialized) return
        messagesBox.removeAllViews()
        if (messages.isEmpty()) {
            messagesBox.text(getString(R.string.chat_empty), TextRole.Muted)
            return
        }
        val inflater = LayoutInflater.from(this)
        val time = SimpleDateFormat("HH:mm", Locale("pt", "BR"))
        messages.takeLast(100).forEach { message ->
            val view = inflater.inflate(R.layout.item_chat_message, messagesBox, false)
            // v1 não tinha senderUid: compara pelo nome nesse caso.
            val mine = message.senderUid?.let { it == myUid } ?: (message.sender == AuthSession.displayName(this))
            val bubble = view.findViewById<LinearLayout>(R.id.messageBubble)
            (bubble.layoutParams as FrameLayout.LayoutParams).gravity = if (mine) Gravity.END else Gravity.START
            bubble.setBackgroundResource(if (mine) R.drawable.bg_chat_mine else R.drawable.bg_chat_other)
            view.findViewById<TextView>(R.id.txtSender).apply { text = message.sender; visible(!mine) }
            view.findViewById<TextView>(R.id.txtMessage).apply {
                text = message.text
                setTextColor(getColor(if (mine) R.color.md_on_primary else R.color.mainInk))
            }
            view.findViewById<TextView>(R.id.txtTime).apply {
                text = time.format(Date(message.timestamp))
                setTextColor(getColor(if (mine) R.color.md_primary_container else R.color.mainMuted))
            }
            view.setPadding(if (mine) dp(48) else 0, dp(4), if (mine) 0 else dp(48), dp(4))
            messagesBox.addView(view)
        }
    }

    // --- Ações ---------------------------------------------------------------------------

    private fun openContributions() = refreshOnReturn.launch(
        Intent(this, ContributionsActivity::class.java).putExtra(ContributionsActivity.EXTRA_ID, churrascoId)
    )

    private fun decline(event: Event) = confirm(
        getString(R.string.decline_title),
        getString(if (event.isMember) R.string.decline_body_confirmed else R.string.decline_body_invited),
        getString(R.string.decline_action),
        danger = true,
    ) {
        if (LocationShareService.state.value.churrascoId == churrascoId) LocationShareService.stop(this)
        call({ ChurrascoRepository.rsvp(churrascoId, "declined") }) { vm.apply(it); toast(getString(R.string.declined_done)) }
    }

    private fun cancelEvent() = confirm(
        getString(R.string.cancel_event_title), getString(R.string.cancel_event_body), getString(R.string.cancel_event_action), danger = true,
    ) {
        call({ ChurrascoRepository.cancelEvent(churrascoId) }) {
            ReminderScheduler.refresh(this)
            toast(getString(R.string.cancel_event_done))
            startActivity(Intent(this, EventsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        }
    }

    private fun shareInvite(event: Event) = call({ ChurrascoRepository.inviteLink(churrascoId) }) { link ->
        InviteLinkActivity.share(this, link.url, event.local, whenText(event))
    }

    private fun openMaps(query: String) {
        val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query)))
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(query)))
        runCatching { startActivity(geo) }.onFailure { runCatching { startActivity(web) } }
    }

    private fun openWaze(query: String) {
        // Sem o Waze instalado, o link abre a página do Waze, que oferece instalar.
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://waze.com/ul?navigate=yes&q=" + Uri.encode(query)))) }
    }

    private fun showMessage(message: String) =
        MaterialAlertDialogBuilder(this).setMessage(message).setPositiveButton(R.string.ok, null).show()
}
