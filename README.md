# Sherlock

Sherlock is an investigation chat application with a Kotlin/Spring Boot backend and an Angular frontend. It stores chats in PostgreSQL, retrieves knowledge from Qdrant with Spring AI RAG, exposes a local MCP server/client loop for an interrogation tool, and serves the built SPA from the backend.

The current app is themed around investigating fictional pirates and villains from [`src/main/resources/knowledge/pirates.json`](/Users/mac/Documents/tbank/sherlock/src/main/resources/knowledge/pirates.json). Chat behavior is Russian-language because the system prompt, RAG prompt, MCP instructions, and image-description prompt are written in Russian.

## Features

- Chat history persisted in PostgreSQL
- Retrieval-augmented generation backed by Qdrant
- Image attachments in chat requests
- Separate vision-model pass before the normal chat pipeline
- Local MCP tool integration for interrogation
- Angular SPA served directly by Spring Boot
- Swagger UI for API inspection at `http://localhost:8080/swagger-ui.html`

## Tech Stack

- Kotlin 1.9.25
- JDK 17
- Spring Boot 3.5.12
- Spring AI 1.1.4
- OpenAI-compatible chat and embedding API
- PostgreSQL 17
- Qdrant
- Liquibase
- Angular 21
- Gradle Wrapper
- npm 11.6.0

## Project Structure

```text
.
|-- src/main/resources/
|   |-- application.yaml
|   |-- db/changelog/
|   |-- knowledge/pirates.json
|   |-- prompts/
|   `-- static/
|-- frontend/
|   `-- src/app/
|-- build.gradle.kts
|-- docker-compose.yaml
`-- CLAUDE.md
```

Backend code lives under `src/main/kotlin/com/example/sherlock` and the Angular app lives in [`frontend/`](/Users/mac/Documents/tbank/sherlock/frontend).

## Requirements

- JDK 17
- Docker and Docker Compose
- Node.js with npm
- Ollama or another OpenAI-compatible local API at `http://localhost:11434`

## Local Run

Start infrastructure:

```bash
docker compose up -d
```

Pull the default local models in Ollama:

```bash
ollama pull t-tech/T-lite-it-2.1:q8_0
ollama pull qwen3-embedding
ollama pull PetrosStav/gemma3-tools:4b
```

Start Ollama if it is not already running. The backend defaults to an OpenAI-compatible endpoint at `http://localhost:11434`, so local startup will fail until the chat, embedding, and vision models are available there.

Run the backend:

```bash
./gradlew bootRun
```

The backend build automatically builds the frontend and copies the production bundle into Spring static resources during `processResources`.

Open:

- App: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Qdrant UI/API: `http://localhost:6333`

## Frontend Development

For Angular local development with API proxying:

```bash
cd frontend
npm ci
npm start -- --proxy-config src/proxy.conf.json
```

The proxy forwards `/api` to `http://localhost:8080`.

## Build and Test

```bash
./gradlew build
./gradlew test
./gradlew buildFrontend
./gradlew buildFronted
```

Notes:

- `buildFronted` is an intentional compatibility alias for `buildFrontend`
- The repository currently has no backend tests under `src/test`
- The Angular app currently has no `*.spec.ts` tests

## Configuration

The main local defaults are defined in [`src/main/resources/application.yaml`](/Users/mac/Documents/tbank/sherlock/src/main/resources/application.yaml).

Default services:

- PostgreSQL: `jdbc:postgresql://localhost:5432/sherlock`
- PostgreSQL credentials: `sherlock` / `sherlock`
- Qdrant HTTP: `localhost:6333`
- Qdrant gRPC: `localhost:6334`
- Qdrant collection: `sherlock-knowledge-local`
- Default OpenAI-compatible base URL: `http://localhost:11434`
- Default chat model: `t-tech/T-lite-it-2.1:q8_0`
- Default embedding model: `qwen3-embedding`
- Default vision model: `PetrosStav/gemma3-tools:4b`
- Qdrant vector size: `1024`

If you use Ollama with the default config, these models must be available locally:

- `t-tech/T-lite-it-2.1:q8_0`
- `qwen3-embedding`
- `PetrosStav/gemma3-tools:4b`

Environment variables:

