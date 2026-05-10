# AI Todo Proxy

Local MVP proxy for AI todo draft extraction.

## Run

```bash
node tools/ai-todo-proxy/server.mjs
```

Defaults:

- Proxy: `http://127.0.0.1:8787`
- Ollama: `http://127.0.0.1:11434`
- Model: `qwen3:4b-instruct`

Android emulator debug builds call `http://10.0.2.2:8787/` by default.

## Health Check

```bash
curl http://127.0.0.1:8787/health
```

## Parse Example

```bash
curl -s http://127.0.0.1:8787/ai/todo-drafts \
  -H 'Content-Type: application/json' \
  -d '{
    "text":"neo는 내일 오전 7시에 학원 가야 하고 나는 오전 10시에 빨래해야 해. 오늘은 저녁준비도 해야 해.",
    "now":"2026-05-10T12:00:00+09:00",
    "timezone":"Asia/Seoul",
    "locale":"ko-KR",
    "people":[
      {"id":"self","name":"나","aliases":["나","내","본인"],"isSelf":true},
      {"id":"user_neo","name":"Neo","aliases":["neo","네오"],"isSelf":false}
    ]
  }' | jq
```
