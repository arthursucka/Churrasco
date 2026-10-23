package com.longynus.churrasco.data.model

// Formato da API v2 (fcm-server/lib/app.js). Todos os campos têm valor padrão para o
// Gson usar o construtor sem argumentos e nunca deixar null onde o Kotlin não espera.

data class ApiEnvelope<T>(
    val success: Boolean = false,
    val message: String? = null,
    val code: String? = null,
    val id: String? = null,
    val payload: T? = null,
    val churrasco: Event? = null,
    val churrascos: List<Event>? = null,
    val invites: List<Event>? = null,
    val sessionId: String? = null,
    val expiresAt: Long? = null,
)

data class Profile(
    val uid: String = "",
    val username: String = "",
    val displayName: String = "",
    val email: String? = null,
    val pixKey: String? = null,
)

data class Person(val uid: String? = null, val displayName: String = "")

data class Participant(val uid: String? = null, val displayName: String = "", val status: String = "invited")

data class Contribution(
    val id: String = "",
    val uid: String? = null,
    val displayName: String = "",
    val category: String = "",
    val details: Map<String, String> = emptyMap(),
    val note: String = "",
)

data class Expense(
    val id: String = "",
    val uid: String? = null,
    val displayName: String = "",
    val amountCents: Long = 0,
    val description: String = "",
)

data class Event(
    val id: String = "",
    val churrascoDate: String = "",
    val hora: String = "",
    val local: String = "",
    val address: String = "",
    val timeZone: String = "America/Sao_Paulo",
    val eventStartsAt: Long? = null,
    val organizer: Person = Person(),
    val participants: List<Participant> = emptyList(),
    val contributions: List<Contribution> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    /** organizer | invited | confirmed | declined | null (admin vendo de fora) */
    val myStatus: String? = null,
    val canEdit: Boolean = false,
) {
    val isOrganizer get() = myStatus == "organizer"
    val isMember get() = myStatus == "organizer" || myStatus == "confirmed"
    val confirmed get() = participants.filter { it.status == "confirmed" }
    val declined get() = participants.filter { it.status == "declined" }
    val pending get() = participants.filter { it.status == "invited" }
    fun contributionsOf(uid: String?) = contributions.filter { it.uid != null && it.uid == uid }
}

data class Friend(val id: String = "", val uid: String? = null, val displayName: String = "", val email: String? = null)

data class FriendsPayload(
    val friends: List<Friend> = emptyList(),
    val incoming: List<Friend> = emptyList(),
    val outgoing: List<Friend> = emptyList(),
)

data class FriendStatus(val status: String = "")

data class PersonPix(val uid: String? = null, val displayName: String = "", val pixKey: String? = null)

data class BalanceRow(
    val uid: String? = null,
    val displayName: String = "",
    val pixKey: String? = null,
    val paidCents: Long = 0,
    val netCents: Long = 0,
)

data class Transfer(val from: PersonPix = PersonPix(), val to: PersonPix = PersonPix(), val amountCents: Long = 0)

data class Balance(
    val totalCents: Long = 0,
    val shareCents: Long = 0,
    val members: Int = 0,
    val balances: List<BalanceRow> = emptyList(),
    val transfers: List<Transfer> = emptyList(),
)

data class InviteLink(val token: String = "", val url: String = "")

data class LegacyLinkRequestInfo(
    val id: String = "",
    val username: String = "",
    val email: String = "",
    val status: String = "",
    val reason: String? = null,
)

data class LegacyLinkStatus(
    val request: LegacyLinkRequestInfo? = null,
    val canReview: Boolean = false,
    val requestsOpen: Boolean = false,
)

// --- Corpos das requisições --------------------------------------------------

data class RegisterRequest(val displayName: String)
data class LoginRequest(val fcmToken: String?)
data class ProfileUpdate(val displayName: String? = null, val pixKey: String? = null)
data class FriendRequest(val email: String)

data class ContributionInput(
    val id: String? = null,
    val category: String,
    val details: Map<String, String> = emptyMap(),
    val note: String = "",
)

data class EventRequest(
    val churrascoDate: String,
    val hora: String,
    val local: String,
    val address: String,
    val invitedUids: List<String>? = null,
    val contributions: List<ContributionInput>? = null,
)

data class RsvpRequest(val status: String, val contributions: List<ContributionInput> = emptyList())
data class ExpenseRequest(val amountCents: Long, val description: String)
data class ChatMessageRequest(val text: String)
data class LocationUpdateRequest(val latitude: Double, val longitude: Double, val sessionId: String)
data class InviteLinkRequest(val rotate: Boolean = false)
data class LegacyLinkRequest(val username: String)
data class LegacyLinkApproval(
    val confirmedOwner: Boolean,
    val evidenceReference: String,
    val expectedUsername: String,
    val expectedEmail: String,
)
data class LegacyLinkRejection(val reason: String)

// --- Realtime Database (lidos direto pelo app) --------------------------------

data class ChatMessage(
    val senderUid: String? = null,
    val sender: String = "",
    val text: String = "",
    val timestamp: Long = 0L,
)

data class SharedLocation(
    val uid: String? = null,
    val displayName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val updatedAt: Long = 0L,
    val expiresAt: Long = 0L,
)
