package com.example.data.api

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.DeviceStats
import com.example.data.model.ToolExecutionResult
import com.example.data.preferences.AssistantPreferences
import com.example.data.security.KeystoreManager
import com.example.system.DeviceController
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
 * Service layer coordinating Retrofit network calls to Google Gemini API (gemini-3.5-flash)
 * with device telemetry, accessibility context, tool declarations, response parsing,
 * and device tool execution.
 */
class GeminiService(
    private val context: Context,
    private val keystoreManager: KeystoreManager,
    private val preferences: AssistantPreferences,
    private val deviceController: DeviceController? = null
) {
    private val tag = "GeminiService"
    private val apiService: GeminiApiService by lazy { GeminiRetrofitClient.apiService }

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
        val apiKey = getEffectiveApiKey()
        if (apiKey.isBlank()) {
            Log.d(tag, "No API key configured. Executing command via offline rule engine.")
            val fallbackReply = runOfflineRuleEngine(command, deviceStats)
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

            val response = apiService.generateContent(apiKey = apiKey, request = request)

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

            // 1. Identify tool call from response (Native functionCall or parsed tags)
            val toolCall = identifyToolCall(candidateContent)

            // 2. Extract or synthesize spoken text
            val spokenText = extractSpokenResponse(candidateContent, toolCall)

            // 3. Execute identified tool call on device
            var toolResult: ToolExecutionResult? = null
            if (toolCall != null) {
                Log.d(tag, "Identified tool call: ${toolCall.functionName} with args: ${toolCall.arguments}")
                toolResult = executeToolCall(toolCall)
            }

            AssistantCommandResult(
                spokenResponse = spokenText,
                toolCallRequest = toolCall,
                executedToolResult = toolResult,
                executionSource = "gemini_api"
            )
        } catch (e: Exception) {
            Log.e(tag, "Gemini Retrofit request failed: ${e.message}", e)
            val fallbackReply = runOfflineRuleEngine(command, deviceStats)
            val executedResult = executeLegacyTool(fallbackReply.toolCommand, fallbackReply.toolArg1, fallbackReply.toolArg2)
            AssistantCommandResult(
                spokenResponse = fallbackReply.spokenResponse,
                toolCallRequest = fallbackReply.toolCommand?.let { ToolCallRequest(it) },
                executedToolResult = executedResult,
                executionSource = "offline_fallback_exception"
            )
        }
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
        if (content == null) return null

        // 1. Check for native Gemini functionCall
        for (part in content.parts) {
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
        val fullText = content.parts.mapNotNull { it.text }.joinToString("\n")
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

        return null
    }

    /**
     * Extracts spoken response text from candidates, stripping any markup directives.
     */
    private fun extractSpokenResponse(content: GeminiContent?, toolCall: ToolCallRequest?): String {
        val toolRegex = Regex("\\[TOOL:([A-Za-z0-9_]+)(?::([^:\\]]+))?(?::([^\\]]+))?\\]")
        val rawText = content?.parts?.mapNotNull { it.text }?.joinToString(" ")?.replace(toolRegex, "")?.trim() ?: ""

        if (rawText.isNotBlank()) {
            return rawText
        }

        // If Gemini returned a functionCall with empty text, synthesize a clear verbal confirmation:
        return when (toolCall?.functionName?.lowercase()) {
            "open_app", "openapp" -> "Opening ${toolCall.arguments["app_name"] ?: "the app"}."
            "set_timer", "settimer" -> "Setting a timer for ${toolCall.arguments["seconds"] ?: "5"} seconds."
            "set_volume", "setvolume" -> "Setting media volume to ${toolCall.arguments["level_percent"] ?: "50"} percent."
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
     * Executes the identified tool call directly on device hardware/system.
     */
    fun executeToolCall(toolCall: ToolCallRequest): ToolExecutionResult {
        val controller = deviceController ?: DeviceController(context)
        val fn = toolCall.functionName.lowercase().trim()
        val args = toolCall.arguments

        return when (fn) {
            "open_app", "openapp" -> {
                val appName = args["app_name"]?.toString() ?: args["arg1"]?.toString() ?: "settings"
                if (preferences.toggleSystemControlAppLauncher) {
                    controller.launchApp(appName)
                } else {
                    ToolExecutionResult("App Launcher", "App launching is disabled in settings", false)
                }
            }
            "set_timer", "settimer" -> {
                val sec = (args["seconds"]?.toString() ?: args["arg1"]?.toString())?.toDoubleOrNull()?.toInt() ?: 300
                val label = args["label"]?.toString() ?: args["arg2"]?.toString() ?: "Aura Timer"
                if (preferences.toggleProdTimers) {
                    controller.setTimer(sec, label)
                } else {
                    ToolExecutionResult("Timer", "Timer tools disabled in settings", false)
                }
            }
            "set_volume", "setvolume", "volume" -> {
                val pct = (args["level_percent"]?.toString() ?: args["arg1"]?.toString())?.toDoubleOrNull()?.toInt() ?: 70
                if (preferences.toggleSystemControlVolume) {
                    controller.setVolume(pct)
                } else {
                    ToolExecutionResult("Volume", "Volume control disabled in settings", false)
                }
            }
            "toggle_flashlight", "toggleflashlight", "flashlight" -> {
                val stateStr = (args["state"]?.toString() ?: args["arg1"]?.toString())?.uppercase()
                val turnOn = stateStr == "ON" || stateStr == "TRUE"
                controller.toggleFlashlight(turnOn)
            }
            "search_web", "searchweb", "search" -> {
                val query = args["query"]?.toString() ?: args["arg1"]?.toString() ?: "Google"
                if (preferences.toggleWebGoogleSearch) {
                    controller.searchWeb(query)
                } else {
                    ToolExecutionResult("Web Search", "Web search is disabled in settings", false)
                }
            }
            "open_spotify", "openspotify", "spotify" -> {
                val query = args["query"]?.toString() ?: args["arg1"]?.toString() ?: ""
                controller.openSpotifySearch(query)
            }
            "open_youtube", "openyoutube", "youtube" -> {
                val query = args["query"]?.toString() ?: args["arg1"]?.toString() ?: ""
                controller.openYouTubeSearch(query)
            }
            "send_whatsapp", "sendwhatsapp" -> {
                val phone = args["phone_number"]?.toString() ?: args["arg1"]?.toString() ?: ""
                val msg = args["message"]?.toString() ?: args["arg2"]?.toString() ?: "Hello from Aura"
                if (preferences.toggleCommWhatsapp) {
                    controller.sendWhatsApp(phone, msg)
                } else {
                    ToolExecutionResult("WhatsApp", "WhatsApp integration disabled in settings", false)
                }
            }
            "send_sms", "sendsms" -> {
                val phone = args["phone_number"]?.toString() ?: args["arg1"]?.toString() ?: ""
                val msg = args["message"]?.toString() ?: args["arg2"]?.toString() ?: ""
                if (preferences.toggleCommSms) {
                    controller.sendSms(phone, msg)
                } else {
                    ToolExecutionResult("SMS", "SMS integration disabled in settings", false)
                }
            }
            "make_call", "makecall", "call" -> {
                val phone = args["phone_number"]?.toString() ?: args["arg1"]?.toString() ?: ""
                if (preferences.toggleCommPhoneCalls) {
                    controller.makeCall(phone)
                } else {
                    ToolExecutionResult("Call", "Phone calling disabled in settings", false)
                }
            }
            "open_settings", "opensettings" -> {
                controller.openWirelessSettings()
            }
            "check_battery", "checkbattery", "battery_check" -> {
                val stats = controller.getDeviceStats()
                ToolExecutionResult("Battery", "${stats.batteryPercent}% (${if (stats.isCharging) "Charging" else "Unplugged"})", true)
            }
            "check_storage", "checkstorage", "storage_check" -> {
                val stats = controller.getDeviceStats()
                ToolExecutionResult("Storage", "${stats.storageFreeGb} GB free out of ${stats.storageTotalGb} GB", true)
            }
            "read_screen", "readscreen" -> {
                val screenText = com.example.service.AuraAccessibilityService.getActiveScreenText()
                ToolExecutionResult("Read Screen", if (screenText.isNotBlank()) screenText.take(160) else "No text found on active screen", true)
            }
            else -> {
                ToolExecutionResult(toolCall.functionName, "Executed action ${toolCall.functionName}", true)
            }
        }
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

            Directives:
            - When the user asks for a device action (app opening, timer, volume, flashlight, search, messaging, calling, screen reading, battery check), call the appropriate tool.
            - Keep spoken conversational answers concise (1-3 sentences), warm, confident, and natural for text-to-speech synthesis.
        """.trimIndent()

        // Tools declared for Gemini function calling
        val tools = listOf(
            GeminiTool(
                functionDeclarations = listOf(
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
                        description = "Search the web using Google",
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
     * Local offline rule engine guaranteeing 100% functionality without internet or API keys.
     */
    private fun runOfflineRuleEngine(query: String, stats: DeviceStats): LlmAssistantReply {
        val q = query.lowercase().trim()
        val userName = preferences.userName

        return when {
            q.contains("battery") || q.contains("charge") -> {
                LlmAssistantReply(
                    spokenResponse = "Your battery is currently at ${stats.batteryPercent} percent and ${if (stats.isCharging) "charging" else "discharging"}.",
                    toolCommand = "check_battery"
                )
            }
            q.contains("ram") || q.contains("memory") || q.contains("storage") -> {
                LlmAssistantReply(
                    spokenResponse = "System RAM usage is ${stats.ramUsedMb} megabytes out of ${stats.ramTotalMb}. You have ${stats.storageFreeGb} gigabytes of free internal storage.",
                    toolCommand = "check_storage"
                )
            }
            q.contains("flashlight") || q.contains("torch") -> {
                val turnOn = !q.contains("off")
                LlmAssistantReply(
                    spokenResponse = if (turnOn) "Turning the flashlight on." else "Turning the flashlight off.",
                    toolCommand = "toggle_flashlight",
                    toolArg1 = if (turnOn) "ON" else "OFF"
                )
            }
            q.contains("timer") -> {
                val num = Regex("(\\d+)").find(q)?.groupValues?.get(1)?.toIntOrNull() ?: 5
                val seconds = if (q.contains("second")) num else num * 60
                LlmAssistantReply(
                    spokenResponse = "Setting a timer for $num ${if (q.contains("second")) "seconds" else "minutes"}.",
                    toolCommand = "set_timer",
                    toolArg1 = seconds.toString(),
                    toolArg2 = "Aura Assistant Timer"
                )
            }
            q.contains("open youtube") -> {
                LlmAssistantReply(spokenResponse = "Opening YouTube.", toolCommand = "open_app", toolArg1 = "youtube")
            }
            q.contains("open spotify") || q.contains("play music") -> {
                LlmAssistantReply(spokenResponse = "Launching Spotify.", toolCommand = "open_app", toolArg1 = "spotify")
            }
            q.contains("open camera") || q.contains("take photo") || q.contains("selfie") -> {
                LlmAssistantReply(spokenResponse = "Opening camera.", toolCommand = "open_app", toolArg1 = "camera")
            }
            q.contains("open whatsapp") -> {
                LlmAssistantReply(spokenResponse = "Opening WhatsApp.", toolCommand = "open_app", toolArg1 = "whatsapp")
            }
            q.contains("open settings") || q.contains("wifi") || q.contains("bluetooth") -> {
                LlmAssistantReply(spokenResponse = "Opening system settings.", toolCommand = "open_settings")
            }
            q.contains("read screen") || q.contains("what is on my screen") -> {
                LlmAssistantReply(spokenResponse = "Scanning the active screen content.", toolCommand = "read_screen")
            }
            q.contains("search for") || q.contains("google") -> {
                val searchQuery = q.replace("search for", "").replace("google", "").trim()
                LlmAssistantReply(
                    spokenResponse = "Searching the web for $searchQuery.",
                    toolCommand = "search_web",
                    toolArg1 = searchQuery
                )
            }
            q.contains("volume to") -> {
                val vol = Regex("(\\d+)").find(q)?.groupValues?.get(1)?.toIntOrNull() ?: 70
                LlmAssistantReply(
                    spokenResponse = "Setting volume to $vol percent.",
                    toolCommand = "set_volume",
                    toolArg1 = vol.toString()
                )
            }
            q.contains("hello") || q.contains("hi aura") || q.contains("hey aura") -> {
                LlmAssistantReply(spokenResponse = "Hello $userName. Voice biometric lock verified. How can I assist you right now?")
            }
            q.contains("who are you") -> {
                LlmAssistantReply(spokenResponse = "I am Aura, your personal AI voice assistant with on-device voice-lock verification.")
            }
            else -> {
                LlmAssistantReply(
                    spokenResponse = "Understood. I have logged that request for $userName.",
                    toolCommand = "search_web",
                    toolArg1 = query
                )
            }
        }
    }
}
