package com.example.sherlock.dto

import java.time.Instant

data class AddMessageRequest(
    val content: String
)

data class ChatSummaryResponse(
    val id: Long,
    val title: String?,
    val createdAt: Instant
)

data class ChatDetailResponse(
    val id: Long,
    val title: String?,
    val createdAt: Instant,
    val messages: List<MessageResponse>
)

data class MessageResponse(
    val id: Long,
    val content: String,
    val role: String,
    val createdAt: Instant
)
