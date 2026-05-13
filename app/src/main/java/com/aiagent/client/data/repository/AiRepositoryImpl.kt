package com.aiagent.client.data.repository

import com.aiagent.client.domain.repository.AiRepository
import com.aiagent.client.domain.repository.TokenUsage
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.sse.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiRepositoryImpl @Inject constructor() : AiRepository {

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(io.ktor.client.plugins.SSE) {
            reconnectionTime = 3000
        }
    }

    override fun streamCompletion(
        messages: List<Map<String, String>>,
        baseUrl: String,
        apiKey: String,
        model: String
    ): Flow<Pair<String, TokenUsage?>> = callbackFlow {
        try {
            val url = if (baseUrl.endsWith("/")) "${baseUrl}chat/completions" else "$baseUrl/chat/completions"
            
            httpClient.sse(url = url) {
                header("Authorization", "Bearer $apiKey")
                header("Content-Type", "application/json")
                
                setBody(
                    mapOf(
                        "model" to model,
                        "messages" to messages,
                        "stream" to true
                    )
                )
            }.collect { event ->
                val data = event.data ?: return@collect
                
                if (data == "[DONE]") {
                    close()
                    return@collect
                }
                
                try {
                    val json = Json.parseToJsonElement(data).jsonObject
                    val choices = json["choices"]?.let { 
                        if (it is kotlinx.serialization.json.JsonArray) it else emptyList() 
                    }
                    
                    var content: String? = null
                    var tokenUsage: TokenUsage? = null
                    
                    choices?.forEach { choice ->
                        if (choice is kotlinx.serialization.json.JsonObject) {
                            val delta = choice["delta"]
                            if (delta is kotlinx.serialization.json.JsonObject) {
                                val deltaContent = delta["content"]
                                if (deltaContent is kotlinx.serialization.json.JsonPrimitive && deltaContent.isString) {
                                    content = deltaContent.content
                                }
                            }
                        }
                    }
                    
                    val usage = json["usage"]
                    if (usage is kotlinx.serialization.json.JsonObject) {
                        val promptTokens = (usage["prompt_tokens"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() ?: 0
                        val completionTokens = (usage["completion_tokens"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() ?: 0
                        val totalTokens = (usage["total_tokens"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() ?: 0
                        tokenUsage = TokenUsage(promptTokens, completionTokens, totalTokens)
                    }
                    
                    if (!content.isNullOrBlank()) {
                        trySend(content to tokenUsage)
                    }
                } catch (e: Exception) {
                    // Parse error, continue
                }
            }
        } catch (e: Exception) {
            close(e)
        }
        
        awaitClose {
            httpClient.close()
        }
    }
}
