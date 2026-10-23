package com.longynus.churrasco

import com.longynus.churrasco.model.*
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @POST("churrascos")
    fun createChurrasco(
        @Body request: CreateChurrascoRequest
    ): Call<CreateChurrascoResponse>

    @GET("churrascos")
    fun listChurrascos(
        @Query("status") status: String
    ): Call<ApiResponse<Churrasco>>

    @GET("churrascos/{id}")
    fun getChurrasco(
        @Path("id") churrascoId: String
    ): Call<ApiResponse<Churrasco>>

    @GET("/users")
    fun getAllUsers(): Call<ApiResponse<List<Usuario>>>

    @POST("churrascos/{id}/confirm-presenca")
    fun confirmPresenca(
        @Path("id") churrascoId: String,
        @Body request: PresencaRequest
    ): Call<ApiResponse<Any>>

    @POST("churrascos/{id}/decline-presenca")
    fun declinePresenca(
        @Path("id") churrascoId: String,
        @Body request: DeclineRequest
    ): Call<ApiResponse<Any>>

    @DELETE("churrascos/{id}")
    fun deleteChurrasco(
        @Path("id") churrascoId: String
    ): Call<ApiResponse<Any>>

    @POST("churrascos/{id}/messages")
    fun sendChatMessage(
        @Path("id") churrascoId: String,
        @Body request: ChatMessageRequest
    ): Call<ApiResponse<Any>>

    @POST("churrascos/{id}/location-session")
    fun startLocationSession(
        @Path("id") churrascoId: String
    ): Call<LocationSessionResponse>

    @POST("churrascos/{id}/location")
    fun shareLocation(
        @Path("id") churrascoId: String,
        @Body request: LocationUpdateRequest
    ): Call<ApiResponse<Any>>

    @DELETE("churrascos/{id}/location")
    fun stopLocationSharing(
        @Path("id") churrascoId: String
    ): Call<ApiResponse<Any>>

    @GET("users/{userName}/invites")
    fun getPendingInvites(
        @Path("userName") userName: String
    ): Call<ApiResponse<Churrasco>>

    @POST("/users/register")
    fun registerUser(
        @Body request: RegisterRequest
    ): Call<ApiResponse<Usuario>>

    @POST("/users/login")
    fun login(
        @Body request: LoginRequest
    ): Call<ApiResponse<Usuario>>

    @POST("/users/logout")
    fun logout(
        @Body request: LoginRequest
    ): Call<ApiResponse<Any>>

    @GET("/legacy-link/me")
    fun legacyLinkStatus(): Call<ApiResponse<LegacyLinkStatus>>

    @POST("/legacy-link/request")
    fun requestLegacyLink(
        @Body request: LegacyLinkRequest
    ): Call<ApiResponse<LegacyLinkRequestInfo>>
}
