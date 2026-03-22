package com.example.sherlock.tools

import org.slf4j.LoggerFactory
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component
import kotlin.random.Random

@Component
class CriminalScoringTools {

    private val log = LoggerFactory.getLogger(CriminalScoringTools::class.java)

    @Tool(description = "Оценивает человека на риск совершения криминала. Возвращает score — процентную вероятность (1–100) того, что человек является преступником.")
    fun scoreCriminal(
        @ToolParam(description = "Имя человека, которого нужно оценить") name: String
    ): Int {
        val score = Random.nextInt(1, 101)
        log.info("CriminalScoring: name='{}', score={}", name, score)
        return score
    }
}
