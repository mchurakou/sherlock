package com.example.sherlock.controller

import com.example.sherlock.dto.*
import com.example.sherlock.service.ChatService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@RestController
@RequestMapping("/api/chats")
@Tag(name = "Chats", description = "Chat management endpoints")
class ChatController(
    private val chatService: ChatService
) {

    @GetMapping
    @Operation(summary = "List all chats")
    fun listChats(): List<ChatSummaryResponse> =
        chatService.listChats()

    @GetMapping("/{id}")
    @Operation(summary = "Get chat with all messages")
    fun getChat(@PathVariable id: Long): ChatDetailResponse =
        chatService.getChat(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new empty chat")
    fun createChat(): ChatDetailResponse =
        chatService.createChat()

    @PostMapping("/{id}/messages", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @Operation(summary = "Add a message with streaming LLM response")
    fun addMessageStream(
        @PathVariable id: Long,
        @RequestBody request: AddMessageRequest
    ): Flux<String> {
        chatService.saveUserMessage(id, request.content)
        val buffer = StringBuilder()
        return chatService.streamLlm(request.content)
            .doOnNext { buffer.append(it) }
            .concatWith(
                Mono.fromRunnable<String> { chatService.saveAssistantMessage(id, buffer.toString()) }
                    .subscribeOn(Schedulers.boundedElastic())
            )
    }
}
