package com.example.sherlock.config

import io.micrometer.common.KeyValue
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationFilter
import org.springframework.ai.chat.observation.ChatModelObservationContext
import org.springframework.stereotype.Component
import org.springframework.util.CollectionUtils

/**
 * Adds gen_ai.prompt and gen_ai.completion as high-cardinality span attributes so
 * Langfuse can display prompt/completion content on generation observations.
 *
 * Required even when spring.ai.chat.observations.log-prompt/log-completion are true,
 * because those flags only enable data collection inside Spring AI's context — they do
 * not automatically export the content as OTel span attributes.
 */
@Component
class ChatModelCompletionContentObservationFilter : ObservationFilter {

    override fun map(context: Observation.Context): Observation.Context {
        if (context !is ChatModelObservationContext) return context

        context.addHighCardinalityKeyValue(KeyValue.of("gen_ai.prompt", processPrompts(context)))
        context.addHighCardinalityKeyValue(KeyValue.of("gen_ai.completion", processCompletion(context)))

        return context
    }

    private fun processPrompts(context: ChatModelObservationContext): String =
        context.request.instructions
            .takeUnless { CollectionUtils.isEmpty(it) }
            ?.mapNotNull { it.text?.takeIf(String::isNotBlank) }
            ?.joinToString("\n")
            ?: ""

    private fun processCompletion(context: ChatModelObservationContext): String =
        context.response?.results
            .takeUnless { CollectionUtils.isEmpty(it) }
            ?.mapNotNull { it.output.text?.takeIf(String::isNotBlank) }
            ?.joinToString("\n")
            ?: ""
}
