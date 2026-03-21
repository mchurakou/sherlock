# CLAUDE.md


This file provides guidance to Claude Code (`claude.ai/code`) when working with code in this repository.

## Project

Sherlock is a Russian-language investigation-themed chat assistant with a Spring Boot backend and an Angular frontend. It uses Spring AI with persistent chat memory plus a small RAG knowledge base. The bundled knowledge base currently comes from `src/main/resources/knowledge/pirates.json` and contains pirate / Treasure Island-style characters.

## Tech Stack

- **Language**: Kotlin 1.9.25 with JDK 17 toolchain
- **Framework**: Spring Boot 3.5.12
- **Database**: PostgreSQL 17 via Docker Compose
- **Migrations**: Liquibase YAML changelogs in `src/main/resources/db/changelog/`
- **ORM**: Spring Data JPA / Hibernate
- **LLM integration**: Spring AI 1.1.4 via `spring-ai-starter-model-openai`
- **Vector store**: Qdrant via `spring-ai-starter-vector-store-qdrant`
- **RAG advisor**: `spring-ai-advisors-vector-store` with `QuestionAnswerAdvisor`
- **API docs**: springdoc OpenAPI, Swagger UI at `http://localhost:8080/swagger-ui.html`
- **Frontend**: Angular 21 standalone app in `frontend/`
- **Build**: Gradle 8.14.4, npm lockfile checked in under `frontend/`

## Runtime Defaults

The checked-in `application.yaml` is configured for a local OpenAI-compatible endpoint by default:

- **LLM base URL**: `http://localhost:11434`
- **Chat model**: `t-tech/T-lite-it-2.1:q8_0`
- **Embedding model**: `qwen3-embedding`
- **Qdrant gRPC port**: `6334`
- **Qdrant collection**: `sherlock-knowledge-local`
- **Vector size**: `1024`

Override these with environment variables:

- `SHERLOCK_OPENAI_API_KEY`
- `SHERLOCK_OPENAI_BASE_URL`
- `SHERLOCK_CHAT_MODEL`
- `SHERLOCK_EMBEDDING_MODEL`
- `SHERLOCK_QDRANT_COLLECTION_NAME`

`SHERLOCK_OPENAI_API_KEY` falls back to `LLM_TEST_API_KEY`, then to the literal string `ollama`.

## Common Commands

```bash
docker compose up -d
./gradlew bootRun
./gradlew build
./gradlew buildFrontend
./gradlew buildFronted   # typo-preserving alias kept in build.gradle.kts
./gradlew test           # command exists; there are currently no checked-in tests
```

Frontend dev server with `/api` proxy to the backend:

```bash
cd frontend
npm ci
npm start -- --proxy-config src/proxy.conf.json
```

Reset Qdrant when embedding dimensions or collection schema change:

```bash
docker compose down
docker volume rm sherlock_qdrantdata
docker compose up -d
```

## Architecture

```text
ChatController (/api/chats) -> ChatService -> ChatRepository -> PostgreSQL
                                   |
                                   -> ChatClient
                                      |- MessageChatMemoryAdvisor -> JpaChatMemory -> PostgreSQL
                                      |- QuestionAnswerAdvisor -> Qdrant
                                      `- OpenAI-compatible chat model (streaming)

Angular frontend -> Gradle buildFrontend -> src/main/resources/static/browser/ -> Spring Boot static hosting
```

### Backend packages (`com.example.sherlock`)

- `controller/`:
  - `ChatController` exposes the chat REST API
  - `GlobalExceptionHandler` maps `ChatNotFoundException` to HTTP 404 `ProblemDetail`
  - `SpaController` redirects `/` to `/index.html`
- `service/`:
  - `ChatService` handles chat CRUD reads plus LLM streaming
  - `ChatNotFoundException` for missing chats
- `repository/`:
  - `ChatRepository` extends `JpaRepository`
  - `findByIdWithMessages()` uses `LEFT JOIN FETCH` for chat-memory access
- `entity/`:
  - `Chat`
  - `Message`
  - `MessageRole`
- `dto/`:
  - `AddMessageRequest`
  - `ChatSummaryResponse`
  - `ChatDetailResponse`
  - `MessageResponse`
- `config/`:
  - `AiConfig` builds the shared `ChatClient`
  - `JpaChatMemory` stores Spring AI chat memory in PostgreSQL
  - `WebConfig` serves the Angular SPA and falls back to `index.html`
- `knowledge/`:
  - `KnowledgeLoader` ingests `knowledge/pirates.json` into Qdrant on startup

## REST API

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/chats` | List chats, newest first |
| GET | `/api/chats/{id}` | Get a chat with messages |
| POST | `/api/chats` | Create an empty chat |
| POST | `/api/chats/{id}/messages` | Send a user message and stream the model response as `text/event-stream` |

