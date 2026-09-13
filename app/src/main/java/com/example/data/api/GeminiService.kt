package com.example.data.api

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.DeviceStats
import com.example.data.model.ToolExecutionResult
import com.example.data.preferences.AssistantPreferences
import com.example.data.security.KeystoreManager
import com.example.system.DeviceController
import com.example.system.ToolExecutionModule
import com.example.util.PipelinePerfLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LlmAssistantReply(
    val spokenResponse: String,
    val toolCommand: String? = null,
    val toolArg1: String? = null,
    val toolArg2: String? = null,
    val executionSource: String = "gemini_api"
)

/**
 * Service layer coordinating Retrofit network calls to Google Gemini API (gemini-3.6-flash)
 * with device telemetry, accessibility context, tool declarations, response parsing,
 * and device tool execution via ToolExecutionModule.
 */
class GeminiService(
    private val context: Context,
    private val keystoreManager: KeystoreManager,
    private val preferences: AssistantPreferences,
    private val deviceController: DeviceController? = null,
    private val toolExecutionModule: ToolExecutionModule? = null
) {
    private val tag = "GeminiService"
    private val apiService: GeminiApiService by lazy { GeminiRetrofitClient.apiService }
    private val toolExecutor: ToolExecutionModule by lazy {
        toolExecutionModule ?: ToolExecutionModule(context, preferences)
    }

    /**
     * Validates an API key by making a lightweight test request to Gemini API.
     * Returns Result.success if key is valid, or Result.failure with descriptive error if 401/403/invalid.
     */
    suspend fun validateApiKey(keyToTest: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = keyToTest.trim()
            .removeSurrounding("\"", "\"")
            .removeSurrounding("'", "'")
            .replace("\n", "")
            .replace("\r", "")
            .trim()

        if (cleanKey.isBlank()) {
            Log.w(tag, "[API_KEY_VALIDATION] Empty API key provided.")
            return@withContext Result.failure(IllegalArgumentException("API key cannot be empty."))
        }

        Log.i(tag, "[API_KEY_VALIDATION] Initiating validation for key '${cleanKey.take(6)}...${cleanKey.takeLast(4)}' (len=${cleanKey.length})")

        try {
            val pingRequest = GeminiGenerateContentRequest(
                contents = listOf(
                    GeminiContent(
                        role = "user",
                        parts = listOf(GeminiPart(text = "Hello! Please reply with 'OK'."))
                    )
                ),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.1f,
                    maxOutputTokens = 10
                )
            )

            // Primary attempt: gemini-3.6-flash:generateContent
            val response = apiService.generateContent(apiKey = cleanKey, request = pingRequest)
            val httpCode = response.code()
            val rawErrorBody = response.errorBody()?.string() ?: ""
            val responseBody = response.body()

            Log.i(
                tag,
                "[API_KEY_VALIDATION] Primary endpoint 'v1beta/models/gemini-3.6-flash:generateContent' returned HTTP $httpCode | Successful=${response.isSuccessful} | ResponseBody=$responseBody | Candidates=${responseBody?.candidates} | ErrorBody=$rawErrorBody"
            )

            // Case 1: HTTP 200 OK
            if (response.isSuccessful) {
                val apiError = responseBody?.error
                if (apiError != null && (apiError.status == "INVALID_ARGUMENT" || apiError.message?.contains("API_KEY_INVALID", ignoreCase = true) == true)) {
                    Log.w(tag, "[API_KEY_VALIDATION] HTTP 200 returned API error: ${apiError.message}")
                    return@withContext Result.failure(Exception("API Key Invalid: ${apiError.message}"))
                }

                val firstCandidate = responseBody?.candidates?.firstOrNull()
                val finishReason = firstCandidate?.finishReason
                val parts = firstCandidate?.content?.parts

                Log.i(tag, "[API_KEY_VALIDATION] Candidate finishReason: '${finishReason ?: "STOP"}' | Parts count: ${parts?.size ?: 0}")

                if (firstCandidate != null && finishReason != null && finishReason != "STOP") {
                    Log.w(tag, "[API_KEY_VALIDATION] Warning: Ping response ended with non-STOP finishReason '$finishReason'")
                }

                Log.i(tag, "[API_KEY_VALIDATION] SUCCESS: Key validated via gemini-3.6-flash endpoint.")
                return@withContext Result.success("API key verified and stored securely.")
            }

            // Case 2: HTTP 429 Too Many Requests / Quota Exceeded
            // Rate limiting means Google accepted the key as valid, but quota limit is hit. Key IS VALID!
            if (httpCode == 429 || rawErrorBody.contains("RESOURCE_EXHAUSTED", ignoreCase = true) || rawErrorBody.contains("Quota exceeded", ignoreCase = true)) {
                Log.i(tag, "[API_KEY_VALIDATION] SUCCESS (Rate Limited): HTTP $httpCode returned quota limit. Key is valid.")
                return@withContext Result.success("API key verified (Quota limit active).")
            }

            // Case 3: HTTP 404 (Model Not Found) or 503 (Server Overload) -> Fallback to GET v1beta/models
            if (httpCode == 404 || httpCode >= 500) {
                Log.w(tag, "[API_KEY_VALIDATION] Primary model endpoint returned HTTP $httpCode. Attempting fallback validation via GET 'v1beta/models'...")
                val modelsResponse = apiService.listModels(apiKey = cleanKey)
                val modelsCode = modelsResponse.code()
                val modelsErr = modelsResponse.errorBody()?.string() ?: ""

                Log.i(tag, "[API_KEY_VALIDATION] Fallback 'v1beta/models' returned HTTP $modelsCode | Successful=${modelsResponse.isSuccessful} | Error=$modelsErr")

                if (modelsResponse.isSuccessful || modelsCode == 429) {
                    Log.i(tag, "[API_KEY_VALIDATION] SUCCESS: Key validated via fallback models endpoint.")
                    return@withContext Result.success("API key verified via models endpoint.")
                } else {
                    Log.w(tag, "[API_KEY_VALIDATION] Key rejected by models endpoint HTTP $modelsCode: $modelsErr")
                    return@withContext Result.failure(Exception("Invalid API key (HTTP $modelsCode): $modelsErr"))
                }
            }

            // Case 4: HTTP 400, 401, 403 (Invalid Key / Unauthorized)
            Log.w(tag, "[API_KEY_VALIDATION] Key rejected by Gemini API with HTTP $httpCode: $rawErrorBody")
            val userFriendlyMessage = when {
                rawErrorBody.contains("API_KEY_INVALID", ignoreCase = true) || rawErrorBody.contains("API key not valid", ignoreCase = true) ->
                    "Invalid API key — key was rejected by Google."
                rawErrorBody.contains("PERMISSION_DENIED", ignoreCase = true) ->
                    "Permission denied for this API key."
                else ->
                    "Invalid API key (HTTP $httpCode) — please check your key and try again."
            }

            Result.failure(Exception(userFriendlyMessage))
        } catch (e: Exception) {
            Log.e(tag, "[API_KEY_VALIDATION] Exception during key validation: ${e.message}", e)
            Result.failure(Exception("Validation error: ${e.localizedMessage ?: "Network or connection error"}"))
        }
    }

    /**
     * Resolves the active Gemini API key from Android Keystore or BuildConfig.
     */
    fun getEffectiveApiKey(): String {
        val userEnteredKey = keystoreManager.retrieveAndDecrypt("user_llm_api_key")
        if (!userEnteredKey.isNullOrBlank()) return userEnteredKey.trim()

        return try {
            val buildKey = BuildConfig.GEMINI_API_KEY
            if (buildKey.isNotBlank() && !buildKey.contains("MY_GEMINI_API_KEY")) buildKey else ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Full-featured pipeline: Sends transcribed command + device context via Retrofit to Gemini,
     * parses response for tool calls, and immediately executes identified tool calls on device.
     */
    suspend fun processCommandAndExecuteTools(
        command: String,
        deviceStats: DeviceStats,
        memoriesContext: String,
        screenContext: String? = null
    ): AssistantCommandResult = withContext(Dispatchers.IO) {
        // App Launch Interceptor: If command matches "open X", "launch X", "start X", "X kholo", route directly to App Launcher!
        val appLaunchResult = tryAppLaunchPreRouting(command)
        if (appLaunchResult != null) {
            PipelinePerfLogger.markApiResponseReceived("app_launch_interceptor")
            return@withContext appLaunchResult
        }

        val apiKey = getEffectiveApiKey()
        if (apiKey.isBlank()) {
            Log.d(tag, "No API key configured. Executing command via offline rule engine.")
            PipelinePerfLogger.markApiRequestSent()
            val fallbackReply = runOfflineRuleEngine(command, deviceStats)
            PipelinePerfLogger.markApiResponseReceived("offline_rule_engine")
            val executedResult = executeLegacyTool(fallbackReply.toolCommand, fallbackReply.toolArg1, fallbackReply.toolArg2)
            return@withContext AssistantCommandResult(
                spokenResponse = fallbackReply.spokenResponse,
                toolCallRequest = fallbackReply.toolCommand?.let {
                    ToolCallRequest(it, mapOf("arg1" to fallbackReply.toolArg1, "arg2" to fallbackReply.toolArg2))
                },
                executedToolResult = executedResult,
                executionSource = "offline_rule_engine"
            )
        }

        try {
            // Build rich structured request for Gemini
            val request = buildGeminiRequest(command, deviceStats, memoriesContext, screenContext)
            Log.d(tag, "Sending command to Gemini via Retrofit: '$command'")
            PipelinePerfLogger.markApiRequestSent()

            val response = apiService.generateContent(apiKey = apiKey, request = request)
            PipelinePerfLogger.markApiResponseReceived("gemini_api_http_${response.code()}")

            if (!response.isSuccessful || response.body() == null) {
                val errorMsg = response.errorBody()?.string() ?: response.message()
                Log.w(tag, "Gemini API error (HTTP ${response.code()}): $errorMsg")
                val fallbackReply = runOfflineRuleEngine(command, deviceStats)
                val executedResult = executeLegacyTool(fallbackReply.toolCommand, fallbackReply.toolArg1, fallbackReply.toolArg2)
                return@withContext AssistantCommandResult(
                    spokenResponse = fallbackReply.spokenResponse,
                    toolCallRequest = fallbackReply.toolCommand?.let { ToolCallRequest(it) },
                    executedToolResult = executedResult,
                    executionSource = "offline_fallback_http_${response.code()}"
                )
            }

            val responseBody = response.body()!!
            val candidate = responseBody.candidates?.firstOrNull()
            val candidateContent = candidate?.content
            val finishReason = candidate?.finishReason
            val parts = candidateContent?.parts

            Log.i(
                tag,
                "[GEMINI_RESPONSE] HTTP 200 | Candidates count: ${responseBody.candidates?.size ?: 0} | finishReason: '${finishReason ?: "STOP"}' | Parts count: ${parts?.size ?: 0} | PromptFeedback: ${responseBody.promptFeedback}"
            )

            if (finishReason != null && finishReason != "STOP") {
                Log.w(tag, "[GEMINI_RESPONSE] Non-STOP finishReason encountered: '$finishReason'")
            }

            if (parts.isNullOrEmpty()) {
                Log.w(tag, "[GEMINI_RESPONSE] Candidate has no content parts. finishReason='$finishReason'")
                val fallbackSpoken = when (finishReason) {
                    "SAFETY" -> "Request was blocked by Gemini safety settings."
                    "MAX_TOKENS" -> "The response exceeded token limits."
                    "RECITATION" -> "Response was blocked due to recitation policy."
                    "OTHER" -> "The AI response ended unexpectedly."
                    else -> if (finishReason != null) "Gemini response stopped ($finishReason)." else "Received empty response from Gemini API."
                }

                return@withContext AssistantCommandResult(
                    spokenResponse = fallbackSpoken,
                    toolCallRequest = null,
                    executedToolResult = null,
                    executionSource = "gemini_api_empty_parts"
                )
            }

            // 1. Identify tool call from response (Native functionCall or parsed tags)
            val toolCall = identifyToolCall(candidateContent)

            // 2. Extract or synthesize spoken text
            val spokenText = extractSpokenResponse(candidateContent, toolCall)

            // 3. Execute identified tool call on device
            var toolResult: ToolExecutionResult? = null
            if (toolCall != null) {
                toolResult = executeToolCall(toolCall)
            }

            Log.i(tag, "[INTENT_ROUTER] Raw input: '$command' | Source: 'gemini_api' | Tool: '${toolCall?.functionName ?: "NONE (Conversational)"}' | Args: ${toolCall?.arguments} | Spoken: '$spokenText'")

            AssistantCommandResult(
                spokenResponse = spokenText,
                toolCallRequest = toolCall,
                executedToolResult = toolResult,
                executionSource = "gemini_api"
            )
        } catch (e: Exception) {
            Log.e(tag, "Gemini Retrofit request failed (${e.message}), executing offline rule engine fallback", e)
            val fallbackReply = runOfflineRuleEngine(command, deviceStats)
            val executedResult = executeLegacyTool(fallbackReply.toolCommand, fallbackReply.toolArg1, fallbackReply.toolArg2)
            val toolReq = fallbackReply.toolCommand?.let { ToolCallRequest(it, mapOf("arg1" to fallbackReply.toolArg1, "arg2" to fallbackReply.toolArg2)) }

            Log.i(tag, "[INTENT_ROUTER] Raw input: '$command' | Source: 'offline_fallback_exception' | Tool: '${fallbackReply.toolCommand ?: "NONE (Conversational)"}' | Spoken: '${fallbackReply.spokenResponse}'")

            AssistantCommandResult(
                spokenResponse = fallbackReply.spokenResponse,
                toolCallRequest = toolReq,
                executedToolResult = executedResult,
                executionSource = "offline_fallback_exception"
            )
        }
    }

    private fun tryAppLaunchPreRouting(command: String): AssistantCommandResult? {
        val q = command.lowercase().trim()
        val isAppLaunchPattern = q.startsWith("open ") ||
                q.startsWith("launch ") ||
                q.startsWith("start ") ||
                q.startsWith("kholo ") ||
                q.endsWith(" kholo") ||
                q.endsWith(" open karo") ||
                q.endsWith(" app")

        if (!isAppLaunchPattern) return null

        val appName = q.replace("open app ", "")
            .replace("open ", "")
            .replace("launch ", "")
            .replace("start ", "")
            .replace("kholo ", "")
            .replace(" kholo", "")
            .replace(" open karo", "")
            .replace(" app", "")
            .trim()

        if (appName.isBlank()) return null

        Log.i(tag, "[INTENT_ROUTER] Intercepted App Launch Command: '$command' -> target app '$appName'")
        val toolCall = ToolCallRequest("open_app", mapOf("arg1" to appName, "app_name" to appName))
        val executedResult = executeToolCall(toolCall)
        return AssistantCommandResult(
            spokenResponse = "$appName open kar rahi hun.",
            toolCallRequest = toolCall,
            executedToolResult = executedResult,
            executionSource = "app_launch_intent_interceptor"
        )
    }

    /**
     * Backward-compatible method returning LlmAssistantReply for callers that manage tool execution.
     */
    suspend fun queryAssistant(
        userInput: String,
        deviceStats: DeviceStats,
        memoriesContext: String,
        screenContext: String? = null
    ): LlmAssistantReply {
        val result = processCommandAndExecuteTools(userInput, deviceStats, memoriesContext, screenContext)
        return LlmAssistantReply(
            spokenResponse = result.spokenResponse,
            toolCommand = result.toolCallRequest?.functionName,
            toolArg1 = result.toolCallRequest?.arguments?.get("arg1")?.toString()
                ?: result.toolCallRequest?.arguments?.get("app_name")?.toString()
                ?: result.toolCallRequest?.arguments?.get("seconds")?.toString()
                ?: result.toolCallRequest?.arguments?.get("query")?.toString()
                ?: result.toolCallRequest?.arguments?.get("level_percent")?.toString()
                ?: result.toolCallRequest?.arguments?.get("state")?.toString()
                ?: result.toolCallRequest?.arguments?.get("phone_number")?.toString(),
            toolArg2 = result.toolCallRequest?.arguments?.get("arg2")?.toString()
                ?: result.toolCallRequest?.arguments?.get("message")?.toString()
                ?: result.toolCallRequest?.arguments?.get("label")?.toString(),
            executionSource = result.executionSource
        )
    }

    /**
     * Identifies tool calls either from Gemini Function Calling structure or text directives.
     */
    private fun identifyToolCall(content: GeminiContent?): ToolCallRequest? {
        val partsList = content?.parts
        if (partsList.isNullOrEmpty()) return null

        // 1. Check for native Gemini functionCall
        for (part in partsList) {
            val fn = part.functionCall
            if (fn != null && fn.name.isNotBlank()) {
                val cleanArgs = fn.args ?: emptyMap()
                return ToolCallRequest(
                    functionName = fn.name.trim(),
                    arguments = cleanArgs
                )
            }
        }

        // 2. Check for text directive fallback: [TOOL:COMMAND:arg1:arg2]
        val fullText = partsList.mapNotNull { it.text }.joinToString("\n")
        if (fullText.isNotBlank()) {
            val toolRegex = Regex("\\[TOOL:([A-Za-z0-9_]+)(?::([^:\\]]+))?(?::([^\\]]+))?\\]")
            val match = toolRegex.find(fullText)
            if (match != null) {
                val cmd = match.groupValues[1]
                val arg1 = match.groupValues.getOrNull(2)
                val arg2 = match.groupValues.getOrNull(3)
                val argsMap = mutableMapOf<String, Any?>()
                if (arg1 != null) argsMap["arg1"] = arg1
                if (arg2 != null) argsMap["arg2"] = arg2
                return ToolCallRequest(functionName = cmd, arguments = argsMap)
            }
        }

        return null
    }

    /**
     * Extracts spoken response text from candidates, stripping any markup directives.
     */
    private fun extractSpokenResponse(content: GeminiContent?, toolCall: ToolCallRequest?): String {
        val partsList = content?.parts ?: emptyList()
        val toolRegex = Regex("\\[TOOL:([A-Za-z0-9_]+)(?::([^:\\]]+))?(?::([^\\]]+))?\\]")
        val rawText = partsList.mapNotNull { it.text }.joinToString(" ").replace(toolRegex, "").trim()

        if (rawText.isNotBlank()) {
            return rawText
        }

        // If Gemini returned a functionCall with empty text, synthesize a clear verbal confirmation:
        return when (toolCall?.functionName?.lowercase()) {
            "toggle_wifi", "togglewifi", "wifi", "set_wifi" -> "Configuring Wi-Fi network."
            "toggle_bluetooth", "togglebluetooth", "bluetooth", "set_bluetooth" -> "Configuring Bluetooth."
            "set_volume", "setvolume" -> "Setting media volume to ${toolCall.arguments["level_percent"] ?: toolCall.arguments["percent"] ?: "70"} percent."
            "adjust_volume", "adjustvolume" -> "Adjusting volume level."
            "mute_volume", "mutevolume", "mute" -> "Muting media volume."
            "set_brightness", "setbrightness", "brightness" -> "Setting screen brightness to ${toolCall.arguments["level_percent"] ?: toolCall.arguments["percent"] ?: "60"} percent."
            "adjust_brightness", "adjustbrightness" -> "Adjusting screen brightness."
            "open_app", "openapp" -> "Opening ${toolCall.arguments["app_name"] ?: "the app"}."
            "set_timer", "settimer" -> "Setting a timer for ${toolCall.arguments["seconds"] ?: "5"} seconds."
            "toggle_flashlight", "toggleflashlight" -> "Toggling flashlight ${toolCall.arguments["state"] ?: ""}."
            "search_web", "searchweb" -> "Searching Google for ${toolCall.arguments["query"] ?: "information"}."
            "open_spotify", "openspotify" -> "Searching for ${toolCall.arguments["query"] ?: "music"} on Spotify."
            "open_youtube", "openyoutube" -> "Playing ${toolCall.arguments["query"] ?: "video"} on YouTube."
            "send_whatsapp", "sendwhatsapp" -> "Preparing WhatsApp message."
            "send_sms", "sendsms" -> "Opening SMS composer."
            "make_call", "makecall" -> "Initiating phone call."
            "open_settings", "opensettings" -> "Opening device settings."
            "check_battery", "checkbattery" -> "Checking current battery status."
            "check_storage", "checkstorage" -> "Checking storage capacity."
            "read_screen", "readscreen" -> "Scanning active screen contents."
            null -> "Command completed."
            else -> "Executing ${toolCall.functionName} for you."
        }
    }

    /**
     * Executes the identified tool call directly on device hardware/system via ToolExecutionModule.
     */
    fun executeToolCall(toolCall: ToolCallRequest): ToolExecutionResult {
        return toolExecutor.executeGeminiTool(toolCall)
    }

    private fun executeLegacyTool(cmd: String?, arg1: String?, arg2: String?): ToolExecutionResult? {
        if (cmd.isNullOrBlank()) return null
        val args = mutableMapOf<String, Any?>()
        if (arg1 != null) args["arg1"] = arg1
        if (arg2 != null) args["arg2"] = arg2
        return executeToolCall(ToolCallRequest(cmd, args))
    }

    /**
     * Constructs the Retrofit Gemini request with system instructions, device telemetry,
     * accessibility screen context, user memories, and tool declarations.
     */
    private fun buildGeminiRequest(
        command: String,
        stats: DeviceStats,
        memories: String,
        screenContext: String?
    ): GeminiGenerateContentRequest {
        val userName = preferences.userName
        val lang = preferences.selectedLanguageCode
        val nowStr = SimpleDateFormat("EEEE, MMMM d, yyyy HH:mm", Locale.getDefault()).format(Date())

        val systemPrompt = """
            You are Aura, an elite, proactive, direct personal voice AI assistant running natively on ${userName}'s Android device.
            Language: Respond naturally in $lang.
            Current Time: $nowStr
            
            Device Hardware & Context:
            - Battery: ${stats.batteryPercent}% (${if (stats.isCharging) "Charging" else "Unplugged"})
            - RAM Usage: ${stats.ramUsedMb}MB used of ${stats.ramTotalMb}MB total
            - Internal Storage Free: ${stats.storageFreeGb}GB free of ${stats.storageTotalGb}GB total
            - Approximate Location: ${stats.locationCity}
            - Screen Reading Context (Accessibility): ${screenContext ?: "None available / screen is off"}
            - Owner Memory & Personal Facts: ${if (memories.isNotBlank()) memories else "No prior facts stored"}

            CRITICAL TOOL ROUTING DIRECTIVES:
            1. ONLY call 'search_web' if the user EXPLICITLY asks to perform a web search (e.g., 'search for...', 'google...', 'look up...', 'what is the capital of...').
            2. NEVER call 'search_web' for device control commands, alarms, timers, messages, phone calls, opening apps, or volume/brightness adjustments.
            3. For phone calls ('call Ravi', 'dial X'), call 'make_call'.
            4. For WhatsApp messages ('send WhatsApp to X', 'text X on WhatsApp'), call 'send_whatsapp'.
            5. For alarms ('set an alarm for 7am', 'wake me at 8'), call 'set_alarm'.
            6. For timers ('set timer for 10 minutes'), call 'set_timer'.
            7. For launching apps ('open Spotify', 'launch Settings', 'start Camera'), call 'open_app'.
            8. If a question is general conversation or has no applicable tool, respond directly with spoken text and do NOT call any tool.
            9. Keep spoken conversational answers concise (1-3 sentences), warm, confident, and natural for text-to-speech.
            10. CONVERSATIONAL ACKNOWLEDGMENTS & PERSONA: Prefix your answers or actions with polite, warm, human-sounding verbal acknowledgments. If the language is Hindi or Hinglish, use warm native terms like "Ji sir, ho gaya", "Ji sir, bilkul", "Ji sir, abhi karta hoon", "Ji sir". If English, use professional, elegant prefixes like "Absolutely", "Right away, sir", "Done!", "Sure!", "Ji sir". Use these natural verbal acknowledgments to establish a highly polished, responsive butler-like persona.
        """.trimIndent()

        // Tools declared for Gemini function calling
        val tools = listOf(
            GeminiTool(
                functionDeclarations = listOf(
                    GeminiFunctionDeclaration(
                        name = "toggle_wifi",
                        description = "Enable or disable device Wi-Fi connectivity or open Wi-Fi quick settings panel",
                        parameters = GeminiParametersObject(
                            properties = mapOf(
                                "enable" to GeminiParameterProperty(
                                    type = "BOOLEAN",
                                    description = "true to enable Wi-Fi, false to disable Wi-Fi"
                                )
                            ),
                            required = listOf("enable")
                        )
                    ),
                    GeminiFunctionDeclaration(
                        name = "toggle_bluetooth",
                        description = "Enable or disable device Bluetooth adapter or open Bluetooth quick settings panel",
                        parameters = GeminiParametersObject(
                            properties = mapOf(
                                "enable" to GeminiParameterProperty(
                                    type = "BOOLEAN",
                                    description = "true to enable Bluetooth, false to disable Bluetooth"
                                )
                            ),
                            required = listOf("enable")
                        )
                    ),
                    GeminiFunctionDeclaration(
                        name = "set_brightness",
                        description = "Set device display screen brightness percentage (0-100%)",
                        parameters = GeminiParametersObject(
                            properties = mapOf(
                                "level_percent" to GeminiParameterProperty(
                                    type = "INTEGER",
                                    description = "Target brightness percentage between 0 and 100"
                                ),
                                "auto_brightness" to GeminiParameterProperty(
                                    type = "BOOLEAN",
                                    description = "true to enable automatic/adaptive brightness"
                                )
                            ),
                            required = listOf("level_percent")
                        )
                    ),
                    GeminiFunctionDeclaration(
                        name = "adjust_brightness",
                        description = "Increase or decrease screen display brightness",
                        parameters = GeminiParametersObject(
                            properties = mapOf(
                                "increase" to GeminiParameterProperty(
                                    type = "BOOLEAN",
                                    description = "true to brighten screen, false to dim screen"
                                )
                            ),
                            required = listOf("increase")
                        )
                    ),
                    GeminiFunctionDeclaration(
                        name = "open_app",
                        description = "Launch an installed Android application or utility",
                        parameters = GeminiParametersObject(
                            properties = mapOf(
                                "app_name" to GeminiParameterProperty(
                                    type = "STRING",
                                    description = "Name or package of the app to launch (e.g. 'youtube', 'spotify', 'camera', 'whatsapp', 'settings')"
                                )
                            ),
                            required = listOf("app_name")
                        )
                    ),
                    GeminiFunctionDeclaration(
                        name = "set_timer",
                        description = "Set a countdown timer on the Android device",
                        parameters = GeminiParametersObject(
                            properties = mapOf(
                                "seconds" to GeminiParameterProperty(
                                    type = "INTEGER",
                                    description = "Timer duration in seconds"
                                ),
                                "label" to GeminiParameterProperty(
                                    type = "STRING",
                                    description = "Label or message for the timer"
                                )
                            ),
                            required = listOf("seconds")
                        )
                    ),
                    GeminiFunctionDeclaration(
                        name = "set_volume",
                        description = "Adjust device media audio volume level",
                        parameters = GeminiParametersObject(
                            properties = mapOf(
                                "level_percent" to GeminiParameterProperty(
                                    type = "INTEGER",
                                    description = "Target volume percentage from 0 to 100"
                                )
                            ),
                            required = listOf("level_percent")
                        )
                    ),
                    GeminiFunctionDeclaration(
                        name = "toggle_flashlight",
                        description = "Turn device camera flashlight torch ON or OFF",
                        parameters = GeminiParametersObject(
                            properties = mapOf(
                                "state" to GeminiParameterProperty(
                                    type = "STRING",
                                    description = "'ON' to enable flashlight, 'OFF' to disable"
                                )
                            ),
                            required = listOf("state")
                        )
                    ),
                    GeminiFunctionDeclaration(
                        name = "search_web",
                        description = "Search Google on the web. STRICT REQUIREMENT: Only call this tool if user explicitly requests a web search (e.g., 'search for...', 'look up...', 'google...'). NEVER call for system controls, calls, messages, alarms, timers, or opening apps.",
                        parameters = GeminiParametersObject(
                            properties = mapOf(
                                "query" to GeminiParameterProperty(
                                    type = "STRING",
                                    description = "The search query"
                                )
                            ),
                            required = listOf("query")
                        )
                    ),
                    GeminiFunctionDeclaration(
                        name = "open_spotify",
                        description = "Search and stream music on Spotify",
                        parameters = GeminiParametersObject(
                            properties = mapOf(
                                "query" to GeminiParameterProperty(
                                    type = "STRING",
                                    description = "Song name, album, or artist"
                                )
                            ),
                            required = listOf("query")
                        )
                    ),
                    GeminiFunctionDeclaration(
                        name = "open_youtube",
                        description = "Search and stream video on YouTube",
                        parameters = GeminiParametersObject(
                            properties = mapOf(
                                "query" to GeminiParameterProperty(
                                    type = "STRING",
                                    description = "Video title or search query"
                                )
                            ),
                            required = listOf("query")
                        )
                    ),
                    GeminiFunctionDeclaration(
                        name = "send_whatsapp",
                        description = "Compose and send a WhatsApp message",
                        parameters = GeminiParametersObject(
                            properties = mapOf(
                                "phone_number" to GeminiParameterProperty(type = "STRING", description = "Target phone number with country code"),
                                "message" to GeminiParameterProperty(type = "STRING", description = "Message text")
                            ),
                            required = listOf("phone_number", "message")
                        )
                    ),
                    GeminiFunctionDeclaration(
                        name = "send_sms",
                        description = "Compose an SMS text message",
                        parameters = GeminiParametersObject(
                            properties = mapOf(
                                "phone_number" to GeminiParameterProperty(type = "STRING", description = "Recipient phone number"),
                                "message" to GeminiParameterProperty(type = "STRING", description = "SMS message body")
                            ),
                            required = listOf("phone_number", "message")
                        )
                    ),
                    GeminiFunctionDeclaration(
                        name = "make_call",
                        description = "Dial a voice telephone call",
                        parameters = GeminiParametersObject(
                            properties = mapOf(
                                "phone_number" to GeminiParameterProperty(type = "STRING", description = "Phone number to dial")
                            ),
                            required = listOf("phone_number")
                        )
                    ),
                    GeminiFunctionDeclaration(
                        name = "set_alarm",
                        description = "Set an alarm on the Android device",
                        parameters = GeminiParametersObject(
                            properties = mapOf(
                                "hour" to GeminiParameterProperty(type = "INTEGER", description = "Hour (0-23)"),
                                "minute" to GeminiParameterProperty(type = "INTEGER", description = "Minute (0-59)"),
                                "label" to GeminiParameterProperty(type = "STRING", description = "Alarm label")
                            ),
                            required = listOf("hour", "minute")
                        )
                    ),
                    GeminiFunctionDeclaration(
                        name = "adjust_volume",
                        description = "Raise or lower media audio volume",
                        parameters = GeminiParametersObject(
                            properties = mapOf(
                                "increase" to GeminiParameterProperty(type = "BOOLEAN", description = "true to increase, false to decrease")
                            ),
                            required = listOf("increase")
                        )
                    ),
                    GeminiFunctionDeclaration(
                        name = "mute_volume",
                        description = "Mute media audio volume immediately",
                        parameters = null
                    ),
                    GeminiFunctionDeclaration(
                        name = "open_maps",
                        description = "Navigate or search a place using Google Maps",
                        parameters = GeminiParametersObject(
                            properties = mapOf(
                                "destination" to GeminiParameterProperty(type = "STRING", description = "Destination name, address, or landmark")
                            ),
                            required = listOf("destination")
                        )
                    ),
                    GeminiFunctionDeclaration(
                        name = "system_navigation",
                        description = "Execute Android system navigation actions (back, home, recents, notifications, quick_settings, lock, screenshot)",
                        parameters = GeminiParametersObject(
                            properties = mapOf(
                                "action" to GeminiParameterProperty(
                                    type = "STRING",
                                    description = "One of: 'back', 'home', 'recents', 'notifications', 'quick_settings', 'lock', 'screenshot'"
                                )
                            ),
                            required = listOf("action")
                        )
                    ),
                    GeminiFunctionDeclaration(
                        name = "save_memory",
                        description = "Remember a personal fact, preference, or note for the user",
                        parameters = GeminiParametersObject(
                            properties = mapOf(
                                "key" to GeminiParameterProperty(type = "STRING", description = "Short topic or title of the memory"),
                                "value" to GeminiParameterProperty(type = "STRING", description = "The fact or detail to store")
                            ),
                            required = listOf("key", "value")
                        )
                    ),
                    GeminiFunctionDeclaration(
                        name = "open_settings",
                        description = "Open device system settings or wireless connectivity screen",
                        parameters = null
                    ),
                    GeminiFunctionDeclaration(
                        name = "check_battery",
                        description = "Query device battery state and charging status",
                        parameters = null
                    ),
                    GeminiFunctionDeclaration(
                        name = "check_storage",
                        description = "Query internal device storage capacity and free space",
                        parameters = null
                    ),
                    GeminiFunctionDeclaration(
                        name = "read_screen",
                        description = "Scan accessibility text from the currently active screen",
                        parameters = null
                    ),
                    GeminiFunctionDeclaration(
                        name = "analyze_screen",
                        description = "Perform Siri-style visual analysis and summary of the active screen window",
                        parameters = null
                    ),
                    GeminiFunctionDeclaration(
                        name = "arm_theft_guard",
                        description = "Arm anti-theft motion sensor and voice intruder alarm protection",
                        parameters = null
                    ),
                    GeminiFunctionDeclaration(
                        name = "disarm_theft_guard",
                        description = "Disarm anti-theft security and stop sounding alarm",
                        parameters = null
                    )
                )
            )
        )

        return GeminiGenerateContentRequest(
            contents = listOf(
                GeminiContent(
                    role = "user",
                    parts = listOf(GeminiPart(text = command))
                )
            ),
            systemInstruction = GeminiContent(
                parts = listOf(GeminiPart(text = systemPrompt))
            ),
            tools = tools,
            generationConfig = GeminiGenerationConfig(
                temperature = 0.4f,
                maxOutputTokens = 800
            )
        )
    }

    /**
     * Local offline rule engine guaranteeing 100% smart assistant functionality in Hindi, Hinglish, and English
     * without requiring internet or API keys.
     */
    private fun runOfflineRuleEngine(query: String, stats: DeviceStats): LlmAssistantReply {
        val q = query.lowercase().trim()
        val userName = preferences.userName

        return when {
            // Flashlight / Torch (Hindi: batti, torch jalao/bujhao)
            q.contains("torch") || q.contains("flashlight") || q.contains("batti") -> {
                val turnOff = q.contains("off") || q.contains("band") || q.contains("bujhao") || q.contains("hatao")
                val turnOn = !turnOff
                LlmAssistantReply(
                    spokenResponse = if (turnOn) "Torch on kar di hai." else "Torch band kar di hai.",
                    toolCommand = "toggle_flashlight",
                    toolArg1 = if (turnOn) "ON" else "OFF"
                )
            }

            // Volume Adjustment (Hindi: awaz badhao/kam karo, volume up/down)
            q.contains("awaz badhao") || q.contains("volume badhao") || q.contains("volume up") || q.contains("increase volume") || q.contains("sound badhao") -> {
                LlmAssistantReply(
                    spokenResponse = "Volume badha diya hai.",
                    toolCommand = "adjust_volume",
                    toolArg1 = "true"
                )
            }
            q.contains("awaz kam") || q.contains("volume kam") || q.contains("volume down") || q.contains("decrease volume") || q.contains("sound kam") -> {
                LlmAssistantReply(
                    spokenResponse = "Volume kam kar diya hai.",
                    toolCommand = "adjust_volume",
                    toolArg1 = "false"
                )
            }
            q.contains("mute") || q.contains("silent") || q.contains("awaz band") || q.contains("chup") -> {
                LlmAssistantReply(
                    spokenResponse = "Media volume mute kar diya hai.",
                    toolCommand = "mute_volume"
                )
            }
            q.contains("volume to") || (q.contains("volume") && Regex("\\d+").containsMatchIn(q)) -> {
                val vol = Regex("(\\d+)").find(q)?.groupValues?.get(1)?.toIntOrNull() ?: 70
                LlmAssistantReply(
                    spokenResponse = "Volume $vol percent par set kar diya hai.",
                    toolCommand = "set_volume",
                    toolArg1 = vol.toString()
                )
            }

            // System Navigation Actions (Hindi: back jao, home jao, apps dikhao, notification, lock karo, screenshot)
            q.contains("back jao") || q.contains("go back") || q.contains("piche jao") || q.contains("piche chalo") || q == "back" -> {
                LlmAssistantReply(
                    spokenResponse = "Going back.",
                    toolCommand = "system_navigation",
                    toolArg1 = "back"
                )
            }
            q.contains("home jao") || q.contains("go home") || q.contains("home screen") || q == "home" -> {
                LlmAssistantReply(
                    spokenResponse = "Going to home screen.",
                    toolCommand = "system_navigation",
                    toolArg1 = "home"
                )
            }
            q.contains("recent apps") || q.contains("recent app") || q.contains("app switch") || q.contains("apps dikhao") -> {
                LlmAssistantReply(
                    spokenResponse = "Showing recent apps.",
                    toolCommand = "system_navigation",
                    toolArg1 = "recents"
                )
            }
            q.contains("notification") -> {
                LlmAssistantReply(
                    spokenResponse = "Opening notifications.",
                    toolCommand = "system_navigation",
                    toolArg1 = "notifications"
                )
            }
            q.contains("quick settings") -> {
                LlmAssistantReply(
                    spokenResponse = "Opening quick settings.",
                    toolCommand = "system_navigation",
                    toolArg1 = "quick_settings"
                )
            }
            q.contains("lock screen") || q.contains("phone lock") || q.contains("screen lock") -> {
                LlmAssistantReply(
                    spokenResponse = "Locking device.",
                    toolCommand = "system_navigation",
                    toolArg1 = "lock"
                )
            }
            q.contains("screenshot") -> {
                LlmAssistantReply(
                    spokenResponse = "Taking screenshot.",
                    toolCommand = "system_navigation",
                    toolArg1 = "screenshot"
                )
            }

            // Alarms
            q.contains("alarm") -> {
                val hour = Regex("(\\d{1,2})\\s*(?:baje|am|pm|:)").find(q)?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("(\\d{1,2})").find(q)?.groupValues?.get(1)?.toIntOrNull() ?: 7
                val isPm = q.contains("pm") || q.contains("sham") || q.contains("raat")
                val actualHour = if (isPm && hour < 12) hour + 12 else hour
                val minute = Regex(":(\\d{2})").find(q)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                LlmAssistantReply(
                    spokenResponse = "$actualHour:$minute ka alarm set kar diya hai.",
                    toolCommand = "set_alarm",
                    toolArg1 = actualHour.toString(),
                    toolArg2 = minute.toString()
                )
            }

            // Timers
            q.contains("timer") -> {
                val num = Regex("(\\d+)").find(q)?.groupValues?.get(1)?.toIntOrNull() ?: 5
                val isSeconds = q.contains("second") || q.contains("sec")
                val seconds = if (isSeconds) num else num * 60
                LlmAssistantReply(
                    spokenResponse = "$num ${if (isSeconds) "seconds" else "minutes"} ka timer set kar diya hai.",
                    toolCommand = "set_timer",
                    toolArg1 = seconds.toString(),
                    toolArg2 = "Aura Assistant Timer"
                )
            }

            // Battery Status
            q.contains("battery") || q.contains("charge") -> {
                LlmAssistantReply(
                    spokenResponse = "Aapki battery ${stats.batteryPercent} percent hai aur phone ${if (stats.isCharging) "charge ho raha hai" else "battery par chal raha hai"}.",
                    toolCommand = "check_battery"
                )
            }

            // RAM & Storage
            q.contains("ram") || q.contains("storage") || q.contains("memory") -> {
                LlmAssistantReply(
                    spokenResponse = "Device RAM usage ${stats.ramUsedMb} MB out of ${stats.ramTotalMb} MB hai. Aapke paas ${stats.storageFreeGb} GB internal storage khali hai.",
                    toolCommand = "check_storage"
                )
            }

            // Memory Storage (yaad rakho / remember that)
            q.contains("yaad rakho") || q.contains("remember that") || q.contains("remember this") -> {
                val content = q.replace("yaad rakho", "")
                    .replace("ki", "")
                    .replace("remember that", "")
                    .replace("remember this", "")
                    .trim()
                LlmAssistantReply(
                    spokenResponse = "Maine yaad rakh liya hai: $content",
                    toolCommand = "save_memory",
                    toolArg1 = "Personal Note",
                    toolArg2 = content
                )
            }

            // Screen Reading & Analysis (Siri-style)
            q.contains("screen analysis") || q.contains("analyze screen") || q.contains("screen analys") || q.contains("read screen") || q.contains("screen padho") || q.contains("screen par kya hai") || q.contains("what is on my screen") || q.contains("summarize screen") || q.contains("kya likha hai screen") -> {
                val screenData = com.example.service.AuraAccessibilityService.analyzeCurrentScreen(context)
                val reply = if (screenData.isEnabled) {
                    if (screenData.items.isNotEmpty()) {
                        "Aapki screen par abhi ${screenData.appName} khula hai. Isme mukhya roop se: ${screenData.items.take(4).joinToString(", ")} dikh raha hai."
                    } else {
                        "${screenData.appName} screen active hai, par isme koi text content nahi mila."
                    }
                } else {
                    "Screen analyzing ke liye Aura Accessibility Service on honi zaroori hai. Please Accessibility Settings mein jakar Aura ko allow karein."
                }
                LlmAssistantReply(
                    spokenResponse = reply,
                    toolCommand = "analyze_screen"
                )
            }

            // Anti-Theft Guard Security Controls
            q.contains("theft guard on") || q.contains("arm theft guard") || q.contains("theft guard chalu") || q.contains("suraksha on") || q.contains("turn on theft guard") || q.contains("anti theft on") -> {
                try {
                    com.example.AuraApplication.instance.theftGuardManager.armGuard()
                    preferences.toggleSecurityAntiTheft = true
                } catch (e: Exception) { }
                LlmAssistantReply(
                    spokenResponse = "Theft Guard security arm kar di hai! Agar koi device ko hilayega ya charger nikala to alarm bajega.",
                    toolCommand = "arm_theft_guard"
                )
            }
            q.contains("theft guard off") || q.contains("disarm theft guard") || q.contains("theft guard band") || q.contains("stop alarm") || q.contains("alarm band") || q.contains("turn off theft guard") || q.contains("anti theft off") -> {
                try {
                    com.example.AuraApplication.instance.theftGuardManager.disarmGuard()
                    preferences.toggleSecurityAntiTheft = false
                } catch (e: Exception) { }
                LlmAssistantReply(
                    spokenResponse = "Theft Guard disarm kar diya gaya hai aur alarm band ho gaya hai.",
                    toolCommand = "disarm_theft_guard"
                )
            }

            // Media search & playback (YouTube & Spotify)
            q.contains("youtube par") || q.contains("play") && q.contains("youtube") -> {
                val song = q.replace("youtube par", "")
                    .replace("play", "")
                    .replace("on youtube", "")
                    .replace("chalao", "")
                    .replace("chala do", "")
                    .replace("sunao", "")
                    .replace("dikhao", "").trim()
                LlmAssistantReply(
                    spokenResponse = "YouTube par $song chala rahi hun.",
                    toolCommand = "open_youtube",
                    toolArg1 = song
                )
            }
            q.contains("spotify par") || q.contains("gana sunao") || q.contains("gana bajao") || q.contains("play music") -> {
                val song = q.replace("spotify par", "")
                    .replace("gana sunao", "")
                    .replace("gana bajao", "")
                    .replace("play music", "")
                    .replace("chalao", "")
                    .replace("play", "").trim()
                LlmAssistantReply(
                    spokenResponse = "Spotify par ${if (song.isNotBlank()) song else "music"} play kar rahi hun.",
                    toolCommand = "open_spotify",
                    toolArg1 = song
                )
            }

            // Maps & Navigation (rasta dikhao, navigate to)
            q.contains("rasta dikhao") || q.contains("kahan hai") || q.contains("navigate to") || q.contains("directions to") -> {
                val place = q.replace("rasta dikhao", "")
                    .replace("ka rasta", "")
                    .replace("kahan hai", "")
                    .replace("navigate to", "")
                    .replace("directions to", "").trim()
                LlmAssistantReply(
                    spokenResponse = "$place ke liye Google Maps khol rahi hun.",
                    toolCommand = "open_maps",
                    toolArg1 = place
                )
            }

            // Phone Calls (call, dial, phone, ring, call lagao, call karo)
            q.contains("call") || q.contains("dial") || q.contains("phone") || q.contains("ring") || q.contains("lagao") -> {
                val target = q.replace("make a call to", "")
                    .replace("call lagao", "")
                    .replace("call karo", "")
                    .replace("phone lagao", "")
                    .replace("dial", "")
                    .replace("call", "")
                    .replace("phone", "")
                    .replace("ko", "")
                    .replace("to", "").trim()
                LlmAssistantReply(
                    spokenResponse = if (target.isNotBlank()) "$target ko call dial kar rahi hun." else "Calling...",
                    toolCommand = "make_call",
                    toolArg1 = target
                )
            }

            // WhatsApp & Messages
            q.contains("whatsapp") -> {
                val msgText = q.replace("send whatsapp to", "")
                    .replace("whatsapp message", "")
                    .replace("whatsapp par", "")
                    .replace("whatsapp", "").trim()
                LlmAssistantReply(
                    spokenResponse = "WhatsApp message composer open kar rahi hun.",
                    toolCommand = "send_whatsapp",
                    toolArg1 = "",
                    toolArg2 = if (msgText.isNotBlank()) msgText else "Hello"
                )
            }
            q.contains("sms") || q.contains("message") || q.contains("text") || q.contains("bhejo") -> {
                val msgText = q.replace("send message", "")
                    .replace("send sms", "")
                    .replace("bhejo", "")
                    .replace("message", "").trim()
                LlmAssistantReply(
                    spokenResponse = "SMS composer open kar rahi hun.",
                    toolCommand = "send_sms",
                    toolArg1 = "",
                    toolArg2 = msgText
                )
            }

            // Wi-Fi Controls (toggle or specific ON/OFF)
            q.contains("wifi") || q.contains("wi-fi") -> {
                val turnOff = q.contains("off") || q.contains("band") || q.contains("disable") || q.contains("hatao")
                val turnOn = q.contains("on") || q.contains("chalu") || q.contains("enable") || q.contains("kholo")
                if (turnOff) {
                    LlmAssistantReply(spokenResponse = "Wi-Fi band kar rahi hun.", toolCommand = "toggle_wifi", toolArg1 = "false")
                } else if (turnOn) {
                    LlmAssistantReply(spokenResponse = "Wi-Fi chalu kar rahi hun.", toolCommand = "toggle_wifi", toolArg1 = "true")
                } else {
                    LlmAssistantReply(spokenResponse = "Wi-Fi toggle kar rahi hun.", toolCommand = "toggle_wifi")
                }
            }

            // Bluetooth Controls (toggle or specific ON/OFF)
            q.contains("bluetooth") -> {
                val turnOff = q.contains("off") || q.contains("band") || q.contains("disable") || q.contains("hatao")
                val turnOn = q.contains("on") || q.contains("chalu") || q.contains("enable") || q.contains("kholo")
                if (turnOff) {
                    LlmAssistantReply(spokenResponse = "Bluetooth band kar rahi hun.", toolCommand = "toggle_bluetooth", toolArg1 = "false")
                } else if (turnOn) {
                    LlmAssistantReply(spokenResponse = "Bluetooth on kar rahi hun.", toolCommand = "toggle_bluetooth", toolArg1 = "true")
                } else {
                    LlmAssistantReply(spokenResponse = "Bluetooth toggle kar rahi hun.", toolCommand = "toggle_bluetooth")
                }
            }

            // Screen Brightness Controls
            q.contains("brightness badhao") || q.contains("brightness up") || q.contains("increase brightness") || q.contains("roshni badhao") -> {
                LlmAssistantReply(spokenResponse = "Screen brightness badha rahi hun.", toolCommand = "adjust_brightness", toolArg1 = "true")
            }
            q.contains("brightness kam") || q.contains("brightness down") || q.contains("decrease brightness") || q.contains("roshni kam") -> {
                LlmAssistantReply(spokenResponse = "Screen brightness kam kar rahi hun.", toolCommand = "adjust_brightness", toolArg1 = "false")
            }
            q.contains("brightness") && Regex("\\d+").containsMatchIn(q) -> {
                val pct = Regex("(\\d+)").find(q)?.groupValues?.get(1)?.toIntOrNull() ?: 60
                LlmAssistantReply(spokenResponse = "Brightness $pct percent par set kar rahi hun.", toolCommand = "set_brightness", toolArg1 = pct.toString())
            }
            q.contains("brightness") || q.contains("display") -> {
                LlmAssistantReply(
                    spokenResponse = "Display settings open kar rahi hun.",
                    toolCommand = "open_settings",
                    toolArg1 = "display"
                )
            }
            q.contains("open settings") || q.contains("settings kholo") || q.contains("setting kholo") -> {
                LlmAssistantReply(
                    spokenResponse = "Device settings khol rahi hun.",
                    toolCommand = "open_settings",
                    toolArg1 = "general"
                )
            }

            // Specific App Launchers
            q.contains("youtube") && (q.contains("kholo") || q.contains("open") || q.contains("launch") || q.contains("start")) -> {
                LlmAssistantReply(spokenResponse = "YouTube open kar rahi hun.", toolCommand = "open_app", toolArg1 = "youtube")
            }
            q.contains("whatsapp") && (q.contains("kholo") || q.contains("open") || q.contains("launch") || q.contains("start")) -> {
                LlmAssistantReply(spokenResponse = "WhatsApp open kar rahi hun.", toolCommand = "open_app", toolArg1 = "whatsapp")
            }
            q.contains("spotify") && (q.contains("kholo") || q.contains("open") || q.contains("launch") || q.contains("start")) -> {
                LlmAssistantReply(spokenResponse = "Spotify open kar rahi hun.", toolCommand = "open_app", toolArg1 = "spotify")
            }
            q.contains("camera") || q.contains("photo khincho") || q.contains("selfie") -> {
                LlmAssistantReply(spokenResponse = "Camera open kar rahi hun.", toolCommand = "open_app", toolArg1 = "camera")
            }
            q.contains("calculator") || q.contains("hisab") -> {
                LlmAssistantReply(spokenResponse = "Calculator open kar rahi hun.", toolCommand = "open_app", toolArg1 = "calculator")
            }
            q.contains("chrome") || q.contains("browser") -> {
                LlmAssistantReply(spokenResponse = "Browser open kar rahi hun.", toolCommand = "open_app", toolArg1 = "chrome")
            }
            q.contains("maps") || q.contains("google maps") -> {
                LlmAssistantReply(spokenResponse = "Google Maps open kar rahi hun.", toolCommand = "open_app", toolArg1 = "maps")
            }
            q.contains("instagram") -> {
                LlmAssistantReply(spokenResponse = "Instagram open kar rahi hun.", toolCommand = "open_app", toolArg1 = "instagram")
            }
            q.contains("telegram") -> {
                LlmAssistantReply(spokenResponse = "Telegram open kar rahi hun.", toolCommand = "open_app", toolArg1 = "telegram")
            }
            q.contains("gmail") || q.contains("email") -> {
                LlmAssistantReply(spokenResponse = "Gmail open kar rahi hun.", toolCommand = "open_app", toolArg1 = "gmail")
            }
            q.contains("gallery") || q.contains("photos") -> {
                LlmAssistantReply(spokenResponse = "Photos open kar rahi hun.", toolCommand = "open_app", toolArg1 = "photos")
            }

            // Generic "open [app]" or "launch [app]" or "[app] kholo"
            q.startsWith("open ") || q.startsWith("launch ") || q.startsWith("start ") || q.endsWith(" kholo") || q.contains(" open karo") -> {
                val appName = q.replace("open ", "")
                    .replace("launch ", "")
                    .replace("start ", "")
                    .replace(" kholo", "")
                    .replace(" open karo", "")
                    .replace(" app", "")
                    .trim()
                LlmAssistantReply(
                    spokenResponse = "$appName open kar rahi hun.",
                    toolCommand = "open_app",
                    toolArg1 = appName
                )
            }

            // Web Search — STRICT: require explicit search keywords!
            q.startsWith("search") || q.contains(" search ") || q.contains("google") || q.contains("khojo") || q.startsWith("what is") || q.startsWith("who is") || q.startsWith("look up") -> {
                val searchQuery = q.replace("search for", "")
                    .replace("search", "")
                    .replace("google", "")
                    .replace("par khojo", "")
                    .replace("khojo", "")
                    .replace("look up", "").trim()
                LlmAssistantReply(
                    spokenResponse = "Google par '${if (searchQuery.isNotBlank()) searchQuery else query}' search kar rahi hun.",
                    toolCommand = "search_web",
                    toolArg1 = if (searchQuery.isNotBlank()) searchQuery else query
                )
            }

            // Greeting & Assistant Identity
            q.contains("hello") || q.contains("hi aura") || q.contains("hey aura") || q.contains("namaste") -> {
                LlmAssistantReply(spokenResponse = "Namaste $userName! Main Aura hun, aapki smart personal assistant. Bataiye main aapke phone par kya control karun?")
            }
            q.contains("who are you") || q.contains("tum kaun ho") || q.contains("aap kaun ho") -> {
                LlmAssistantReply(spokenResponse = "Main Aura hun, aapki fully authorized smart voice assistant. Main aapke phone ki calls, messages, flashlight, volume, apps, alarm aur navigation sab control kar sakti hun.")
            }

            // Conversational fallback — DO NOT trigger search_web by default!
            else -> {
                LlmAssistantReply(
                    spokenResponse = "Aapka aadesh samajh aa gaya hai $userName. Main action process kar rahi hun.",
                    toolCommand = null
                )
            }
        }
    }
}
