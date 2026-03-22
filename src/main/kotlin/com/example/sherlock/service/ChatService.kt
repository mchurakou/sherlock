package com.example.sherlock.service

import com.example.sherlock.dto.*
import com.example.sherlock.entity.Chat
import com.example.sherlock.repository.ChatRepository
import io.opentelemetry.api.trace.Tracer
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.core.io.ByteArrayResource
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.MimeType
import reactor.core.publisher.Flux
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64

@Service
@Transactional(readOnly = true)
class ChatService(
    private val chatRepository: ChatRepository,
    private val chatClient: ChatClient,
    private val tracer: Tracer
) {

    fun listChats(): List<ChatSummaryResponse> =
        chatRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
            .map { it.toSummary() }

    fun getChat(id: Long): ChatDetailResponse {
        val chat = chatRepository.findById(id)
            .orElseThrow { ChatNotFoundException(id) }
        return chat.toDetail()
    }

    @Transactional
    fun createChat(): ChatDetailResponse {
        val title = "New chat - " + TITLE_FORMATTER.format(java.time.Instant.now())
        return chatRepository.save(Chat(title = title)).toDetail()
    }

    fun streamLlm(chatId: Long, userMessage: String, imageBase64: String? = null, imageMimeType: String? = null): Flux<String> {
        val imageBytes = imageBase64?.let { Base64.getDecoder().decode(it) }
        val span = tracer.spanBuilder("user-interaction")
            .setAttribute("langfuse.session.id", chatId.toString())
            .setAttribute("langfuse.observation.input", userMessage)
            .startSpan()
        val scope = span.makeCurrent()
        val outputBuffer = StringBuilder()
        return chatClient.prompt()
            .user { spec ->
                spec.text(userMessage)
                if (imageBytes != null && imageMimeType != null) {
                    spec.media(MimeType.valueOf(imageMimeType), ByteArrayResource(imageBytes))
                }
            }
            .advisors { it.param(ChatMemory.CONVERSATION_ID, chatId.toString()) }
            .stream()
            .content()
            .doOnNext { token -> outputBuffer.append(token) }
            .doOnComplete { span.setAttribute("langfuse.observation.output", outputBuffer.toString()) }
            .doFinally { scope.close(); span.end() }
    }

    companion object {
        private val TITLE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.systemDefault())
    }

    private fun Chat.toSummary() = ChatSummaryResponse(
        id = id, title = title, createdAt = createdAt
    )

    private fun Chat.toDetail() = ChatDetailResponse(
        id = id, title = title, createdAt = createdAt,
        messages = messages.map { it.toResponse() }
    )

    private fun com.example.sherlock.entity.Message.toResponse() = MessageResponse(
        id = id, content = content, role = role.name, createdAt = createdAt,
        imageData = imageData, imageMimeType = imageMimeType
    )
}
