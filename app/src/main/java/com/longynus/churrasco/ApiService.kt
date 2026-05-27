package com.longynus.churrasco

import com.longynus.churrasco.model.*
import retrofit2.Call
import retrofit2.http.*

interface ApiService {

    /**
     * Cria um novo churrasco.
     * @param request modelo contendo data, hora, local, itens fornecidos, nome do criador e lista de convidados.
     */
    @POST("churrascos")
    fun createChurrasco(
        @Body request: CreateChurrascoRequest
    ): Call<CreateChurrascoResponse>

    /**
     * Lista churrascos por status ("active" ou "past").
     */
    @GET("churrascos")
    fun listChurrascos(
        @Query("status") status: String
    ): Call<ApiResponse<Churrasco>>

    /**
     * Busca detalhes de um churrasco específico.
     */
    @GET("churrascos/{id}")
    fun getChurrasco(
        @Path("id") churrascoId: String
    ): Call<ApiResponse<Churrasco>>

    @GET("/users")
    fun getAllUsers(): Call<ApiResponse<List<Usuario>>>

    /**
     * Confirma presença em um churrasco, enviando nome e itens que o usuário levará.
     */
    @POST("churrascos/{id}/confirm-presenca")
    fun confirmPresenca(
        @Path("id") churrascoId: String,
        @Body request: PresencaRequest
    ): Call<ApiResponse<Any>>

    /**
     * Recusa presença em um churrasco, enviando apenas o nome do usuário.
     */
    @POST("churrascos/{id}/decline-presenca")
    fun declinePresenca(
        @Path("id") churrascoId: String,
        @Body request: DeclineRequest
    ): Call<ApiResponse<Any>>

    /**
     * Cancela (deleta) um churrasco.
     */
    @DELETE("churrascos/{id}")
    fun deleteChurrasco(
        @Path("id") churrascoId: String
    ): Call<ApiResponse<Any>>

    /**
     * Envia uma notificação via seu backend para o FCM.
     */
    @POST("send-notification")
    fun sendNotification(
        @Body payload: NotificationPayload
    ): Call<ApiResponse<Any>>

    /**
     * Busca convites pendentes para o usuário.
     * **Observação:** esse endpoint deve existir no seu backend.
     */
    @GET("users/{userName}/invites")
    fun getPendingInvites(
        @Path("userName") userName: String
    ): Call<ApiResponse<Churrasco>>

    // Novo endpoint para registrar usuário

    @POST("/users/register")
    fun registerUser(
        @Body request: RegisterRequest
    ): Call<ApiResponse<Any>>

    @POST("/users/login")
    fun login(
        @Body request: LoginRequest
    ): Call<ApiResponse<Any>>
}
