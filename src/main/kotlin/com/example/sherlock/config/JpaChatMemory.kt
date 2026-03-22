package com.example.sherlock.config

import com.example.sherlock.entity.Message as ChatMessage
import com.example.sherlock.entity.MessageRole
import com.example.sherlock.repository.ChatRepository
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.Base64

@Component
class JpaChatMemory(private val chatRepository: ChatRepository) : ChatMemory {

    @Transactional
    override fun add(conversationId: String, messages: List<Message>) {
        val chat = chatRepository.findByIdWithMessages(conversationId.toLong()).orElseThrow()
        if (chat.messages.isEmpty()) {
            messages.firstOrNull { it is UserMessage }?.let { chat.title = it.text.take(50) }
        }
        messages.forEach { msg ->
            val role = when (msg) {
                is UserMessage -> MessageRole.USER
                is AssistantMessage -> MessageRole.ASSISTANT
                else -> return@forEach
            }
            val firstMedia = if (msg is UserMessage) msg.media?.firstOrNull() else null
            val imageData = firstMedia?.let { media ->
                runCatching {
                    when (val d = media.data) {
                        is Resource -> Base64.getEncoder().encodeToString(d.inputStream.readBytes())
                        is ByteArray -> Base64.getEncoder().encodeToString(d)
                        else -> null
                    }
                }.getOrNull()
            }
            val imageMimeType = firstMedia?.mimeType?.toString()
            chat.messages.add(ChatMessage(chat = chat, content = msg.text ?: "", role = role,
                imageData = imageData, imageMimeType = imageMimeType))
        }
        chatRepository.save(chat)
    }

    @Transactional(readOnly = true)
    override fun get(conversationId: String): List<Message> {
        val chat = chatRepository.findByIdWithMessages(conversationId.toLong()).orElse(null) ?: return emptyList()
        return chat.messages.map { msg ->
            when (msg.role) {
                MessageRole.USER -> UserMessage(msg.content)
                MessageRole.ASSISTANT -> AssistantMessage(msg.content)
            }
        }
    }

    @Transactional
    override fun clear(conversationId: String) {
        val chat = chatRepository.findByIdWithMessages(conversationId.toLong()).orElse(null) ?: return
        chat.messages.clear()
        chatRepository.save(chat)
    }
}
