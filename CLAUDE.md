# CLAUDE.md


This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Sherlock is a chat assistant web application — Spring Boot backend with Angular frontend.

## Tech Stack

- **Language**: Kotlin 1.9.25 with JDK 17 toolchain
- **Framework**: Spring Boot 3.5.12
- **Database**: PostgreSQL 17 (via Docker Compose)
- **Migrations**: Liquibase (YAML changelogs in `src/main/resources/db/changelog/`)
- **ORM**: Spring Data JPA / Hibernate
- **LLM**: Spring AI 1.1.4 (`spring-ai-starter-model-openai`) — OpenAI-compatible API, defaulting to local Ollama at `http://localhost:11434`
- **API Docs**: springdoc-openapi (Swagger UI at `http://localhost:8080/swagger-ui.html`)
- **Frontend**: Angular (in `frontend/`)
- **Build**: Gradle 8.14.4 (Kotlin DSL)

## Common Commands

```bash
docker compose up -d                          # Start PostgreSQL from docker-compose.yaml
ollama pull t-tech/T-lite-it-2.1:q8_0        # Pull the default chat model from src/main/resources/application.yaml
./gradlew bootRun                             # Build frontend + start app
./gradlew buildFrontend                       # Build Angular only (output → src/main/resources/static/)
./gradlew test                                # Run all tests
./gradlew test --tests "com.example.sherlock.SomeTest"  # Run single test
```

If `SHERLOCK_CHAT_MODEL` is overridden, pull that model before startup:
```bash
ollama pull "$SHERLOCK_CHAT_MODEL"
```

Frontend dev server (hot reload, proxies `/api` → `localhost:8080`):
```bash
cd frontend && npx ng serve --proxy-config src/proxy.conf.json
# Then open http://localhost:4200
```

## Architecture

```
Controller (REST /api/chats) → Service (ChatService) → Repository (JPA) → PostgreSQL
                                        ↓
                                  Spring AI ChatClient → LLM API (SSE streaming)
Angular frontend (frontend/) → built into src/main/resources/static/ → served by Spring Boot
```

### Backend packages (`com.example.sherlock`)

- `controller/` — `ChatController` (4 REST endpoints) + `GlobalExceptionHandler`
- `service/` — `ChatService` (business logic + LLM calls)
- `repository/` — Spring Data JPA repositories
- `entity/` — JPA entities (`Chat`, `Message`, `MessageRole` enum)
- `dto/` — Request/response DTOs (`ChatDtos.kt`)
- `config/` — `WebConfig` (SPA fallback routing), `AiConfig` (ChatClient bean with default system prompt from `prompts/system.st` and `temperature=0.5`)

### REST API

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/chats` | List all chats |
| GET | `/api/chats/{id}` | Get chat with messages |
| POST | `/api/chats` | Create new empty chat (no body) |
| POST | `/api/chats/{id}/messages` | Add message + stream LLM reply (`text/event-stream`) |

The streaming endpoint (`POST /{id}/messages`) saves the user message, then returns a `Flux<String>` of raw token chunks as SSE `data:` events. When the flux completes, the full assistant message is saved to DB via `concatWith(Mono.fromRunnable(...).subscribeOn(Schedulers.boundedElastic()))`.

### Chat title lifecycle

- Created with title `"New chat - dd.MM.yyyy HH:mm"`
- Updated to `content.take(50)` of the first user message when `saveUserMessage` is called

### Database

Schema is managed by Liquibase (`src/main/resources/db/changelog/`). Current PostgreSQL tables:

- `chat`: `id BIGINT` identity PK, `title VARCHAR(255)` nullable, `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`
- `message`: `id BIGINT` identity PK, `chat_id BIGINT NOT NULL` FK to `chat(id)`, `content TEXT NOT NULL`, `role VARCHAR(20) NOT NULL`, `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`
- Index: `idx_message_chat_id` on `message(chat_id)`

JPA mapping notes:

- `Chat.messages` is `@OneToMany(mappedBy = "chat", cascade = [CascadeType.ALL], orphanRemoval = true)` and ordered by `createdAt ASC`
- `Message.chat` is `@ManyToOne(fetch = FetchType.LAZY)` via `chat_id`
- `Message.role` is stored as `EnumType.STRING`

`docker-compose.yaml` provides PostgreSQL 17 with database/user/password all set to `sherlock`. Start it manually with `docker compose up -d`.

### Local LLM Setup

Default Spring AI settings from `src/main/resources/application.yaml`:

- `spring.ai.openai.api-key`: `${SHERLOCK_OPENAI_API_KEY:${LLM_TEST_API_KEY:ollama}}`
- `spring.ai.openai.base-url`: `${SHERLOCK_OPENAI_BASE_URL:http://localhost:11434}`
- `spring.ai.openai.chat.options.model`: `${SHERLOCK_CHAT_MODEL:t-tech/T-lite-it-2.1:q8_0}`

Before running the app, pull every model referenced in `src/main/resources/application.yaml`. At the moment there is one default model:

```bash
ollama pull t-tech/T-lite-it-2.1:q8_0
./gradlew bootRun
```

### Frontend

Angular app in `frontend/`. Gradle `buildFrontend` task (hooked into `processResources`) builds it and copies output to `src/main/resources/static/`.

Streaming flow: `createChat()` (POST /api/chats) → `streamAddMessage()` (POST /api/chats/{id}/messages, fetch API with `ReadableStream`). Tokens are appended to `streamingContent` in `App` component and shown as a live bubble in `ChatView`. On stream complete, full chat is re-fetched from the server.

## Kotlin/Spring Conventions

- `plugin.spring` makes Spring-annotated classes open; `allOpen` in `build.gradle.kts` extends this to `@Entity`, `@MappedSuperclass`, `@Embeddable` — required so Hibernate can create proxy subclasses for lazy loading (open class alone is not enough; getter methods must also be non-final)
- JPA entities use `class` with `var` properties and default values (not `data class`)
- `jackson-module-kotlin` handles Kotlin constructor/nullable type deserialization; `kotlin-reflect` is pulled in transitively and not declared explicitly
- `AiConfig` sets default chat options with `temperature=0.5` and loads the system prompt from `src/main/resources/prompts/system.st`
