package com.longynus.churrasco

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ChildEventListener
import com.longynus.churrasco.adapter.ChatAdapter
import com.longynus.churrasco.model.ChatMessageRequest
import com.longynus.churrasco.model.Churrasco
import com.longynus.churrasco.model.LocationSessionResponse
import com.longynus.churrasco.model.LocationUpdateRequest
import com.longynus.churrasco.model.Message
import com.longynus.churrasco.model.SharedLocation
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class ActiveChurrascoDetailsActivity : AppCompatActivity() {

    private lateinit var churrascoId: String
    private lateinit var userName: String
    private lateinit var rootLayout: View
    private lateinit var btnCancelChurrasco: Button
    private lateinit var creatorActionsContainer: LinearLayout
    private lateinit var txtUserRole: TextView
    private lateinit var tvStatusDescription: TextView
    private lateinit var statusCard: LinearLayout
    private lateinit var btnShareLocation: Button
    private lateinit var txtLocationStatus: TextView
    private lateinit var containerLocations: LinearLayout
    private lateinit var locationMap: WebView

    private var currentChurrasco: Churrasco? = null
    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null
    private var isSharingLocation = false
    private var locationSessionId: String? = null
    private val sessionExpiryHandler = Handler(Looper.getMainLooper())
    private val sessionExpiry = Runnable { endLocalSharing("O tempo de compartilhamento acabou.") }
    private var mapReady = false
    private var lastLocations: List<SharedLocation> = emptyList()

    // O Realtime Database só libera leitura para quem participa (regra eventAccess),
    // então os listeners entram depois de confirmar o papel da pessoa no evento.
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var rvChat: RecyclerView
    private lateinit var txtChatEmpty: TextView
    private var chatRef: DatabaseReference? = null
    private var chatListener: ChildEventListener? = null
    private var locationsRef: DatabaseReference? = null
    private var locationsListener: ValueEventListener? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (granted) {
            startLocationSharing()
        } else {
            rootLayout.showErrorDialog("Para aparecer no mapa, permita o acesso à sua localização.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_active_churrasco_details)
        TopBarHelper.setup(this, getString(R.string.details_event_title))
        BottomNavHelper.setup(this, R.id.nav_active)
        KeyboardInsetsHelper.setup(this, hideBottomNavigation = true)

        rootLayout = findViewById(R.id.rootLayout)
        creatorActionsContainer = findViewById(R.id.creatorActionsContainer)
        txtUserRole = findViewById(R.id.txtUserRole)
        tvStatusDescription = findViewById(R.id.tvStatusDescription)
        statusCard = findViewById(R.id.statusCard)
        btnShareLocation = findViewById(R.id.btnShareLocation)
        txtLocationStatus = findViewById(R.id.txtLocationStatus)
        containerLocations = findViewById(R.id.containerLocations)
        locationMap = findViewById(R.id.locationMap)

        churrascoId = intent.getStringExtra("churrascoId") ?: run {
            finish()
            return
        }

        userName = AuthSession.userName(this)

        btnCancelChurrasco = findViewById<Button>(R.id.btnCancelChurrasco).apply {
            setOnClickListener { deleteChurrasco() }
        }
        findViewById<Button>(R.id.btnJumpToChat).setOnClickListener {
            scrollToChatSection()
        }
        btnShareLocation.setOnClickListener {
            if (isSharingLocation) stopLocationSharing() else requestLocationSharing()
        }

        setupLocationMap()
        setupChat()
        fetchChurrascoDetails()
    }

    private fun setupChat() {
        val scrollView = findViewById<ScrollView>(R.id.detailsScrollView)
        rvChat = findViewById(R.id.rvChat)
        txtChatEmpty = findViewById(R.id.txtChatEmpty)
        val edtMessage = findViewById<EditText>(R.id.edtMessage)
        val btnSend = findViewById<Button>(R.id.btnSend)
        chatAdapter = ChatAdapter(userName)

        rvChat.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        rvChat.adapter = chatAdapter
        txtChatEmpty.visibility = View.VISIBLE

        edtMessage.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) scrollToChatInput(scrollView)
        }
        edtMessage.setOnClickListener {
            scrollToChatInput(scrollView)
        }

        btnSend.setOnClickListener {
            val text = edtMessage.text.toString().trim()
            if (text.isEmpty()) {
                rootLayout.showSnackbar("Digite uma mensagem antes de enviar.")
                return@setOnClickListener
            }

            sendChatMessage(text, btnSend, edtMessage)
        }
    }

    private fun sendChatMessage(text: String, btnSend: Button, edtMessage: EditText) {
        btnSend.isEnabled = false
        RetrofitClient.instance
            .sendChatMessage(churrascoId, ChatMessageRequest(text))
            .enqueue(object : Callback<ApiResponse<Any>> {
                override fun onResponse(
                    call: Call<ApiResponse<Any>>,
                    response: Response<ApiResponse<Any>>
                ) {
                    btnSend.isEnabled = true
                    if (response.isSuccessful && response.body()?.success == true) {
                        edtMessage.text.clear()
                        rootLayout.showSnackbar("Mensagem enviada.")
                    } else {
                        rootLayout.showErrorDialog(
                            response.apiMessage("Não conseguimos enviar a mensagem agora. Tente de novo.")
                        )
                    }
                }

                override fun onFailure(call: Call<ApiResponse<Any>>, t: Throwable) {
                    btnSend.isEnabled = true
                    rootLayout.showErrorDialog(
                        "Nao conseguimos enviar a mensagem agora. Confira sua internet e tente de novo."
                    )
                }
            })
    }

    private fun attachRealtimeListeners() {
        if (chatListener != null) return
        val database = FirebaseDatabase.getInstance()

        val messagesRef = database.getReference("churrascos/$churrascoId/messages")
        chatRef = messagesRef
        chatListener = messagesRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                snapshot.getValue(Message::class.java)?.let { msg ->
                    chatAdapter.addMessage(msg)
                    txtChatEmpty.visibility = if (chatAdapter.isEmpty) View.VISIBLE else View.GONE
                    rvChat.post {
                        rvChat.smoothScrollToPosition(chatAdapter.itemCount - 1)
                    }
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}

            override fun onCancelled(error: DatabaseError) {
                txtChatEmpty.visibility = View.VISIBLE
                txtChatEmpty.text = "Não conseguimos carregar a conversa agora. Abra o churrasco de novo em instantes."
            }
        })

        val sharedRef = database.getReference("churrascos/$churrascoId/locations")
        locationsRef = sharedRef
        locationsListener = sharedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val now = System.currentTimeMillis()
                // Sessões recém-abertas ainda não têm coordenadas (updatedAt = 0).
                val locations = snapshot.children
                    .mapNotNull { it.getValue(SharedLocation::class.java) }
                    .filter { it.expiresAt > now && it.updatedAt > 0 }
                    .sortedByDescending { it.updatedAt }

                lastLocations = locations
                bindSharedLocations(locations)
                renderLocationsOnMap(locations)
            }

            override fun onCancelled(error: DatabaseError) {
                txtLocationStatus.text = "Não conseguimos carregar o mapa agora."
            }
        })
    }

    private fun detachRealtimeListeners() {
        chatListener?.let { chatRef?.removeEventListener(it) }
        locationsListener?.let { locationsRef?.removeEventListener(it) }
        chatListener = null
        locationsListener = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupLocationMap() {
        locationMap.settings.javaScriptEnabled = true
        locationMap.settings.domStorageEnabled = true
        locationMap.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                mapReady = true
                renderLocationsOnMap(lastLocations)
            }
        }
        locationMap.loadDataWithBaseURL(
            "https://unpkg.com/",
            mapHtml(),
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun requestLocationSharing() {
        val churrasco = currentChurrasco
        if (churrasco != null && !locationSharingWindowIsOpen(churrasco)) {
            rootLayout.showErrorDialog("O mapa libera 1 hora antes do churrasco.")
            return
        }

        val hasFine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            startLocationSharing()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun startLocationSharing() {
        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER
        ).filter { provider ->
            runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
        }

        if (providers.isEmpty()) {
            rootLayout.showErrorDialog("Ative a localização do celular para aparecer no mapa.")
            return
        }

        // O servidor abre a sessão (e o prazo dela) antes de aceitar coordenadas.
        btnShareLocation.isEnabled = false
        RetrofitClient.instance
            .startLocationSession(churrascoId)
            .enqueue(object : Callback<LocationSessionResponse> {
                override fun onResponse(
                    call: Call<LocationSessionResponse>,
                    response: Response<LocationSessionResponse>
                ) {
                    btnShareLocation.isEnabled = true
                    val session = response.body()
                    val sessionId = session?.sessionId
                    if (!response.isSuccessful || session?.success != true || sessionId == null) {
                        rootLayout.showErrorDialog(
                            response.apiMessage("Não conseguimos iniciar o compartilhamento agora.")
                        )
                        return
                    }
                    if (isFinishing || isDestroyed) return
                    startLocationUpdates(manager, providers, sessionId, session.expiresAt)
                }

                override fun onFailure(call: Call<LocationSessionResponse>, t: Throwable) {
                    btnShareLocation.isEnabled = true
                    rootLayout.showErrorDialog(
                        "Não conseguimos iniciar o compartilhamento. Confira sua internet e tente de novo."
                    )
                }
            })
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates(
        manager: LocationManager,
        providers: List<String>,
        sessionId: String,
        expiresAt: Long
    ) {
        locationManager = manager
        locationSessionId = sessionId

        val listener = LocationListener { location ->
            sendLocation(location)
        }
        locationListener = listener
        isSharingLocation = true
        btnShareLocation.text = "Parar compartilhamento"
        txtLocationStatus.text =
            "Compartilhando sua localização até ${timeText(expiresAt)}. Mantenha o app aberto para o mapa continuar atualizando."

        sessionExpiryHandler.removeCallbacks(sessionExpiry)
        val remaining = expiresAt - System.currentTimeMillis()
        if (remaining > 0) sessionExpiryHandler.postDelayed(sessionExpiry, remaining)

        providers.forEach { provider ->
            manager.requestLocationUpdates(provider, 30_000L, 20f, listener)
            manager.getLastKnownLocation(provider)?.let { sendLocation(it) }
        }
    }

    /** Para o GPS deste aparelho; [message] explica o porquê na tela. */
    private fun endLocalSharing(message: String) {
        locationListener?.let { listener ->
            locationManager?.removeUpdates(listener)
        }
        sessionExpiryHandler.removeCallbacks(sessionExpiry)
        locationListener = null
        locationSessionId = null
        isSharingLocation = false
        btnShareLocation.text = "Estou a caminho"
        txtLocationStatus.text = message
    }

    private fun stopLocationSharing() {
        endLocalSharing("Compartilhamento encerrado.")

        RetrofitClient.instance
            .stopLocationSharing(churrascoId)
            .enqueue(object : Callback<ApiResponse<Any>> {
                override fun onResponse(
                    call: Call<ApiResponse<Any>>,
                    response: Response<ApiResponse<Any>>
                ) = Unit

                override fun onFailure(call: Call<ApiResponse<Any>>, t: Throwable) {
                    rootLayout.showSnackbar("Nao conseguimos encerrar no servidor agora.")
                }
            })
    }

    private fun sendLocation(location: Location) {
        val sessionId = locationSessionId ?: return
        RetrofitClient.instance
            .shareLocation(
                churrascoId,
                LocationUpdateRequest(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    sessionId = sessionId
                )
            )
            .enqueue(object : Callback<ApiResponse<Any>> {
                override fun onResponse(
                    call: Call<ApiResponse<Any>>,
                    response: Response<ApiResponse<Any>>
                ) {
                    if (response.isSuccessful && response.body()?.success == true) return
                    val message = response.apiMessage("Não conseguimos atualizar sua localização.")
                    // 410: a sessão acabou ou foi encerrada em outro aparelho; 403: a janela do evento fechou.
                    if (response.code() == 410 || response.code() == 403) {
                        if (locationSessionId == sessionId) endLocalSharing(message)
                    } else {
                        txtLocationStatus.text = message
                    }
                }

                override fun onFailure(call: Call<ApiResponse<Any>>, t: Throwable) {
                    txtLocationStatus.text = "Sem conexão para atualizar sua localização."
                }
            })
    }

    private fun updateLocationAvailability(churrasco: Churrasco, canParticipate: Boolean) {
        val isOpen = locationSharingWindowIsOpen(churrasco)
        btnShareLocation.isEnabled = canParticipate && isOpen
        if (isSharingLocation) return

        txtLocationStatus.text = when {
            !canParticipate -> "Confirme presença antes de aparecer no mapa."
            isOpen -> "Toque em Estou a caminho para compartilhar sua localização por até 2 horas."
            else -> "O mapa fica disponível 1 hora antes do churrasco."
        }
    }

    private fun bindSharedLocations(locations: List<SharedLocation>) {
        containerLocations.removeAllViews()
        if (locations.isEmpty()) {
            containerLocations.addView(TextView(this).apply {
                text = "Ninguém compartilhou localização ainda."
                setTextColor(getColor(R.color.mainMuted))
                setPadding(14.dp(), 10.dp(), 14.dp(), 10.dp())
                textSize = 15f
            })
            return
        }

        locations.forEach { location ->
            containerLocations.addView(TextView(this).apply {
                val name = location.displayName.ifBlank { location.username }
                text = "$name - ${seenText(location.updatedAt)}"
                setTextColor(getColor(R.color.mainInk))
                background = getDrawable(R.drawable.bg_create_chip)
                setPadding(14.dp(), 10.dp(), 14.dp(), 10.dp())
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 8.dp()
                }
            })
        }
    }

    private fun renderLocationsOnMap(locations: List<SharedLocation>) {
        if (!mapReady) return

        val payload = JSONArray()
        locations.forEach { location ->
            payload.put(
                JSONObject().apply {
                    put("name", location.displayName.ifBlank { location.username })
                    put("lat", location.latitude)
                    put("lng", location.longitude)
                    put("seen", seenText(location.updatedAt))
                }
            )
        }

        locationMap.evaluateJavascript(
            "window.updateChurrascoLocations(${payload});",
            null
        )
    }

    private fun locationSharingWindowIsOpen(churrasco: Churrasco): Boolean {
        // Mesma referência do servidor, mesmo com o celular em outro fuso.
        val eventTime = churrasco.eventStartsAt ?: ChurrascoDateUtils.eventDateTimeMillis(
            churrasco.churrascoDate,
            churrasco.hora
        ) ?: return true

        val now = System.currentTimeMillis()
        val opensAt = eventTime - 60 * 60 * 1000
        val closesAt = eventTime + 4 * 60 * 60 * 1000

        return now in opensAt..closesAt
    }

    private fun timeText(millis: Long): String =
        SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date(millis))

    private fun seenText(updatedAt: Long): String {
        val minutes = ((System.currentTimeMillis() - updatedAt) / 60_000.0).roundToInt()
        return when {
            minutes <= 0 -> "visto agora"
            minutes == 1 -> "visto há 1 min"
            minutes < 60 -> "visto há $minutes min"
            else -> "visto há ${minutes / 60} h"
        }
    }

    private fun mapHtml(): String =
        """
        <!doctype html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
          <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
          <style>
            html, body, #map { height: 100%; width: 100%; margin: 0; background: #FFF8F3; }
            .leaflet-popup-content { font-family: sans-serif; }
          </style>
        </head>
        <body>
          <div id="map"></div>
          <script>
            const map = L.map('map', { zoomControl: true }).setView([-14.2350, -51.9253], 4);
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
              maxZoom: 19,
              attribution: '&copy; OpenStreetMap'
            }).addTo(map);
            let markers = [];
            window.updateChurrascoLocations = function(locations) {
              markers.forEach(marker => map.removeLayer(marker));
              markers = [];
              if (!locations || !locations.length) {
                map.setView([-14.2350, -51.9253], 4);
                return;
              }
              const bounds = [];
              locations.forEach(item => {
                // Nomes vêm de outras pessoas: texto puro, nunca HTML.
                const popup = document.createElement('div');
                const name = document.createElement('b');
                name.textContent = item.name;
                popup.appendChild(name);
                popup.appendChild(document.createElement('br'));
                popup.appendChild(document.createTextNode(item.seen));
                const marker = L.marker([item.lat, item.lng])
                  .addTo(map)
                  .bindPopup(popup);
                markers.push(marker);
                bounds.push([item.lat, item.lng]);
              });
              if (bounds.length === 1) {
                map.setView(bounds[0], 15);
              } else {
                map.fitBounds(bounds, { padding: [24, 24] });
              }
            };
          </script>
        </body>
        </html>
        """.trimIndent()

    private fun scrollToChatInput(scrollView: ScrollView) {
        scrollView.postDelayed({
            scrollView.smoothScrollTo(0, scrollView.getChildAt(0).bottom)
        }, 250)
    }

    private fun scrollToChatSection() {
        val scrollView = findViewById<ScrollView>(R.id.detailsScrollView)
        val chatSection = findViewById<View>(R.id.chatSection)
        scrollView.post {
            scrollView.smoothScrollTo(0, chatSection.top)
        }
    }

    private fun deleteChurrasco() {
        rootLayout.showLoading()
        RetrofitClient.instance
            .deleteChurrasco(churrascoId)
            .enqueue(object : Callback<ApiResponse<Any>> {
                override fun onResponse(
                    call: Call<ApiResponse<Any>>,
                    response: Response<ApiResponse<Any>>
                ) {
                    rootLayout.hideLoading()
                    if (response.isSuccessful && response.body()?.success == true) {
                        rootLayout.showSnackbar("Churrasco cancelado.")
                        startActivity(
                            Intent(this@ActiveChurrascoDetailsActivity, ActiveChurrascosActivity::class.java)
                                .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
                        )
                    } else {
                        rootLayout.showErrorDialog(
                            "Nao conseguimos cancelar o churrasco agora. Tente novamente."
                        )
                    }
                }

                override fun onFailure(call: Call<ApiResponse<Any>>, t: Throwable) {
                    rootLayout.hideLoading()
                    rootLayout.showErrorDialog(
                        "Nao conseguimos cancelar o churrasco agora. Confira sua internet e tente de novo."
                    )
                }
            })
    }

    private fun fetchChurrascoDetails() {
        rootLayout.showLoading()
        RetrofitClient.instance
            .getChurrasco(churrascoId)
            .enqueue(object : Callback<ApiResponse<Churrasco>> {
                override fun onResponse(
                    call: Call<ApiResponse<Churrasco>>,
                    response: Response<ApiResponse<Churrasco>>
                ) {
                    rootLayout.hideLoading()
                    val body = response.body()
                    if (response.isSuccessful && body?.success == true && body.churrasco != null) {
                        populateDetails(body.churrasco)
                    } else {
                        rootLayout.showErrorDialog(
                            body?.message ?: "Nao conseguimos carregar os detalhes."
                        )
                    }
                }

                override fun onFailure(call: Call<ApiResponse<Churrasco>>, t: Throwable) {
                    rootLayout.hideLoading()
                    rootLayout.showErrorDialog(
                        "Nao conseguimos carregar os detalhes agora. Confira sua internet e tente de novo."
                    )
                }
            })
    }

    private fun populateDetails(churrasco: Churrasco) {
        currentChurrasco = churrasco
        val isCreator = churrasco.createdBy == userName
        val isConfirmed = churrasco.guestsConfirmed.any { it.name == userName }
        val canUseLocation = isCreator || isConfirmed

        creatorActionsContainer.visibility = if (isCreator) View.VISIBLE else View.GONE
        if (isCreator) {
            statusCard.setBackgroundResource(R.drawable.bg_status_info)
            txtUserRole.text = "Voce e o organizador"
            tvStatusDescription.text = "Acompanhe quem vai, quem leva cada item e converse com o grupo."
        } else if (isConfirmed) {
            statusCard.setBackgroundResource(R.drawable.bg_status_success)
            txtUserRole.text = "Você confirmou presença"
            tvStatusDescription.text = "Confira os combinados e use o chat para alinhar detalhes."
        } else {
            statusCard.setBackgroundResource(R.drawable.bg_status_warning)
            txtUserRole.text = "Aguardando resposta"
            tvStatusDescription.text = "Responda ao convite antes de participar da conversa."
        }

        updateLocationAvailability(churrasco, canUseLocation)
        if (canUseLocation) {
            attachRealtimeListeners()
        } else {
            txtChatEmpty.text = "Confirme presença para ver e participar da conversa."
        }

        findViewById<TextView>(R.id.txtDetalhesEvento).text = buildString {
            append("Criado por: ${churrasco.createdBy}\n")
            append("Quando: ${ChurrascoDateUtils.eventDateTime(churrasco.churrascoDate, churrasco.hora)}\n")
            append("Onde: ${churrasco.local}")
        }

        bindSimpleList(
            findViewById(R.id.containerProvided),
            buildProvidedByCreatorRows(churrasco),
            "Nenhum item garantido pelo organizador."
        )

        bindSimpleList(
            findViewById(R.id.containerConfirmed),
            churrasco.guestsConfirmed.map { it.name },
            "Ninguém confirmou ainda."
        )

        bindSimpleList(
            findViewById(R.id.containerAssignedItems),
            buildAssignedItemRows(churrasco),
            "Ninguém assumiu itens ainda."
        )

        bindSimpleList(
            findViewById(R.id.containerMissingItems),
            buildMissingItems(churrasco),
            "Tudo certo por enquanto."
        )

        bindSimpleList(
            findViewById(R.id.containerDeclined),
            churrasco.guestsDeclined,
            "Nenhuma recusa."
        )
    }

    private fun bindSimpleList(container: LinearLayout, items: List<String>, emptyMessage: String) {
        container.removeAllViews()
        if (items.isEmpty()) {
            container.addView(TextView(this).apply {
                text = emptyMessage
                setTextColor(getColor(R.color.mainMuted))
                setPadding(14.dp(), 10.dp(), 14.dp(), 10.dp())
                textSize = 15f
            })
            return
        }

        items.forEach { item ->
            container.addView(TextView(this).apply {
                text = item
                setTextColor(getColor(R.color.mainInk))
                background = getDrawable(R.drawable.bg_create_chip)
                setPadding(14.dp(), 10.dp(), 14.dp(), 10.dp())
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 8.dp()
                }
            })
        }
    }

    private fun buildAssignedItemRows(churrasco: Churrasco): List<String> {
        return churrasco.guestsConfirmed.flatMap { guest ->
            guest.items.map { item -> "$item - ${guest.name}" }
        }
    }

    private fun buildProvidedByCreatorRows(churrasco: Churrasco): List<String> {
        val assignedItems = churrasco.guestsConfirmed
            .flatMap { it.items }
            .toSet()
        return churrasco.fornecidosAgregados.filter { it !in assignedItems }
    }

    private fun buildMissingItems(churrasco: Churrasco): List<String> {
        val provided = churrasco.fornecidosAgregados.toSet()
        return ItemConstants.listaCompletaDeItens.filter { it !in provided }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        locationListener?.let { listener ->
            locationManager?.removeUpdates(listener)
        }
        sessionExpiryHandler.removeCallbacks(sessionExpiry)
        detachRealtimeListeners()
        super.onDestroy()
    }
}
