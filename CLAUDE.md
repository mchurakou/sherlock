# CLAUDE.md


This file provides guidance to Claude Code (`claude.ai/code`) when working with code in this repository.

## Project

Sherlock is an investigation chat application. The backend is a Spring Boot app that stores chats in PostgreSQL, retrieves knowledge from Qdrant through Spring AI RAG, exposes a local MCP server/client loop for an interrogation tool, and serves an Angular SPA built from `frontend/`.

The product is no longer text-only. The current code supports image attachments in chat requests, runs a separate vision-model pass through `ImageAdvisor`, and then feeds the enriched text into the normal memory + RAG + tools pipeline.

Assistant behavior is Russian-language because the system, RAG, MCP, and image-description prompts/tool descriptions are Russian. The SPA chrome is still mostly English (`+ New Chat`, `Send`, `Untitled`, `Select a chat or start a new one`, `Type a message...`), and newly created empty chats get the placeholder title `New chat - dd.MM.yyyy HH:mm`.

The knowledge base is `src/main/resources/knowledge/pirates.json`, so the product is currently themed around investigating fictional pirates / villains rather than being a generic assistant.

## Tech Stack

- **Language**: Kotlin 1.9.25 with JDK 17 toolchain
- **Backend**: Spring Boot 3.5.12
- **AI**: Spring AI 1.1.4
- **LLM provider**: OpenAI-compatible API via `spring-ai-starter-model-openai`
- **Vector store**: Qdrant via `spring-ai-starter-vector-store-qdrant`
- **RAG advisor**: `spring-ai-advisors-vector-store`
- **MCP**:
  - `spring-ai-starter-mcp-client`
  - `spring-ai-starter-mcp-server-webmvc`
- **Database**: PostgreSQL 17
- **Migrations**: Liquibase YAML changelogs in `src/main/resources/db/changelog/`
- **API docs**: springdoc OpenAPI, Swagger UI at `http://localhost:8080/swagger-ui.html`
- **Frontend**: Angular 21 standalone app in `frontend/`
- **Build**: Gradle wrapper plus npm 11.6.0 for frontend tooling

## Runtime Defaults

`src/main/resources/application.yaml` is the source of truth for local defaults:

- PostgreSQL: `jdbc:postgresql://localhost:5432/sherlock`, username/password `sherlock`
- Qdrant REST/UI port: `6333`
- Qdrant gRPC port: `6334`
- Default Qdrant collection: `sherlock-knowledge-local`
- Default chat base URL: `http://localhost:11434`
- Default chat model: `t-tech/T-lite-it-2.1:q8_0`
- Default embedding model: `qwen3-embedding`
- Default vision model: `PetrosStav/gemma3-tools:4b`
- Default Qdrant vector size: `1024`
- MCP server SSE endpoint: `/mcp/sse`
- MCP server message endpoint: `/mcp/message`
- MCP client target base URL: `http://127.0.0.1:${server.port:8080}`

Relevant environment variables:

- `SHERLOCK_OPENAI_API_KEY`
- `SHERLOCK_OPENAI_BASE_URL`
- `SHERLOCK_CHAT_MODEL`
- `SHERLOCK_EMBEDDING_MODEL`
- `SHERLOCK_QDRANT_COLLECTION_NAME`
- `SHERLOCK_VISION_MODEL`
- `LLM_TEST_API_KEY` is used only as a fallback for the API key

There is no dedicated Spring profile for Ollama in the current code. The default configuration already points to a local OpenAI-compatible endpoint on `localhost:11434`.

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
- The frontend proxy only forwards `/api` to `http://localhost:8080`.
- The repo currently has no backend test sources under `src/test` and no Angular `*.spec.ts` files, even though Gradle / Angular test tasks exist.

If you change embedding dimensions or switch to an embedding model with a different vector size, clear the Qdrant volume or update `spring.ai.vectorstore.qdrant.vector-size` to match:

```bash
docker compose down
docker volume rm sherlock_qdrantdata
docker compose up -d
```

## Architecture

```text
Angular SPA (frontend/)
  -> built into src/main/resources/static/browser/
  -> served by Spring MVC resource handler

REST API (/api/chats)
  -> ChatController
  -> ChatService
  -> ChatClient
     -> ImageAdvisor -> visionChatClient
     -> MessageChatMemoryAdvisor -> JpaChatMemory -> PostgreSQL
     -> QuestionAnswerAdvisor -> Qdrant
     -> CriminalScoringTools
     -> MCP tool callbacks -> local MCP server -> InterrogationMcpTool
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
  - `findByIdWithMessages()` uses `LEFT JOIN FETCH` and is used by `JpaChatMemory`
- `entity/`
  - `Chat`
  - `Message`
  - `MessageRole`
- `dto/`
  - request/response DTOs in `ChatDtos.kt`
- `config/`
  - `AiConfig` wires prompts, advisors, direct tools, MCP tool callbacks, `visionChatClient`, and the main `ChatClient`
  - `ImageAdvisor` converts the last user message with media into text enriched by a vision-model description
  - `JpaChatMemory` persists chat history through JPA
  - `WebConfig` serves the SPA and falls back unknown non-API routes to `index.html`
- `knowledge/`
  - `KnowledgeLoader` ingests `pirates.json` into Qdrant at startup
- `tools/`
  - `CriminalScoringTools` exposes a Spring AI tool that returns a random 1-100 score
  - `InterrogationMcpTool` exposes an MCP tool that returns `true` when the suspect name length is even

## REST API

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/chats` | List chats, newest first |
| GET | `/api/chats/{id}` | Get one chat with messages |
| POST | `/api/chats` | Create a new empty chat |
| POST | `/api/chats/{id}/messages` | Add a user message and stream the assistant reply as `text/event-stream` |

