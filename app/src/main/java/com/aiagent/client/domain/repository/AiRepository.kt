package com.aiagent.client.domain.repository

import kotlinx.coroutines.flow.Flow

data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

interface AiRepository {
    fun streamCompletion(
        messages: List<Map<String, String>>,
        baseUrl: String,
        apiKey: String,
        model: String
    ): Flow<Pair<String, TokenUsage?>>
}
