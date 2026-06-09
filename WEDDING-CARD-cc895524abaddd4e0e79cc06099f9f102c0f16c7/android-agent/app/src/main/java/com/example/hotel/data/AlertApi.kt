package com.example.hotel.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface AlertApi {

    @POST("/api/alert/breach")
    suspend fun breach(
        @Header("Authorization") auth: String,
        @Body body: BreachRequest
    ): Map<String, Any>

    @POST("/api/alert/battery")
    suspend fun battery(
        @Header("Authorization") auth: String,
        @Body body: BatteryRequest
    ): Map<String, Any>

    @POST("/api/heartbeat")
    suspend fun heartbeat(
        @Header("Authorization") auth: String,
        @Body body: HeartbeatRequest
    ): Map<String, Any>

    @POST("/api/devices/register")
    suspend fun register(
        @Header("Authorization") auth: String,
        @Body body: RegisterRequest
    ): Map<String, Any>

    @GET("/api/config/{deviceId}")
    suspend fun config(
        @Header("Authorization") auth: String,
        @Path("deviceId") deviceId: String
    ): Map<String, Any>

    @POST("/api/rooms/upsert")
    suspend fun upsertRoom(
        @Header("Authorization") auth: String,
        @Body body: Map<String, Any>
    ): Map<String, Any>

    @POST("/api/alert/tamper")
    suspend fun tamper(
        @Header("Authorization") auth: String,
        @Body body: TamperRequest
    ): Map<String, Any>
}
