package com.example.sherlock.config

import com.example.sherlock.entity.Message as ChatMessage
import com.example.sherlock.entity.MessageRole
import com.example.sherlock.repository.ChatRepository
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

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
            chat.messages.add(ChatMessage(chat = chat, content = msg.text!!, role = role))
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