Request body for `POST /api/chats/{id}/messages`:

```json
{
  "content": "...",
  "imageBase64": "optional raw base64 without data URL prefix",
  "imageMimeType": "optional MIME type such as image/png"
}
```

Response details:

- `ChatDetailResponse.messages[*]` and `MessageResponse` include optional `imageData` and `imageMimeType`
- `ChatSummaryResponse.title` and `ChatDetailResponse.title` are nullable

Behavior details:

- `createChat()` creates a title like `New chat - dd.MM.yyyy HH:mm`
- On the first persisted user message, `JpaChatMemory` replaces that placeholder title with `msg.text.take(50)` from the stored Spring AI `UserMessage`
- Because image requests are rewritten into text before later advisors run, the first title for an image-only message can come from the enriched text rather than the raw user input
- Missing chats raise `ChatNotFoundException`, returned as HTTP 404
- The frontend "new chat" action does not persist anything by itself; it only clears selection, and the actual `POST /api/chats` happens on the first send

## AI / RAG / MCP Behavior

`AiConfig` currently builds two chat clients:

- `visionChatClient`
  - uses `sherlock.vision.model` when it is non-blank
  - defaults to `PetrosStav/gemma3-tools:4b` from `application.yaml`
- main `chatClient`
  - default temperature `0.5`
  - system prompt from `src/main/resources/prompts/system.st`
  - direct tool access to `CriminalScoringTools`
  - MCP-provided tool callbacks from the configured `ToolCallbackProvider` beans

Main advisor order:

1. `ImageAdvisor`
2. `MessageChatMemoryAdvisor`
3. `QuestionAnswerAdvisor`

Current prompt/advisor behavior matters:

- The system prompt explicitly tells the assistant to answer in Russian.
- The image-description prompt also explicitly asks for Russian output.
- `ImageAdvisor` looks for the last `UserMessage` that still has media attached.
- It calls `visionChatClient` with `prompts/image-description.st`, appends the returned description to the user's text, and clears media from that message before the rest of the advisor chain runs.
- This means the main chat model, memory advisor, and RAG advisor receive text enriched with the image description, not raw image bytes.
- `JpaChatMemory.get()` reconstructs prior turns as plain text `UserMessage` / `AssistantMessage` instances, so previous images are not reattached as media on later turns.
- The RAG prompt instructs the model to answer only from retrieved context and avoid phrases like "according to the context".

`QuestionAnswerAdvisor` search settings:

- similarity threshold `0.5`
- `topK = 1`

MCP wiring details:

- The app exposes its own SSE MCP server with `InterrogationMcpTool`.
- The app configures an MCP client connection back to `http://127.0.0.1:${server.port:8080}/mcp/sse`.
- `spring.ai.mcp.server.tool-callback-converter` is disabled, and the server relies on annotation scanning for the MCP tool.
- `ChatClient` includes MCP tool callbacks, so the model can invoke the local interrogation tool through the MCP client layer.

## Knowledge Base

- Source file: `src/main/resources/knowledge/pirates.json`
- Loaded on application startup by `KnowledgeLoader`
- Loader downcasts the configured `VectorStore` to `QdrantVectorStore` and checks Qdrant point count first
- Ingestion is skipped if the collection is non-empty
- Documents are read with `JsonReader`
- Content is built from `name` and `description`
- Metadata excludes `description`
- Text is split with `TokenTextSplitter`

## Frontend

The frontend is an Angular 21 standalone application. There is no Angular router usage in the current app and no NgModule-based app structure.

Key files:

- `frontend/src/app/app.ts` is the root component
- `frontend/src/app/app.config.ts` provides `HttpClient`, global error listeners, and zone change detection with event coalescing
- `frontend/src/app/components/chat-list/` renders the sidebar
- `frontend/src/app/components/chat-view/` renders messages, image preview, and the composer
- `frontend/src/app/services/chat.service.ts` owns API calls and streaming
- `frontend/src/app/models/chat.model.ts` defines the TS API models

Frontend behavior details:

- `ChatView` supports optional image attachment and currently accepts `image/png`, `image/jpeg`, and `image/gif`
- `ChatView` reads selected files as data URLs, strips the prefix, and emits raw base64 plus MIME type
- `ChatService.streamAddMessage()` uses `fetch()` rather than Angular `HttpClient` so it can manually parse SSE chunks from `response.body`
- SSE parsing only reads `data:` lines from double-newline-separated events
- The root `App` component appends the optimistic user message locally before streaming starts
- That optimistic message includes `imageData` / `imageMimeType` so the attached image appears immediately in the UI
- `streamingContent` holds the in-progress assistant text
- After stream completion, the frontend re-fetches the whole chat and chat list from the backend
- `ChatView` auto-scrolls during streaming via `AfterViewChecked`
- The current UI copy is English, not Russian

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

- Do not assume all user-facing text is Russian. The AI prompts and tool descriptions are Russian, but the current frontend copy and placeholder chat title are English.
- When changing AI behavior, inspect Kotlin config, advisor ordering, prompt templates, and MCP wiring together.
- When changing image behavior, inspect `ChatController`, `ChatService`, `AiConfig`, `ImageAdvisor`, `JpaChatMemory`, DTOs, Liquibase changelogs, and `frontend/src/app/components/chat-view/` together.
- When changing chat persistence behavior, inspect `ChatService`, `JpaChatMemory`, entities, and Liquibase changelogs together.
- When changing the frontend build or static serving, inspect both Gradle tasks and `WebConfig`.
- When changing interrogation behavior, inspect `application.yaml`, `AiConfig`, and `InterrogationMcpTool` together.
- Do not assume old documentation is correct; this file has drifted before.
