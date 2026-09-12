package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// --- Gemini Request Models ---

@JsonClass(generateAdapter = true)
data class GeminiGenerateContentRequest(
    @Json(name = "contents") val contents: List<GeminiContent>,
    @Json(name = "systemInstruction") val systemInstruction: GeminiContent? = null,
    @Json(name = "tools") val tools: List<GeminiTool>? = null,
    @Json(name = "generationConfig") val generationConfig: GeminiGenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    @Json(name = "role") val role: String? = null,
    @Json(name = "parts") val parts: List<GeminiPart>? = null
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    @Json(name = "text") val text: String? = null,
    @Json(name = "functionCall") val functionCall: GeminiFunctionCall? = null
)

@JsonClass(generateAdapter = true)
data class GeminiFunctionCall(
    @Json(name = "name") val name: String,
    @Json(name = "args") val args: Map<String, Any?>? = null
)

@JsonClass(generateAdapter = true)
data class GeminiTool(
    @Json(name = "functionDeclarations") val functionDeclarations: List<GeminiFunctionDeclaration>
)

@JsonClass(generateAdapter = true)
data class GeminiFunctionDeclaration(
    @Json(name = "name") val name: String,
    @Json(name = "description") val description: String,
    @Json(name = "parameters") val parameters: GeminiParametersObject? = null
)

@JsonClass(generateAdapter = true)
data class GeminiParametersObject(
    @Json(name = "type") val type: String = "OBJECT",
    @Json(name = "properties") val properties: Map<String, GeminiParameterProperty>,
    @Json(name = "required") val required: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class GeminiParameterProperty(
    @Json(name = "type") val type: String,
    @Json(name = "description") val description: String
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    @Json(name = "temperature") val temperature: Float? = null,
    @Json(name = "topP") val topP: Float? = null,
    @Json(name = "topK") val topK: Int? = null,
    @Json(name = "maxOutputTokens") val maxOutputTokens: Int? = null
)

// --- Gemini Response Models ---

@JsonClass(generateAdapter = true)
data class GeminiGenerateContentResponse(
    @Json(name = "candidates") val candidates: List<GeminiCandidate>? = null,
    @Json(name = "error") val error: GeminiErrorResponse? = null,
    @Json(name = "promptFeedback") val promptFeedback: Map<String, Any?>? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    @Json(name = "content") val content: GeminiContent? = null,
    @Json(name = "finishReason") val finishReason: String? = null,
    @Json(name = "finishMessage") val finishMessage: String? = null,
    @Json(name = "index") val index: Int? = null
)

@JsonClass(generateAdapter = true)
data class GeminiErrorResponse(
    @Json(name = "code") val code: Int? = null,
    @Json(name = "message") val message: String? = null,
    @Json(name = "status") val status: String? = null
)

// --- Internal Assistant Result Models ---

data class ToolCallRequest(
    val functionName: String,
    val arguments: Map<String, Any?> = emptyMap()
)

data class AssistantCommandResult(
    val spokenResponse: String,
    val toolCallRequest: ToolCallRequest? = null,
    val executedToolResult: com.example.data.model.ToolExecutionResult? = null,
    val executionSource: String = "gemini_api"
)