### Streaming behavior

- `ChatService.streamLlm()` sets `ChatMemory.CONVERSATION_ID` to the chat ID.
- Conversation persistence is delegated to `MessageChatMemoryAdvisor` and `JpaChatMemory`.
- The controller returns `Flux<String>` with SSE framing handled by Spring.
- The frontend consumes the stream manually with `fetch()` + `ReadableStream`, appending each `data:` chunk to `streamingContent`.

## Chat Lifecycle

- New chats are created with title `New chat - dd.MM.yyyy HH:mm`.
- On the first persisted user message, `JpaChatMemory.add()` replaces the title with the first 50 characters of that message.
- Chat list ordering is `createdAt DESC`.
- Message ordering inside a chat is `createdAt ASC` via `@OrderBy("createdAt ASC")`.

## AI / RAG Details

- The default system prompt comes from `src/main/resources/prompts/system.st`.
- The system prompt explicitly requires Russian responses.
- The RAG prompt comes from `src/main/resources/prompts/rag-question-answer.st`.
- `AiConfig` sets default chat temperature to `0.5`.
- `QuestionAnswerAdvisor` uses:
  - `similarityThreshold = 0.5`
  - `topK = 1`
  - the custom RAG prompt template
- `KnowledgeLoader`:
  - casts the configured `VectorStore` to `QdrantVectorStore`
  - checks the native Qdrant collection point count before loading
  - skips ingestion if the collection is already non-empty
  - reads `pirates.json` with `JsonReader`
  - uses `"name"` and `"description"` as document content fields
  - keeps metadata except `description` (so `name` and `alignment` remain)
  - splits documents with `TokenTextSplitter`

## Database

Liquibase manages two tables:

- `chat`
- `message`

`message.chat_id` has a foreign key to `chat(id)` plus an index `idx_message_chat_id`.

## Frontend

- The frontend is an Angular 21 standalone app, not an Angular Router SPA.
- `App` is the orchestration component and owns:
  - `chats`
  - `selectedChat`
  - `selectedChatId`
  - `streamingContent`
- `ChatList` emits chat selection and "new chat" events.
- `ChatView` renders messages, input, and auto-scroll behavior while streaming.
- Creating a "new chat" in the UI is client-side only until the first message is sent. The actual backend chat is created lazily in `onMessageSent()`.
- `ChatService` uses `HttpClient` for list/get/create and raw `fetch()` for streaming.

## Static Hosting

- Gradle task `buildFrontend` runs `npm ci`, then `npm run build -- --output-path ../src/main/resources/static`.
- With Angular's application builder, built assets land under `src/main/resources/static/browser/`.
- `WebConfig` serves from `classpath:/static/browser/`.
- Unknown non-API paths fall back to `static/browser/index.html`.
- `WebConfig` does **not** intercept:
  - `/api/**`
  - `/swagger-ui/**`
  - `/v3/**`
  - `/api-docs`

## Kotlin / Spring Conventions

- `plugin.spring` opens Spring-managed classes; `allOpen` additionally opens JPA entities for Hibernate proxies.
- JPA entities use mutable `class` types with default values, not `data class`.
- `spring.jpa.open-in-view=false`, so lazy relations must be loaded inside transactional service / repository boundaries.
- `ChatRepository.findByIdWithMessages()` exists specifically for `JpaChatMemory`, where conversation history must be loaded with messages eagerly.
