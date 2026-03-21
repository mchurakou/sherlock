# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Sherlock is a single-page chat assistant application with a Kotlin/Spring Boot backend and an Angular frontend.

The backend exposes a small REST API for chats, streams model output over SSE, and persists conversation history through Spring AI `ChatMemory`. The Angular app is built into Spring Boot static assets and served by the backend in production.

## Tech Stack

- **Language**: Kotlin 1.9.25 with Java 17 toolchain
- **Framework**: Spring Boot 3.5.12
- **Persistence**: Spring Data JPA / Hibernate
- **Database**: PostgreSQL 17 via `docker-compose.yaml`
- **Migrations**: Liquibase YAML changelogs in `src/main/resources/db/changelog/`
- **LLM**: Spring AI 1.1.4 with `spring-ai-starter-model-openai`
- **Default local model endpoint**: Ollama-compatible API at `http://localhost:11434`
- **API docs**: springdoc-openapi 2.8.6, Swagger UI at `http://localhost:8080/swagger-ui.html`, OpenAPI JSON at `http://localhost:8080/api-docs`
- **Frontend**: Angular 21 standalone app in `frontend/`
- **Build**: Gradle 8.14.4 (Kotlin DSL), npm lockfile checked in under `frontend/`

## Common Commands

```bash
docker compose up -d
ollama pull t-tech/T-lite-it-2.1:q8_0
./gradlew bootRun
./gradlew buildFrontend
./gradlew buildFronted   # alias kept in Gradle, despite the typo
./gradlew test
cd frontend && npm ci
cd frontend && npm start -- --proxy-config src/proxy.conf.json
```

Notes:

- `buildFrontend` runs `npm run build -- --output-path ../src/main/resources/static`
- Angular's application builder emits runtime files under `src/main/resources/static/browser/`
- `processResources` depends on `buildFrontend`, so `./gradlew bootRun` builds the frontend first
- `./gradlew test` is configured, but there are currently no checked-in backend tests under `src/test/kotlin`

If `SHERLOCK_CHAT_MODEL` is overridden, pull that model before startup:

```bash
ollama pull "$SHERLOCK_CHAT_MODEL"
```

## Architecture

High-level flow:

```text
GET/POST chat metadata:
ChatController -> ChatService -> ChatRepository -> PostgreSQL

Streaming reply:
ChatController -> ChatService.streamLlm() -> ChatClient
                                         -> MessageChatMemoryAdvisor
                                         -> JpaChatMemory -> ChatRepository -> PostgreSQL

Angular App -> /api/chats -> Spring Boot
Angular production build -> src/main/resources/static/browser/ -> served by WebConfig
```

Important detail: message persistence is not done manually in `ChatService`. The streamed chat flow relies on Spring AI chat memory:

- `ChatController` calls `ChatService.streamLlm(chatId, content)`
- `ChatService` sets `ChatMemory.CONVERSATION_ID` to the chat id and streams `chatClient.prompt().stream().content()`
- `AiConfig` wires `MessageChatMemoryAdvisor` into the default `ChatClient`
- `JpaChatMemory` reads prior messages and persists both `UserMessage` and `AssistantMessage`

## Backend packages (`com.example.sherlock`)

- `controller/`:
  - `ChatController` exposes the four chat endpoints
  - `GlobalExceptionHandler` maps `ChatNotFoundException` to HTTP 404 `ProblemDetail`
  - `SpaController` redirects `/` to `/index.html`
- `service/`:
  - `ChatService` handles list/get/create and starts LLM streaming
  - `ChatNotFoundException` is the only custom service exception
- `repository/`:
  - `ChatRepository` extends `JpaRepository`
  - `findByIdWithMessages()` uses a fetch join for chat-memory reads/writes
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
  - `AiConfig` builds the default `ChatClient`, applies the system prompt from `src/main/resources/prompts/system.st`, sets `temperature=0.5`, and attaches `MessageChatMemoryAdvisor`
  - `JpaChatMemory` stores conversation history in Postgres through JPA entities
  - `WebConfig` serves Angular assets from `classpath:/static/browser/` and falls back to `index.html` for non-API SPA routes

