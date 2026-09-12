package com.example.data.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for Google Gemini Generative Language API.
 * Uses gemini-3.6-flash for modern general text and tool execution tasks.
 */
interface GeminiApiService {

    @POST("v1beta/models/gemini-3.6-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiGenerateContentRequest
    ): Response<GeminiGenerateContentResponse>

    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContentWithModel(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiGenerateContentRequest
    ): Response<GeminiGenerateContentResponse>

    @GET("v1beta/models")
    suspend fun listModels(
        @Query("key") apiKey: String
    ): Response<ResponseBody>
}
