package com.longynus.churrasco.data

import com.longynus.churrasco.data.model.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Rotas da API v2. Use pelo [ChurrascoRepository], que trata erros e mensagens. */
interface ChurrascoApi {
    @GET("health") suspend fun health(): Response<ApiEnvelope<Any>>

    // Conta
    @POST("users/register") suspend fun register(@Body body: RegisterRequest): Response<ApiEnvelope<Profile>>
    @POST("users/login") suspend fun login(@Body body: LoginRequest): Response<ApiEnvelope<Profile>>
    @POST("users/logout") suspend fun logout(@Body body: LoginRequest): Response<ApiEnvelope<Any>>
    @PATCH("users/me") suspend fun updateProfile(@Body body: ProfileUpdate): Response<ApiEnvelope<Profile>>

    // Amigos
    @GET("friends") suspend fun friends(): Response<ApiEnvelope<FriendsPayload>>
    @POST("friends") suspend fun addFriend(@Body body: FriendRequest): Response<ApiEnvelope<FriendStatus>>
    @POST("friends/{id}/accept") suspend fun acceptFriend(@Path("id") id: String): Response<ApiEnvelope<Any>>
    @DELETE("friends/{id}") suspend fun removeFriend(@Path("id") id: String): Response<ApiEnvelope<Any>>

    // Churrascos
    @GET("churrascos") suspend fun events(@Query("status") status: String): Response<ApiEnvelope<Any>>
    @GET("churrascos/{id}") suspend fun event(@Path("id") id: String): Response<ApiEnvelope<Any>>
    @POST("churrascos") suspend fun createEvent(@Body body: EventRequest): Response<ApiEnvelope<Any>>
    @PATCH("churrascos/{id}") suspend fun updateEvent(@Path("id") id: String, @Body body: EventRequest): Response<ApiEnvelope<Any>>
    @DELETE("churrascos/{id}") suspend fun cancelEvent(@Path("id") id: String): Response<ApiEnvelope<Any>>
    @POST("churrascos/{id}/rsvp") suspend fun rsvp(@Path("id") id: String, @Body body: RsvpRequest): Response<ApiEnvelope<Any>>

    // Convites
    @GET("invites/pending") suspend fun pendingInvites(): Response<ApiEnvelope<Any>>
    @POST("churrascos/{id}/invite-link") suspend fun inviteLink(@Path("id") id: String, @Body body: InviteLinkRequest): Response<ApiEnvelope<InviteLink>>
    @POST("invites/{token}/accept") suspend fun acceptInvite(@Path("token") token: String): Response<ApiEnvelope<Any>>

    // Custos
    @POST("churrascos/{id}/expenses") suspend fun addExpense(@Path("id") id: String, @Body body: ExpenseRequest): Response<ApiEnvelope<Any>>
    @DELETE("churrascos/{id}/expenses/{expenseId}") suspend fun removeExpense(@Path("id") id: String, @Path("expenseId") expenseId: String): Response<ApiEnvelope<Any>>
    @GET("churrascos/{id}/balance") suspend fun balance(@Path("id") id: String): Response<ApiEnvelope<Balance>>

    // Chat e mapa
    @POST("churrascos/{id}/messages") suspend fun sendMessage(@Path("id") id: String, @Body body: ChatMessageRequest): Response<ApiEnvelope<Any>>
    @POST("churrascos/{id}/location-session") suspend fun startLocationSession(@Path("id") id: String): Response<ApiEnvelope<Any>>
    @POST("churrascos/{id}/location") suspend fun shareLocation(@Path("id") id: String, @Body body: LocationUpdateRequest): Response<ApiEnvelope<Any>>
    @DELETE("churrascos/{id}/location") suspend fun stopLocation(@Path("id") id: String): Response<ApiEnvelope<Any>>

    // Contas antigas
    @GET("legacy-link/me") suspend fun legacyStatus(): Response<ApiEnvelope<LegacyLinkStatus>>
    @POST("legacy-link/request") suspend fun requestLegacy(@Body body: LegacyLinkRequest): Response<ApiEnvelope<LegacyLinkRequestInfo>>
    @GET("legacy-link/review") suspend fun legacyReviews(): Response<ApiEnvelope<List<LegacyLinkRequestInfo>>>
    @POST("legacy-link/{id}/approve") suspend fun approveLegacy(@Path("id") id: String, @Body body: LegacyLinkApproval): Response<ApiEnvelope<LegacyLinkRequestInfo>>
    @POST("legacy-link/{id}/reject") suspend fun rejectLegacy(@Path("id") id: String, @Body body: LegacyLinkRejection): Response<ApiEnvelope<LegacyLinkRequestInfo>>
}