## REST API

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/chats` | List all chats |
| GET | `/api/chats/{id}` | Get one chat with all messages |
| POST | `/api/chats` | Create a new empty chat |
| POST | `/api/chats/{id}/messages` | Add a user message and stream the assistant reply (`text/event-stream`) |

Notes:

- `POST /api/chats` returns `201 Created`
- `POST /api/chats/{id}/messages` returns raw token chunks as SSE `data:` events
- Conversation history is loaded and persisted through Spring AI chat memory, not by explicit save calls in `ChatService`

## Chat Title Lifecycle

- New chats are created with title `"New chat - dd.MM.yyyy HH:mm"`
- When the first `UserMessage` is persisted through `JpaChatMemory.add()`, the chat title is replaced with `userMessage.take(50)`

## Database

Schema is managed by Liquibase (`src/main/resources/db/changelog/`).

Current tables:

- `chat`: `id BIGINT` identity PK, `title VARCHAR(255)` nullable, `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`
- `message`: `id BIGINT` identity PK, `chat_id BIGINT NOT NULL` FK to `chat(id)`, `content TEXT NOT NULL`, `role VARCHAR(20) NOT NULL`, `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`
- Index: `idx_message_chat_id` on `message(chat_id)`

JPA mapping notes:

- `Chat.messages` is `@OneToMany(mappedBy = "chat", cascade = [CascadeType.ALL], orphanRemoval = true)`
- Messages are ordered by `createdAt ASC`
- `Message.chat` is `@ManyToOne(fetch = FetchType.LAZY)` via `chat_id`
- `Message.role` is stored as `EnumType.STRING`
- `spring.jpa.open-in-view=false`, so lazy-loaded relationships must be accessed inside transactions

`docker-compose.yaml` provides PostgreSQL 17 with database/user/password all set to `sherlock`.

## Local LLM Setup

Default Spring AI settings from `src/main/resources/application.yaml`:

- `spring.ai.openai.api-key`: `${SHERLOCK_OPENAI_API_KEY:${LLM_TEST_API_KEY:ollama}}`
- `spring.ai.openai.base-url`: `${SHERLOCK_OPENAI_BASE_URL:http://localhost:11434}`
- `spring.ai.openai.chat.options.model`: `${SHERLOCK_CHAT_MODEL:t-tech/T-lite-it-2.1:q8_0}`

`AiConfig` does not hardcode the model. It only sets the system prompt and `temperature=0.5`. Model selection comes from `application.yaml` / environment variables.

## Frontend

The frontend is a standalone Angular app with no router.

Current structure:

- `frontend/src/app/app.ts` is the root component
- `ChatList` and `ChatView` are standalone child components in `frontend/src/app/components/`
- `app.config.ts` provides `HttpClient` and zone/global error providers
- `ChatService` uses:
  - `HttpClient` for list/get/create
  - raw `fetch()` + `ReadableStream` parsing for SSE streaming

Current client flow:

1. Load chat summaries with `GET /api/chats`
2. Select a chat with `GET /api/chats/{id}`
3. If there is no active chat, create one first with `POST /api/chats`
4. Send a message with `POST /api/chats/{id}/messages`
5. Append streamed tokens to `streamingContent`
6. Re-fetch the full chat on stream completion

The frontend dev server should be run with `frontend/src/proxy.conf.json` so `/api` proxies to `http://localhost:8080`.

## Kotlin / Spring Conventions

- JPA entities are mutable `class`es with default values, not `data class`es
- `allOpen` is configured for `@Entity`, `@MappedSuperclass`, and `@Embeddable`
- `plugin.spring` handles Spring-managed class openness
- `ChatMemory.CONVERSATION_ID` must stay aligned with the persisted chat id string
- `ChatService` is `@Transactional(readOnly = true)` by default, with `createChat()` marked `@Transactional`
- Use `ChatRepository.findByIdWithMessages()` when chat-memory code needs messages eagerly fetched
