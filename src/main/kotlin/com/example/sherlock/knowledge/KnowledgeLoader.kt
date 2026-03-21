package com.example.sherlock.knowledge

import io.qdrant.client.QdrantClient
import org.slf4j.LoggerFactory
import org.springframework.ai.reader.JsonReader
import org.springframework.ai.transformer.splitter.TokenTextSplitter
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component

@Component
class KnowledgeLoader(
    private val vectorStore: VectorStore,
    @param:Value("classpath:knowledge/pirates.json") private val knowledgeResource: Resource,
    @param:Value("\${spring.ai.vectorstore.qdrant.collection-name}") private val collectionName: String
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)
    private val splitter = TokenTextSplitter()

    override fun run(args: ApplicationArguments) {
        val nativeClient: java.util.Optional<QdrantClient> = (vectorStore as QdrantVectorStore).getNativeClient()
        check(nativeClient.isPresent) { "Qdrant native client not available" }
        val client: QdrantClient = nativeClient.get()

        val count = client.countAsync(collectionName).get()
        if (count > 0) {
            log.info("Knowledge base already loaded ({} points), skipping ingestion", count)
            return
        }

        val jsonReader = JsonReader(
            knowledgeResource,
            { json -> json.filterKeys { it != "description" } },
            "name", "description"
        )

        val documents = splitter.split(jsonReader.get())

        vectorStore.add(documents)
        log.info("Loaded {} document chunks into knowledge base", documents.size)
    }
}
