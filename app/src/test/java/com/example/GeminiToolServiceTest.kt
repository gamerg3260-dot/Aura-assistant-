package com.example

import com.example.data.api.GeminiCandidate
import com.example.data.api.GeminiContent
import com.example.data.api.GeminiFunctionCall
import com.example.data.api.GeminiGenerateContentRequest
import com.example.data.api.GeminiGenerateContentResponse
import com.example.data.api.GeminiPart
import com.example.data.api.ToolCallRequest
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiToolServiceTest {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun testSerializationOfGeminiRequest() {
        val request = GeminiGenerateContentRequest(
            contents = listOf(
                GeminiContent(
                    role = "user",
                    parts = listOf(GeminiPart(text = "Turn on flashlight"))
                )
            )
        )
        val adapter = moshi.adapter(GeminiGenerateContentRequest::class.java)
        val json = adapter.toJson(request)
        assertTrue(json.contains("Turn on flashlight"))
        assertTrue(json.contains("\"role\":\"user\""))
    }

    @Test
    fun testDeserializationOfGeminiFunctionCallResponse() {
        val sampleResponseJson = """
            {
                "candidates": [
                    {
                        "content": {
                            "role": "model",
                            "parts": [
                                {
                                    "functionCall": {
                                        "name": "set_timer",
                                        "args": {
                                            "seconds": 300,
                                            "label": "Tea"
                                        }
                                    }
                                }
                            ]
                        },
                        "finishReason": "STOP"
                    }
                ]
            }
        """.trimIndent()

        val adapter = moshi.adapter(GeminiGenerateContentResponse::class.java)
        val response = adapter.fromJson(sampleResponseJson)

        assertNotNull(response)
        assertNotNull(response?.candidates)
        val firstCandidate = response?.candidates?.first()
        val functionCall = firstCandidate?.content?.parts?.first()?.functionCall

        assertNotNull(functionCall)
        assertEquals("set_timer", functionCall?.name)
        assertEquals(300.0, functionCall?.args?.get("seconds"))
        assertEquals("Tea", functionCall?.args?.get("label"))
    }

    @Test
    fun testToolCallRegexParsing() {
        val text = "Sure! I am setting that up right away. [TOOL:set_timer:120:Quick Timer]"
        val toolRegex = Regex("\\[TOOL:([A-Za-z0-9_]+)(?::([^:\\]]+))?(?::([^\\]]+))?\\]")
        val match = toolRegex.find(text)

        assertNotNull(match)
        assertEquals("set_timer", match?.groupValues?.get(1))
        assertEquals("120", match?.groupValues?.get(2))
        assertEquals("Quick Timer", match?.groupValues?.get(3))
    }
}
