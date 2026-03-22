package com.example.sherlock.config

import io.micrometer.common.KeyValue
import io.micrometer.observation.ObservationFilter
import io.micrometer.observation.ObservationPredicate
import org.springframework.ai.chat.client.observation.ChatClientObservationContext
import org.springframework.ai.chat.messages.MessageType
import org.springframework.ai.document.Document
import org.springframework.ai.embedding.observation.EmbeddingModelObservationContext
import org.springframework.ai.tool.observation.ToolCallingObservationContext
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ObservabilityConfig {

    /**
     * Suppress embedding observations (they are internal implementation detail).
     * Vector store observations are kept so we can see what was queried and returned.
     */
    @Bean
    fun suppressEmbeddingObservations(): ObservationPredicate =
        ObservationPredicate { _, context -> context !is EmbeddingModelObservationContext }

    /**
     * Shows the search query (input) and the matched documents (output)
     * on each Qdrant vector-store span in Langfuse.
     */
    @Bean
    fun vectorStoreInputOutputFilter(): ObservationFilter = ObservationFilter { context ->
        if (context !is VectorStoreObservationContext) return@ObservationFilter context

        val query = context.queryRequest?.query?.takeIf(String::isNotBlank)
        val docs = context.queryResponse

        if (query != null) {
            context.addHighCardinalityKeyValue(KeyValue.of("langfuse.observation.input", query))
        }
        if (!docs.isNullOrEmpty()) {
            val output = docs.joinToString("\n\n") { doc -> formatDocument(doc) }
            context.addHighCardinalityKeyValue(KeyValue.of("langfuse.observation.output", output))
        }

        context
    }

    /**
     * Populates langfuse.observation.input/output on the root chat-client span so that
     * Langfuse shows the user message and assistant reply at the trace level.
     */
    @Bean
    fun chatClientInputOutputFilter(): ObservationFilter = ObservationFilter { context ->
        if (context !is ChatClientObservationContext) return@ObservationFilter context

        val userText = context.request.prompt.instructions
            .filter { it.messageType == MessageType.USER }
            .mapNotNull { it.text?.takeIf(String::isNotBlank) }
            .lastOrNull()

        val assistantText = context.response?.chatResponse?.result?.output?.text
            ?.takeIf(String::isNotBlank)

        if (userText != null) {
            context.addHighCardinalityKeyValue(KeyValue.of("langfuse.observation.input", userText))
        }
        if (assistantText != null) {
            context.addHighCardinalityKeyValue(KeyValue.of("langfuse.observation.output", assistantText))
        }

        context
    }

    /**
     * Populates langfuse.observation.input/output on tool-calling spans so that
     * Langfuse shows the tool arguments and result for each tool invocation.
     */
    @Bean
    fun toolCallingInputOutputFilter(): ObservationFilter = ObservationFilter { context ->
        if (context !is ToolCallingObservationContext) return@ObservationFilter context

        val input = context.toolCallArguments?.takeIf(String::isNotBlank)
        val output = context.toolCallResult?.takeIf(String::isNotBlank)

        if (input != null) {
            context.addHighCardinalityKeyValue(KeyValue.of("langfuse.observation.input", input))
        }
        if (output != null) {
            context.addHighCardinalityKeyValue(KeyValue.of("langfuse.observation.output", output))
        }

        context
    }

    private fun formatDocument(doc: Document): String {
        val sb = StringBuilder(doc.text)
        val meta = doc.metadata
        if (meta.isNotEmpty()) {
            sb.append("\n[metadata: ")
            sb.append(meta.entries.joinToString(", ") { (k, v) -> "$k=$v" })
            sb.append("]")
        }
        return sb.toString()
    }
}
