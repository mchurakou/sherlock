package com.example.sherlock.config

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.ChatClientRequest
import org.springframework.ai.chat.client.ChatClientResponse
import org.springframework.ai.chat.client.advisor.api.CallAdvisor
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.core.io.ByteArrayResource
import reactor.core.publisher.Flux

class ImageAdvisor private constructor(
    private val visionChatClient: ChatClient,
    private val imageDescriptionPrompt: String,
    private val order: Int,
) : CallAdvisor, StreamAdvisor {

    override fun getName(): String = "ImageAdvisor"

    override fun getOrder(): Int = order

    override fun adviseCall(request: ChatClientRequest, chain: CallAdvisorChain) =
        chain.nextCall(enrich(request))

    override fun adviseStream(request: ChatClientRequest, chain: StreamAdvisorChain): Flux<ChatClientResponse> =
        chain.nextStream(enrich(request))

    private fun enrich(request: ChatClientRequest): ChatClientRequest {
        val prompt = request.prompt()
        val messages = prompt.instructions
        val userMsg = messages.filterIsInstance<UserMessage>().lastOrNull { it.media.isNotEmpty() }
            ?: return request

        val description = visionChatClient.prompt()
            .user { spec ->
                spec.text(imageDescriptionPrompt)
                userMsg.media.forEach { m -> spec.media(m.mimeType, ByteArrayResource(m.dataAsByteArray)) }
            }
            .call()
            .content() ?: ""

        if (description.isBlank()) {
            return request
        }

        val enrichedText = "${userMsg.text}\n\n$description"
        val newUserMsg = userMsg.mutate()
            .text(enrichedText)
            .media(emptyList())
            .build()
        val newMessages = messages.map { if (it === userMsg) newUserMsg else it }
        return request.mutate().prompt(Prompt(newMessages, prompt.options)).build()
    }

    class Builder(
        private val visionChatClient: ChatClient,
        private val imageDescriptionPrompt: String,
    ) {
        private var order: Int = -1

        fun order(order: Int) = apply { this.order = order }

        fun build() = ImageAdvisor(visionChatClient, imageDescriptionPrompt, order)
    }

    companion object {
        fun builder(visionChatClient: ChatClient, imageDescriptionPrompt: String) =
            Builder(visionChatClient, imageDescriptionPrompt)
    }
}
