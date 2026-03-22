package com.example.sherlock.tools

import org.slf4j.LoggerFactory
import org.springaicommunity.mcp.annotation.McpTool
import org.springaicommunity.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class InterrogationMcpTool {
    private val log = LoggerFactory.getLogger(InterrogationMcpTool::class.java)

    @McpTool(
        name = "InterrogationMcpTool",
        description = "Допрос подозреваемого, проводит допрос и выдает заключение о виновности на основе имя подозреваемого"
    )
    fun interrogate(
        @McpToolParam(description = "Имя подозреваемого", required = true) suspectName: String
    ): Boolean {
        val result = suspectName.length % 2 == 0
        log.info("InterrogationMcpTool: suspectName='{}', result={}", suspectName, result)
        return result
    }
}
