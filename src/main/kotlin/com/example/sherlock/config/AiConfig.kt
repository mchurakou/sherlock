package com.example.sherlock.config

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.prompt.SystemPromptTemplate
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource

@Configuration
class AiConfig {

    @Value("classpath:prompts/system.st")
    private lateinit var systemPromptResource: Resource

    @Bean
    fun chatClient(builder: ChatClient.Builder): ChatClient = builder
        .defaultOptions(OpenAiChatOptions.builder().temperature(0.5).build())
        .defaultSystem(SystemPromptTemplate(systemPromptResource).render())
        .build()
}
