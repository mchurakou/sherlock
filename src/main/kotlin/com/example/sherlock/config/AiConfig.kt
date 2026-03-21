package com.example.sherlock.config

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.chat.prompt.PromptTemplate
import org.springframework.ai.chat.prompt.SystemPromptTemplate
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource

@Configuration
class AiConfig {

    @Value("classpath:prompts/system.st")
    private lateinit var systemPromptResource: Resource

    @Value("classpath:prompts/rag-question-answer.st")
    private lateinit var ragQuestionAnswerPromptResource: Resource


    @Bean
    fun chatClient(builder: ChatClient.Builder, chatMemory: ChatMemory, vectorStore: VectorStore): ChatClient = builder
        .defaultOptions(OpenAiChatOptions.builder().temperature(0.5).build())
        .defaultSystem(SystemPromptTemplate(systemPromptResource).render())
        .defaultAdvisors(
            MessageChatMemoryAdvisor.builder(chatMemory).order(0).build(),
            QuestionAnswerAdvisor.builder(vectorStore)
                .promptTemplate(PromptTemplate(ragQuestionAnswerPromptResource))
                .searchRequest(
                    SearchRequest.builder()
                        .similarityThreshold(0.5)
                        .topK(1)
                        .build()
                )
                .order(1)
                .build(),
        )
        .build()
}
