package com.example.sherlock.service

import com.example.sherlock.dto.*
import com.example.sherlock.entity.Chat
import com.example.sherlock.entity.Message
import com.example.sherlock.entity.MessageRole
import com.example.sherlock.repository.ChatRepository
import org.springframework.ai.chat.client.ChatClient
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Service
@Transactional(readOnly = true)
class ChatService(
    private val chatRepository: ChatRepository,
    private val chatClient: ChatClient
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

    @Transactional
    fun saveUserMessage(chatId: Long, content: String) {
        val chat = chatRepository.findById(chatId).orElseThrow { ChatNotFoundException(chatId) }
        if (chat.messages.isEmpty()) {
            chat.title = content.take(50)
        }
        chat.messages.add(Message(chat = chat, content = content, role = MessageRole.USER))
        chatRepository.save(chat)
    }

    @Transactional
    fun saveAssistantMessage(chatId: Long, content: String) {
        val chat = chatRepository.findById(chatId).orElseThrow { ChatNotFoundException(chatId) }
        chat.messages.add(Message(chat = chat, content = content, role = MessageRole.ASSISTANT))
        chatRepository.save(chat)
    }

    companion object {
        private val TITLE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.systemDefault())
    }

    fun streamLlm(userMessage: String): Flux<String> =
        chatClient.prompt().user(userMessage).stream().content()

    private fun Chat.toSummary() = ChatSummaryResponse(
        id = id, title = title, createdAt = createdAt
    )

    private fun Chat.toDetail() = ChatDetailResponse(
        id = id, title = title, createdAt = createdAt,
        messages = messages.map { it.toResponse() }
    )

    private fun Message.toResponse() = MessageResponse(
        id = id, content = content, role = role.name, createdAt = createdAt
    )
}