- `SHERLOCK_OPENAI_API_KEY`
- `SHERLOCK_OPENAI_BASE_URL`
- `SHERLOCK_CHAT_MODEL`
- `SHERLOCK_EMBEDDING_MODEL`
- `SHERLOCK_QDRANT_COLLECTION_NAME`
- `SHERLOCK_VISION_MODEL`
- `LLM_TEST_API_KEY` as API key fallback only

If you switch embedding models or embedding dimensions, make sure the Qdrant collection vector size still matches. When needed, recreate the Qdrant volume:

```bash
docker compose down
docker volume rm sherlock_qdrantdata
docker compose up -d
```

## Architecture

```text
Angular SPA
  -> built into src/main/resources/static/browser/
  -> served by Spring MVC

REST API (/api/chats)
  -> ChatController
  -> ChatService
  -> ChatClient
     -> ImageAdvisor
     -> MessageChatMemoryAdvisor -> PostgreSQL
     -> QuestionAnswerAdvisor -> Qdrant
     -> CriminalScoringTools
     -> MCP tool callbacks -> local MCP server -> InterrogationMcpTool
     -> OpenAI-compatible chat model
```

### Backend

- `controller/`: REST endpoints, exception mapping, SPA entry redirect
- `service/`: chat orchestration and streaming responses
- `repository/`: JPA chat access
- `entity/`: chats, messages, roles
- `config/`: AI wiring, memory, SPA/web config
- `knowledge/`: startup ingestion of the pirate knowledge base into Qdrant
- `tools/`: Spring AI tool and MCP tool implementations

### Frontend

The Angular app is standalone-based and does not currently use Angular router for app navigation.

Key files:

- [`frontend/src/app/app.ts`](/Users/mac/Documents/tbank/sherlock/frontend/src/app/app.ts)
- [`frontend/src/app/services/chat.service.ts`](/Users/mac/Documents/tbank/sherlock/frontend/src/app/services/chat.service.ts)
- [`frontend/src/app/components/chat-list/chat-list.ts`](/Users/mac/Documents/tbank/sherlock/frontend/src/app/components/chat-list/chat-list.ts)
- [`frontend/src/app/components/chat-view/chat-view.ts`](/Users/mac/Documents/tbank/sherlock/frontend/src/app/components/chat-view/chat-view.ts)

Notable behavior:

- Users can attach `image/png`, `image/jpeg`, and `image/gif`
- The frontend sends raw base64 plus MIME type
- Assistant responses stream over SSE
- The UI renders the optimistic user message immediately, including attached images
- After a stream finishes, the frontend re-fetches the full chat and chat list

## API

| Method | Path | Description |
| --- | --- | --- |
| GET | `/api/chats` | List chats, newest first |
| GET | `/api/chats/{id}` | Get a single chat with messages |
| POST | `/api/chats` | Create a new empty chat |
| POST | `/api/chats/{id}/messages` | Add a user message and stream the assistant reply as `text/event-stream` |

Example request body for `POST /api/chats/{id}/messages`:

```json
{
  "content": "Кто на изображении?",
  "imageBase64": "optional raw base64 without data URL prefix",
  "imageMimeType": "image/png"
}
```

## AI Pipeline

The main chat flow is:

1. `ImageAdvisor` inspects the latest user message for attached media
2. A vision-model prompt describes the image in Russian
3. The description is appended to the user text
4. Chat memory is loaded from PostgreSQL
5. RAG retrieves supporting context from Qdrant
6. The main model can also call direct tools and the local MCP interrogation tool

Important details:

- The main model receives enriched text, not raw image bytes
- Previous images are not reattached on later turns
- RAG currently uses similarity threshold `0.5` and `topK = 1`
- The MCP server and MCP client both run inside the same app, connected through SSE endpoints

## Knowledge Base

- Source: [`src/main/resources/knowledge/pirates.json`](/Users/mac/Documents/tbank/sherlock/src/main/resources/knowledge/pirates.json)
- Loaded at startup by the knowledge loader
- Ingestion is skipped when the Qdrant collection already contains points
- Documents are split with `TokenTextSplitter`

## SPA Serving

- `GET /` redirects to `/index.html`
- Static assets are served from `classpath:/static/browser/`
- Unknown non-API routes fall back to `static/browser/index.html`
- API and documentation paths are excluded from SPA fallback
