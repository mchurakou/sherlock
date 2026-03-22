# CLAUDE.md

This file provides guidance to Claude Code (`claude.ai/code`) when working with code in this repository.

## Project

Sherlock is a Russian-language investigation chat assistant. The backend is a Spring Boot application that stores chats in PostgreSQL, retrieves character facts from Qdrant through Spring AI RAG, and serves an Angular SPA built from `frontend/`.

The knowledge base is currently `src/main/resources/knowledge/pirates.json`, so the app is themed around investigating fictional pirates / villains rather than a generic assistant.

## Tech Stack

- **Language**: Kotlin 1.9.25 with JDK 17 toolchain
- **Backend**: Spring Boot 3.5.12
- **AI**: Spring AI 1.1.4
- **LLM provider**: OpenAI-compatible API via `spring-ai-starter-model-openai`
- **Vector store**: Qdrant via `spring-ai-starter-vector-store-qdrant`
- **RAG advisor**: `spring-ai-advisors-vector-store`
- **Database**: PostgreSQL 17
- **Migrations**: Liquibase YAML changelogs in `src/main/resources/db/changelog/`
- **API docs**: springdoc OpenAPI, Swagger UI at `http://localhost:8080/swagger-ui.html`
- **Frontend**: Angular 21 standalone app in `frontend/`
- **Build**: Gradle 8.14.4 (wrapper), npm 11 for frontend tooling

## Runtime Defaults

`src/main/resources/application.yaml` is the source of truth for local defaults:

- PostgreSQL: `jdbc:postgresql://localhost:5432/sherlock`, username/password `sherlock`
- Qdrant gRPC port: `6334`
- Qdrant REST/UI port from Docker Compose: `6333`
- Default Qdrant collection: `sherlock-knowledge-local`
- Default chat base URL: `http://localhost:11434`
- Default chat model: `t-tech/T-lite-it-2.1:q8_0`
- Default embedding model: `qwen3-embedding`
- Default Qdrant vector size: `1024`

Relevant environment variables:

- `SHERLOCK_OPENAI_API_KEY`
- `SHERLOCK_OPENAI_BASE_URL`
- `SHERLOCK_CHAT_MODEL`
- `SHERLOCK_EMBEDDING_MODEL`
- `SHERLOCK_QDRANT_COLLECTION_NAME`
- `LLM_TEST_API_KEY` is used only as a fallback for the API key

There is no dedicated Spring profile for Ollama in the current code. Instead, the default configuration already points to a local OpenAI-compatible endpoint on `localhost:11434`.

## Common Commands

```bash
docker compose up -d
./gradlew bootRun
./gradlew build
./gradlew test
./gradlew buildFrontend
./gradlew buildFronted   # typo alias kept in Gradle for compatibility
```

Frontend dev server with API proxy:

```bash
cd frontend
npm ci
npm start -- --proxy-config src/proxy.conf.json
```

Notes:

- `processResources` depends on `buildFrontend`, so `bootRun` / `build` rebuild the Angular app and copy it into Spring static resources.
- Angular production output is written to `src/main/resources/static/`, and with the Angular application builder the browser bundle ends up under `src/main/resources/static/browser/`.
- The repo currently has no backend test sources and no Angular `*.spec.ts` files, even though Gradle / Angular test tasks exist.

If you change embedding dimensions or switch to an embedding model with a different vector size, clear the Qdrant volume or update `spring.ai.vectorstore.qdrant.vector-size` to match:

```bash
docker compose down
docker volume rm sherlock_qdrantdata
docker compose up -d
```

## Architecture

```text
Angular SPA (frontend/) -> built into src/main/resources/static/browser/
                         -> served by Spring MVC resource handler

REST API (/api/chats) -> ChatService -> ChatClient
                                     -> MessageChatMemoryAdvisor -> JpaChatMemory -> PostgreSQL
                                     -> QuestionAnswerAdvisor -> Qdrant
                                     -> CriminalScoringTools
                                     -> OpenAI-compatible chat model
```

### Backend packages (`com.example.sherlock`)

- `controller/`
  - `ChatController` exposes chat REST endpoints
  - `GlobalExceptionHandler` maps `ChatNotFoundException` to HTTP 404 `ProblemDetail`
  - `SpaController` redirects `/` to `/index.html`
- `service/`
  - `ChatService` handles chat CRUD reads and streaming LLM requests
- `repository/`
  - `ChatRepository`
  - `findByIdWithMessages()` uses `LEFT JOIN FETCH` and is mainly used by `JpaChatMemory`
- `entity/`
  - `Chat`
  - `Message`
  - `MessageRole`
