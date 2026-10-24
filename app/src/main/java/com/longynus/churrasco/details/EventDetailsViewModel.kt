package com.longynus.churrasco.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.longynus.churrasco.data.ChurrascoRepository
import com.longynus.churrasco.data.model.Balance
import com.longynus.churrasco.data.model.ChatMessage
import com.longynus.churrasco.data.model.Event
import com.longynus.churrasco.data.model.SharedLocation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Estado da tela do churrasco. Sobrevive a girar a tela e é dono dos listeners do
 * Realtime Database (chat e mapa), que saem junto com ele.
 */
class EventDetailsViewModel : ViewModel() {

    private val _event = MutableStateFlow<Event?>(null)
    val event: StateFlow<Event?> = _event.asStateFlow()

    private val _balance = MutableStateFlow<Balance?>(null)
    val balance: StateFlow<Balance?> = _balance.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _locations = MutableStateFlow<List<SharedLocation>>(emptyList())
    val locations: StateFlow<List<SharedLocation>> = _locations.asStateFlow()

    /** Falhas para a tela mostrar (com opção de tentar de novo quando for rede). */
    private val _errors = MutableSharedFlow<Throwable>(extraBufferCapacity = 4)
    val errors = _errors.asSharedFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private var id: String? = null
    private var chatRef: DatabaseReference? = null
    private var chatListener: ChildEventListener? = null
    private var locationsRef: DatabaseReference? = null
    private var locationsListener: ValueEventListener? = null

    fun start(churrascoId: String) {
        if (id == churrascoId) return
        id = churrascoId
        refresh()
    }

    /** Recarrega o churrasco; o Job termina quando a resposta chega (mudando ou não). */
    fun refresh(): Job? {
        val churrascoId = id ?: return null
        return viewModelScope.launch {
            _loading.value = _event.value == null
            ChurrascoRepository.event(churrascoId)
                .onSuccess { setEvent(it) }
                .onFailure { _errors.emit(it) }
            _loading.value = false
        }
    }

    /** Ações que devolvem o evento atualizado (RSVP, despesas...). */
    fun apply(event: Event) = setEvent(event)

    private fun setEvent(event: Event) {
        _event.value = event
        if (event.isMember) {
            attachRealtime(event.id)
            loadBalance()
        } else {
            detachRealtime()
            _balance.value = null
        }
    }

    fun loadBalance() {
        val churrascoId = id ?: return
        viewModelScope.launch { ChurrascoRepository.balance(churrascoId).onSuccess { _balance.value = it } }
    }

    private fun attachRealtime(churrascoId: String) {
        if (chatListener != null) return
        val database = FirebaseDatabase.getInstance()

        val messagesRef = database.getReference("churrascos/$churrascoId/messages")
        chatRef = messagesRef
        chatListener = messagesRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                snapshot.getValue(ChatMessage::class.java)?.let { _messages.value = _messages.value + it }
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onChildRemoved(snapshot: DataSnapshot) = Unit
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onCancelled(error: DatabaseError) = Unit
        })

        val sharedRef = database.getReference("churrascos/$churrascoId/locations")
        locationsRef = sharedRef
        locationsListener = sharedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val now = System.currentTimeMillis()
                // Sessão recém-aberta ainda não tem coordenadas (updatedAt = 0).
                _locations.value = snapshot.children
                    .mapNotNull { it.getValue(SharedLocation::class.java) }
                    .filter { it.expiresAt > now && it.updatedAt > 0 }
                    .sortedByDescending { it.updatedAt }
            }
            override fun onCancelled(error: DatabaseError) = Unit
        })
    }

    private fun detachRealtime() {
        chatListener?.let { chatRef?.removeEventListener(it) }
        locationsListener?.let { locationsRef?.removeEventListener(it) }
        chatListener = null
        locationsListener = null
        _messages.value = emptyList()
        _locations.value = emptyList()
    }

    override fun onCleared() {
        detachRealtime()
        super.onCleared()
    }
}
