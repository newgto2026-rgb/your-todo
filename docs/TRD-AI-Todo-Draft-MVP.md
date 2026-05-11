# TRD: AI Todo Draft MVP

## Architecture

```text
Android app
  -> core:domain ParseAiTodoDraftsUseCase
  -> core:data AiTodoDraftRepositoryImpl
  -> core:network AiTodoDraftNetworkDataSource
  -> AI proxy /ai/todo-drafts
  -> Ollama qwen3:4b-instruct
```

The app never calls Ollama directly. Prompting, schema, retry, and output normalization live in the proxy.

## Modules

- `:core:model`
  - `AiTodoPerson`
  - `AiTodoDraft`
  - `AiTodoDraftResult`
- `:core:domain`
  - `AiTodoDraftRepository`
  - `ParseAiTodoDraftsUseCase`
- `:core:network`
  - `AiTodoDraftApi`
  - `AiTodoDraftNetworkDataSource`
  - dedicated AI Retrofit using `yourtodo.aiServerBaseUrl`
- `:core:data`
  - `AiTodoDraftRepositoryImpl`
- `:feature:todo:impl`
  - FAB menu
  - `AiTodoDraftViewModel`
  - editable AI draft review sheet
- `tools/ai-todo-proxy`
  - local Node proxy for Ollama

## Proxy Contract

Endpoint:

```http
POST /ai/todo-drafts
```

Request:

```json
{
  "text": "neo는 내일 오전 7시에 학원 가고 나는 10시에 빨래",
  "now": "2026-05-10T12:00:00+09:00",
  "timezone": "Asia/Seoul",
  "locale": "ko-KR",
  "people": [
    { "id": "self", "name": "나", "aliases": ["나", "내", "본인"], "isSelf": true },
    { "id": "user_neo", "name": "Neo", "aliases": ["neo", "네오"], "isSelf": false }
  ]
}
```

Response:

```json
{
  "items": [
    {
      "title": "학원 가기",
      "assigneeId": "user_neo",
      "dueDate": "2026-05-11",
      "dueTimeMinutes": 420,
      "priority": "MEDIUM",
      "needsReview": false,
      "reviewReason": null
    }
  ],
  "model": "qwen3:4b-instruct",
  "fallbackUsed": false
}
```

## Validation

App validation:

- title must not be blank
- assignee must be selected
- date must be `yyyy-MM-dd`
- time must be `HH:mm`
- time requires date
- due time minutes must be `0..1439`

Proxy validation:

- request text is non-empty and <= 2000 chars
- people includes self
- model can only return known assignee ids
- invalid assignee ids become `null` and `needsReview=true`
- invalid dates/times are nulled

## Debug Configuration

Debug default:

```properties
yourtodo.aiServerBaseUrl=http://10.0.2.2:8787/
```

Run proxy:

```bash
node tools/ai-todo-proxy/server.mjs
```

Health:

```bash
curl http://127.0.0.1:8787/health
```

## Test Plan

- `./gradlew :core:domain:testDebugUnitTest`
- `./gradlew :core:data:testDebugUnitTest`
- `./gradlew :core:network:testDebugUnitTest`
- `./gradlew :feature:todo:impl:testDebugUnitTest`
- `./gradlew :feature:todo:impl:lintDebug`
- Run proxy quality cases against local Ollama.
- Launch emulator and manually verify AI add sheet with proxy running.

## Production Hardening

- Put the proxy behind authenticated backend APIs.
- Add per-user rate limits and timeout budgets.
- Add opt-in anonymized quality logging.
- Add server-side schema validation tests.
- Compare `qwen3:4b-instruct`, `gemma3:4b`, and one larger model with a fixed Korean prompt set.