- `dto/`
  - request/response DTOs in `ChatDtos.kt`
- `config/`
  - `AiConfig` wires prompts, advisors, tool access, and `ChatClient`
  - `JpaChatMemory` persists chat history through JPA
  - `WebConfig` serves the SPA and falls back unknown non-API routes to `index.html`
- `knowledge/`
  - `KnowledgeLoader` ingests `pirates.json` into Qdrant at startup
- `tools/`
  - `CriminalScoringTools` exposes a Spring AI tool that returns a random 1-100 score

## REST API

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/chats` | List chats, newest first |
| GET | `/api/chats/{id}` | Get one chat with messages |
| POST | `/api/chats` | Create a new empty chat |
| POST | `/api/chats/{id}/messages` | Add a user message and stream the assistant reply as `text/event-stream` |

Request body for `POST /api/chats/{id}/messages`:

```json
{ "content": "..." }
```

Behavior details:

- `createChat()` creates a title like `New chat - dd.MM.yyyy HH:mm`
- On the first persisted user message, `JpaChatMemory` replaces that placeholder title with `content.take(50)`
- Missing chats raise `ChatNotFoundException`, returned as HTTP 404

## AI / RAG Behavior

`AiConfig` currently builds `ChatClient` with:

- default temperature `0.5`
- system prompt from `src/main/resources/prompts/system.st`
- RAG prompt template from `src/main/resources/prompts/rag-question-answer.st`
- tool access to `CriminalScoringTools`
- advisors in this order:
  1. `MessageChatMemoryAdvisor`
  2. `QuestionAnswerAdvisor`

Current prompt behavior matters:

- The system prompt explicitly tells the assistant to answer in Russian.
- The RAG prompt instructs the model to answer only from retrieved context and avoid phrases like "according to the context".
- If you change product behavior, update the prompt files as well as any documentation/UI text.

`QuestionAnswerAdvisor` search settings:

- similarity threshold `0.5`
- `topK = 1`

## Knowledge Base

- Source file: `src/main/resources/knowledge/pirates.json`
- Loaded on application startup by `KnowledgeLoader`
- Loader checks Qdrant point count first and skips ingestion if the collection is non-empty
- Documents are read with `JsonReader`
- Content is built from `name` and `description`
- Metadata excludes `description`
- Text is split with `TokenTextSplitter`

## Frontend

The frontend is an Angular 21 standalone application. There is no Angular router and no NgModule-based app structure.

Key files:

- `frontend/src/app/app.ts` is the root component
- `frontend/src/app/app.config.ts` provides `HttpClient` and zone settings
- `frontend/src/app/components/chat-list/` renders the sidebar
- `frontend/src/app/components/chat-view/` renders messages and the composer
- `frontend/src/app/services/chat.service.ts` owns API calls and streaming
- `frontend/src/app/models/chat.model.ts` defines the TS API models

Frontend behavior details:

- `ChatService.streamAddMessage()` uses `fetch()` rather than Angular `HttpClient` so it can manually parse SSE chunks from `response.body`
- The root `App` component appends the optimistic user message locally before streaming starts
- Streamed assistant tokens are stored in `streamingContent`
- After stream completion, the frontend re-fetches the whole chat and chat list from the backend
- `ChatView` auto-scrolls during streaming via `AfterViewChecked`

## SPA Serving

SPA serving is split across `SpaController` and `WebConfig`:

- `GET /` redirects to `/index.html`
- Static assets are served from `classpath:/static/browser/`
- Unknown non-API routes fall back to `static/browser/index.html`
- Requests starting with `api/`, `swagger-ui`, `v3/`, or `api-docs` are excluded from SPA fallback

This means frontend routing changes must stay compatible with the custom resource handler, not just the Angular app.

## Kotlin / Spring Conventions

- `allOpen` is configured for JPA annotations, so entity classes stay proxy-friendly
- JPA entities are mutable `class`es with defaults, not `data class`es
- `spring.jpa.open-in-view=false`, so lazy data access must happen inside transactional service methods
- `Chat.messages` is ordered by `createdAt ASC`
- `Message.chat` is `LAZY`
- Liquibase owns schema creation; do not rely on Hibernate DDL beyond validation

## Working Notes For Agents

- Keep user-facing product text in Russian unless the task is explicitly changing locale behavior
- When changing AI behavior, inspect both Kotlin config and prompt templates
- When changing chat persistence behavior, inspect `ChatService`, `JpaChatMemory`, entities, and Liquibase changelogs together
- When changing the frontend build or static serving, inspect both Gradle tasks and `WebConfig`
- Do not assume old documentation is correct; `CLAUDE.md` has already drifted before
