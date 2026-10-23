package com.longynus.churrasco.data

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.longynus.churrasco.BuildConfig
import com.longynus.churrasco.data.model.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Falha de uma chamada, já com a mensagem que o servidor mandou (em português) quando houver. */
class ApiError(
    val status: Int,
    val serverMessage: String?,
    val code: String? = null,
    val isNetwork: Boolean = false,
) : Exception(serverMessage ?: "HTTP $status")

/**
 * Único ponto de acesso à API. Cada função devolve [Result]: sucesso com o dado já
 * desembrulhado ou [ApiError]. Assim as telas não lidam com Retrofit nem callbacks.
 */
object ChurrascoRepository {

    private val gson = Gson()

    val api: ChurrascoApi by lazy {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("X-App-Version", BuildConfig.VERSION_CODE.toString())
                currentIdToken()?.let { request.header("Authorization", "Bearer $it") }
                chain.proceed(request.build())
            }
            // O Render gratuito pode levar ~1 min para acordar se o ping falhar.
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(75, TimeUnit.SECONDS)
            .apply {
                if (BuildConfig.DEBUG) addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
            }
            .build()

        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ChurrascoApi::class.java)
    }

    // Roda na thread do OkHttp; o SDK renova o token sozinho quando expira.
    private fun currentIdToken(): String? {
        val user = FirebaseAuth.getInstance().currentUser ?: return null
        return runCatching { Tasks.await(user.getIdToken(false), 20, TimeUnit.SECONDS).token }.getOrNull()
    }

    private suspend fun <T> envelope(block: suspend ChurrascoApi.() -> Response<ApiEnvelope<T>>): Result<ApiEnvelope<T>> =
        try {
            val response = api.block()
            val body = response.body()
            if (response.isSuccessful && body?.success == true) {
                Result.success(body)
            } else {
                val error = body ?: response.errorBody()?.string()?.let {
                    runCatching { gson.fromJson(it, ApiEnvelope::class.java) }.getOrNull()
                }
                Result.failure(ApiError(response.code(), error?.message, error?.code))
            }
        } catch (e: IOException) {
            Result.failure(ApiError(0, null, isNetwork = true))
        }

    private suspend fun <T> payload(block: suspend ChurrascoApi.() -> Response<ApiEnvelope<T>>): Result<T> =
        envelope(block).mapCatching { it.payload ?: throw ApiError(500, null) }

    private suspend fun event(block: suspend ChurrascoApi.() -> Response<ApiEnvelope<Any>>): Result<Event> =
        envelope(block).mapCatching { it.churrasco ?: throw ApiError(500, null) }

    suspend fun wakeUp() = envelope { health() }

    suspend fun register(name: String) = payload { register(RegisterRequest(name)) }
    suspend fun login(fcmToken: String?) = payload { login(LoginRequest(fcmToken)) }
    suspend fun logout(fcmToken: String) = envelope { logout(LoginRequest(fcmToken)) }
    suspend fun updateProfile(update: ProfileUpdate) = payload { updateProfile(update) }

    suspend fun friends() = payload { friends() }
    suspend fun addFriend(email: String) = payload { addFriend(FriendRequest(email)) }
    suspend fun acceptFriend(id: String) = envelope { acceptFriend(id) }
    suspend fun removeFriend(id: String) = envelope { removeFriend(id) }

    suspend fun events(status: String) = envelope { events(status) }.map { it.churrascos.orEmpty() }
    suspend fun event(id: String) = event { event(id) }
    suspend fun createEvent(request: EventRequest) = envelope { createEvent(request) }.mapCatching { it.id ?: throw ApiError(500, null) }
    suspend fun updateEvent(id: String, request: EventRequest) = event { updateEvent(id, request) }
    suspend fun cancelEvent(id: String) = envelope { cancelEvent(id) }
    suspend fun rsvp(id: String, status: String, contributions: List<ContributionInput> = emptyList()) =
        event { rsvp(id, RsvpRequest(status, contributions)) }

    suspend fun pendingInvites() = envelope { pendingInvites() }.map { it.invites.orEmpty() }
    suspend fun inviteLink(id: String, rotate: Boolean = false) = payload { inviteLink(id, InviteLinkRequest(rotate)) }
    suspend fun acceptInvite(token: String) = event { acceptInvite(token) }

    suspend fun addExpense(id: String, amountCents: Long, description: String) = event { addExpense(id, ExpenseRequest(amountCents, description)) }
    suspend fun removeExpense(id: String, expenseId: String) = event { removeExpense(id, expenseId) }
    suspend fun balance(id: String) = payload { balance(id) }

    suspend fun sendMessage(id: String, text: String) = envelope { sendMessage(id, ChatMessageRequest(text)) }
    suspend fun startLocationSession(id: String) = envelope { startLocationSession(id) }
    suspend fun shareLocation(id: String, request: LocationUpdateRequest) = envelope { shareLocation(id, request) }
    suspend fun stopLocation(id: String) = envelope { stopLocation(id) }

    suspend fun legacyStatus() = payload { legacyStatus() }
    suspend fun requestLegacy(name: String) = payload { requestLegacy(LegacyLinkRequest(name)) }
    suspend fun legacyReviews() = payload { legacyReviews() }
    suspend fun approveLegacy(row: LegacyLinkRequestInfo, evidence: String) =
        payload { approveLegacy(row.id, LegacyLinkApproval(true, evidence, row.username, row.email)) }
    suspend fun rejectLegacy(id: String, reason: String) = payload { rejectLegacy(id, LegacyLinkRejection(reason)) }
}
