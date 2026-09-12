package com.example.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for Google Gemini Generative Language API.
 * Uses gemini-3.5-flash as mandated for modern basic and general text/tool tasks.
 */
interface GeminiApiService {

    @POST("v1beta/models/gemini-3.5-flash:generateContent")
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
}
