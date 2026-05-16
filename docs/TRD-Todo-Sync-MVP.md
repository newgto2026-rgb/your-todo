# TRD - Todo Sync MVP

## 1. 문서 정보
- 문서명: 기술 요구사항 문서 - Todo Sync MVP
- 대상 프로젝트: `YourTodo` Android 앱 + `yourtodo-server`
- Android 기준 브랜치: `codex/android-todo-sync-mvp`
- 서버 기준 브랜치: `codex/server-todo-sync-mvp`
- 작성일: 2026-05-08
- 상태: 서버/QA 1차 리뷰 반영 중. 사용자 승인 후 구현 착수

## 2. 배경
현재 Android 앱은 Room 기반 로컬 Todo를 사용한다. Google 로그인과 닉네임 온보딩 기반은 서버와 연결되었지만, Todo 데이터는 아직 로컬 전용이다.

이번 단계의 핵심은 로그인한 사용자의 개인 Todo를 서버와 동기화하는 것이다. 단순 REST CRUD를 붙이면 온라인 전용 사용에는 충분할 수 있지만, 이미 로컬 DB가 존재하고 오프라인 생성/수정/삭제 가능성이 있으므로 서버와 로컬의 정합성을 보장하기 어렵다.

따라서 MVP라도 서버를 최종 진실의 원천으로 두고, Android 로컬 DB는 서버 상태 캐시와 미전송 변경 큐 역할을 하도록 설계한다.

## 3. 목표 및 비목표

### 3.1 목표
- 로그인한 사용자의 개인 Todo를 서버에 저장한다.
- 앱 화면은 기존처럼 로컬 Room을 관찰해 빠르게 렌더링한다.
- 온라인 복귀 시 서버와 로컬 상태가 최종적으로 서버 기준으로 수렴한다.
- 오프라인에서 생성한 Todo가 온라인 복귀 후 중복 생성되지 않는다.
- 오프라인에서 생성 후 수정/삭제한 Todo가 예측 가능한 방식으로 동기화된다.
- 서버/API/Android DB 변경을 테스트 가능한 단위로 분리한다.

### 3.2 비목표
- 기존 로컬 Todo의 서버 마이그레이션
- 친구가 준 Todo, 친구에게 보낸 Todo, 모니터링
- 푸시 알림
- 여러 기기 간 실시간 동기화
- field-level conflict resolution UI
- CRDT 또는 복잡한 merge 알고리즘
- 카테고리/시간 세부값/리마인더 서버 동기화

## 4. 핵심 정책

### 4.1 서버 권위
서버는 최종 진실의 원천이다. Android는 로컬 변경을 즉시 화면에 반영하지만, 네트워크 동기화가 완료되면 서버 응답으로 로컬 상태를 확정한다.

### 4.2 로컬 DB 역할
Room은 다음 두 역할을 가진다.
- 화면 렌더링용 캐시
- 아직 서버에 반영되지 않은 변경의 임시 상태 저장

### 4.3 Outbox 역할
로컬 변경은 별도 outbox 테이블에 mutation으로 저장한다. 네트워크가 가능해지면 outbox를 순서대로 서버에 push한다.

### 4.4 동기화 순서
온라인 동기화는 항상 다음 순서를 따른다.

```text
pull(cursor) -> apply remote changes -> push(outbox) -> apply mutation results -> pull(latest cursor)
```

이 순서를 통해 서버에서 이미 삭제/수정된 Todo를 먼저 반영한 뒤 로컬 변경을 보내고, 마지막에는 서버 기준 상태로 다시 수렴한다.

## 5. 서버 API 계약

### 5.1 인증
모든 Todo sync API는 서버 access token을 사용한다.

```http
Authorization: Bearer <server accessToken>
```

토큰이 없거나 유효하지 않으면 서버는 공통 에러 shape를 반환한다.

```json
{
  "error": {
    "code": "AUTH_REQUIRED",
    "message": "Authorization bearer token is required."
  }
}
```

현재 서버/Android에는 refresh API 재발급 흐름이 완성되어 있지 않다. 따라서 MVP 1차에서는 access token 만료 시 sync mutation을 삭제하지 않고 outbox에 유지한다. Android는 401을 일반 네트워크 실패처럼 무한 재시도하지 않고 `AUTH_REQUIRED` 상태로 분리한다. refresh/logout/me API가 구현되면 별도 TRD 또는 본 문서 개정으로 retry 정책을 확장한다.

### 5.2 Todo DTO

```json
{
  "id": "server-uuid",
  "clientId": "android-generated-uuid",
  "title": "Buy milk",
  "description": null,
  "dueDate": "2026-05-10",
  "status": "ACTIVE",
  "priority": "MEDIUM",
  "categoryId": 12,
  "dueTimeMinutes": 870,
  "revision": "42",
  "createdAt": "2026-05-08T00:00:00.000Z",
  "updatedAt": "2026-05-08T00:00:00.000Z",
  "deletedAt": null
}
```

필드 정책:
- `id`: 서버가 발급하는 전역 ID
- `clientId`: Android가 로컬 생성 시 발급하는 UUID
- `title`: 필수. Android와 서버가 같은 길이/공백 정책을 가진다.
- `description`: MVP에서는 null 허용. 기존 Android Todo에는 description이 없으므로 1차 UI에서는 사용하지 않는다.
- `dueDate`: `YYYY-MM-DD`, 없으면 null
- `status`: `ACTIVE`, `COMPLETED`, `DELETED`
- `priority`: `LOW`, `MEDIUM`, `HIGH`
- `categoryId`: 개인 Todo 카테고리 ID, 없으면 null
- `dueTimeMinutes`: 0..1439 범위의 로컬 날짜 기준 시각, 없으면 null
- `revision`: 사용자 Todo 변경 순서를 나타내는 서버 단조 증가 값. API에서는 decimal string으로 고정한다.
- `deletedAt`: soft delete tombstone

### 5.2.1 Android sync field policy

현재 Android는 서버 계약을 임의로 확장하지 않는다. `TodoSyncPayload`와 `NetworkTodoMutationPayload`가 전송하는 필드는 다음 7개로 고정한다.

| 필드 | Android source | 서버 왕복 정책 |
|---|---|---|
| `title` | `TodoEntity.title` | push/pull 대상 |
| `description` | Android 개인 Todo 모델에는 아직 없음 | 서버 계약에는 남기되 Android push에서는 null/미사용 |
| `dueDate` | `TodoEntity.dueDateEpochDay` | 날짜만 `YYYY-MM-DD`로 push/pull |
| `status` | `TodoEntity.isDone` | `ACTIVE`/`COMPLETED`, tombstone은 서버 `DELETED`로 수신 |
| `priority` | `TodoEntity.priority` | `LOW`/`MEDIUM`/`HIGH` push/pull |
| `categoryId` | `TodoEntity.categoryId` | 개인 Todo 카테고리 ID를 push/pull, 없으면 null |
| `dueTimeMinutes` | `TodoEntity.dueTimeMinutes` | 마감 시각을 0..1439 minute-of-day로 push/pull, 없으면 null |

다음 Android reminder 세부 필드는 현재 서버 Todo sync 계약에 포함하지 않는 로컬 전용 필드다. 누락이 아니라 MVP 계약 결정이며, 서버와 여러 기기 간 reminder 완전한 왕복이 필요해지면 별도 서버/API/TRD 개정으로 확장한다.

| 로컬 전용 필드 | 이유 |
|---|---|
| `reminderAtEpochMillis` | Todo 내장 reminder sync 계약이 없음 |
| `isReminderEnabled` | Todo 내장 reminder sync 계약이 없음 |
| `reminderRepeatType` | 반복 reminder sync 계약이 없음 |
| `reminderRepeatDaysMask` | 반복 reminder sync 계약이 없음 |
| `reminderLeadMinutes` | reminder lead sync 계약이 없음 |

### 5.3 Pull API

```http
GET /api/sync/todos?cursor=<revision>
```

`cursor`가 없으면 현재 사용자의 서버 Todo 전체 스냅샷을 내려준다. `cursor`가 있으면 해당 revision 이후 변경분만 내려준다. `revision`, `cursor`, `nextCursor`는 모두 API에서 decimal string으로 표현한다.

Response:

```json
{
  "todos": [
    {
      "id": "server-uuid",
      "clientId": "android-generated-uuid",
      "title": "Buy milk",
      "description": null,
      "dueDate": "2026-05-10",
      "status": "ACTIVE",
      "priority": "MEDIUM",
      "categoryId": 12,
      "dueTimeMinutes": 870,
      "revision": "42",
      "createdAt": "2026-05-08T00:00:00.000Z",
      "updatedAt": "2026-05-08T00:00:00.000Z",
      "deletedAt": null
    }
  ],
  "nextCursor": "42",
  "hasMore": false
}
```

MVP에서는 대량 pagination을 강제하지 않는다. 서버가 limit을 둔다면 `hasMore=true`일 때 Android는 `nextCursor`로 반복 pull한다.

Pull 세부 정책:
- `cursor` 없음: non-deleted Todo 전체와 `nextCursor=currentRevision` 반환
- `cursor` 있음: `revision > cursor` 변경분 반환, tombstone 포함
- invalid cursor: HTTP 400 `INVALID_CURSOR`
- cursor가 current revision보다 큼: empty list와 current revision 반환

### 5.4 Push API

```http
POST /api/sync/todos
```

Request:

```json
{
  "baseCursor": "41",
  "mutations": [
    {
      "clientMutationId": "mutation-uuid-1",
      "type": "CREATE",
      "clientId": "todo-client-uuid",
      "payload": {
        "title": "Buy milk",
        "description": null,
        "dueDate": "2026-05-10",
        "status": "ACTIVE",
        "priority": "MEDIUM",
        "categoryId": 12,
        "dueTimeMinutes": 870
      }
    },
    {
      "clientMutationId": "mutation-uuid-2",
      "type": "UPDATE",
      "id": "server-uuid",
      "payload": {
        "title": "Buy milk and eggs",
        "description": null,
        "dueDate": "2026-05-11",
        "status": "COMPLETED",
        "priority": "HIGH",
        "categoryId": 15,
        "dueTimeMinutes": 540
      }
    },
    {
      "clientMutationId": "mutation-uuid-3",
      "type": "DELETE",
      "id": "server-uuid"
    }
  ]
}
```

Response:

```json
{
  "results": [
    {
      "clientMutationId": "mutation-uuid-1",
      "status": "APPLIED",
      "todo": {
        "id": "server-uuid",
        "clientId": "todo-client-uuid",
        "title": "Buy milk",
        "description": null,
        "dueDate": "2026-05-10",
        "status": "ACTIVE",
        "priority": "MEDIUM",
        "revision": "42",
        "createdAt": "2026-05-08T00:00:00.000Z",
        "updatedAt": "2026-05-08T00:00:00.000Z",
        "deletedAt": null
      }
    }
  ],
  "nextCursor": "42"
}
```

Mutation result status:
- `APPLIED`: 정상 반영
- `DUPLICATE_APPLIED`: 이미 처리된 mutation이며 같은 결과 반환
- `DUPLICATE_CLIENT_ID`: 다른 mutation이지만 같은 `clientId` CREATE가 이미 처리되어 기존 Todo 반환
- `REJECTED_VALIDATION`: title 등 검증 실패
- `REJECTED_NOT_FOUND`: 대상 Todo 없음
- `REJECTED_DELETED`: 삭제된 Todo에 대한 update
- `REJECTED_IDEMPOTENCY_CONFLICT`: 같은 `clientMutationId`가 다른 payload로 재전송됨

MVP에서는 push response에 `changes`를 포함하지 않는다. Android는 push 후 pull을 다시 수행해 최종 수렴한다.

`baseCursor`는 MVP에서 충돌 검증에 사용하지 않는다. 서버는 observability/debug 용도로만 받을 수 있으며, 오래된 `baseCursor` 때문에 mutation을 reject하지 않는다.

HTTP error와 mutation result는 분리한다.
- 인증 실패: HTTP 401 공통 error
- malformed JSON, mutations 배열 아님, mutation 개수 제한 초과: HTTP 400 공통 error
- title invalid, not found, deleted 같은 개별 mutation 실패: HTTP 200 + mutation별 result

MVP mutation 배열 제한:
- `mutations.length <= 100`
- `clientId`, `clientMutationId`는 UUID string
- payload는 서버 validation을 통과해야 함

## 6. 서버 정합성 요구사항

### 6.1 Idempotency
CREATE는 `(userId, clientId)` 기준으로 idempotent해야 한다. 같은 `clientId`로 CREATE가 재시도되면 서버는 새 Todo를 만들지 않고 기존 Todo를 반환한다.

Mutation 처리 이력은 `(userId, clientMutationId)` 기준으로 저장한다. 같은 mutation이 재전송되면 `DUPLICATE_APPLIED`와 기존 결과를 반환한다.

Idempotency edge case:
- 같은 `clientMutationId` + 같은 payload: 저장된 결과를 `DUPLICATE_APPLIED`로 반환
- 같은 `clientMutationId` + 다른 payload: `REJECTED_IDEMPOTENCY_CONFLICT`
- 다른 `clientMutationId` + 같은 `clientId` CREATE: 새 Todo 생성 금지, 기존 Todo를 `DUPLICATE_CLIENT_ID`로 반환

### 6.2 Revision
서버는 Todo 변경마다 사용자 단위 revision을 단조 증가시킨다. Android `syncCursor`는 이 revision을 저장한다.

서버는 사용자별 revision counter를 별도 모델로 가진다.

```prisma
model TodoSyncState {
  userId          String @id @map("user_id") @db.Uuid
  currentRevision BigInt @default(0) @map("current_revision")
}
```

권장 서버 트랜잭션:
1. 사용자 sync counter row lock
2. mutation 검증
3. Todo create/update/delete
4. revision 증가 및 Todo에 기록
5. mutation result 기록

Push request는 mutations를 순서대로 처리하되, 각 mutation을 독립 transaction으로 처리한다. 한 mutation의 business reject는 전체 batch rollback을 일으키지 않는다. 앞 mutation이 `APPLIED`되고 뒤 mutation이 `REJECTED_VALIDATION`이면 앞 결과는 유지된다.

### 6.3 Soft Delete
DELETE는 물리 삭제하지 않는다. `status=DELETED`, `deletedAt != null`로 tombstone을 만든다. Pull API는 tombstone도 내려준다. Android는 tombstone을 받으면 로컬 화면에서 숨긴다.

### 6.4 충돌 정책
MVP는 Todo 단위 last-write-wins이다. 단, 삭제는 우선한다.

정책:
- server `DELETED` Todo에 UPDATE가 오면 `REJECTED_DELETED`와 tombstone Todo를 반환한다.
- 이미 `DELETED`인 Todo에 DELETE가 오면 no-op success로 tombstone Todo를 반환하고 revision은 증가시키지 않는다.
- UPDATE vs UPDATE 충돌은 서버 도착 순서가 이긴다.
- Android는 conflict UI를 띄우지 않고 서버 상태로 조용히 수렴한다.

### 6.5 서버 Prisma 모델 요구
서버는 최소 다음 저장 구조를 가진다.

```prisma
model TodoSyncMutation {
  userId           String
  clientMutationId String
  type             String
  status           String
  todoId           String?
  requestHash      String
  resultJson       Json
  createdAt        DateTime @default(now())

  @@id([userId, clientMutationId])
}
```

Todo 모델은 다음 sync 필드를 가져야 한다.
- `ownerUserId`
- `clientId`
- `description`
- `status`
- `priority`
- `categoryId`
- `dueTimeMinutes`
- `revision`
- `deletedAt`
- `createdAt`, `updatedAt`

`dueDate`는 API에서 `YYYY-MM-DD` string으로 고정한다. 서버는 request를 정규식과 실제 calendar date로 검증하고, response mapper에서 timezone에 흔들리지 않게 직접 `YYYY-MM-DD`로 직렬화한다.

## 7. Android 데이터 모델 변경

### 7.1 TodoEntity 확장
기존 `todo` 테이블에 sync 필드를 추가한다.

```kotlin
val serverId: String?
val clientId: String?
val ownerUserId: String?
val syncStatus: String
val serverRevision: Long?
val deletedAt: Long?
val syncHaltReason: String?
val lastSyncError: String?
```

`syncStatus` 값:
- `LOCAL_ONLY`
- `SYNCED`
- `PENDING_CREATE`
- `PENDING_UPDATE`
- `PENDING_DELETE`
- `FAILED`

기존 로컬 Todo는 이번 MVP에서 서버 마이그레이션하지 않는다. 공식 정책은 다음과 같다.
- 기존 로컬 Todo는 `LOCAL_ONLY`로 유지하고 서버 sync 대상에서 제외한다.
- 기존 로컬 Todo는 기존처럼 목록에 표시한다.
- `LOCAL_ONLY` Todo의 `clientId`, `ownerUserId`, `serverId`, `serverRevision`은 null 허용이다.
- 로그인 후 새로 생성되는 Todo만 `PENDING_CREATE`로 서버 동기화한다.
- migration test는 기존 Todo가 유실되지 않고 `LOCAL_ONLY`로 남으며 outbox에 들어가지 않는 것을 검증한다.
- 향후 기존 Todo 서버 마이그레이션은 별도 PRD/TRD에서 다룬다.

### 7.2 TodoOutboxEntity 추가

```kotlin
data class TodoOutboxEntity(
    val id: Long = 0,
    val clientMutationId: String,
    val todoLocalId: Long?,
    val serverId: String?,
    val clientId: String?,
    val type: String,
    val payloadJson: String,
    val createdAt: Long,
    val retryCount: Int,
    val lastError: String?
)
```

Outbox mutation type:
- `CREATE`
- `UPDATE`
- `DELETE`

### 7.3 Sync Metadata
사용자별 `syncCursor`를 저장해야 한다.

MVP 저장 위치:
- DataStore에 `todo_sync_cursor`

주의:
- 로그아웃 시 `todo_sync_cursor`와 서버 Todo 캐시/outbox 처리 정책이 필요하다.
- MVP에서는 로그아웃 시 auth session만 지우는 기존 정책을 유지하되, 다음 로그인 사용자가 바뀔 수 있으므로 서버 Todo 캐시와 outbox를 사용자별로 분리하거나 로그아웃 시 삭제해야 한다.

권장:
- `TodoEntity`에 `ownerUserId`를 추가한다.
- 모든 sync Todo는 현재 `AuthSession.user.id` 기준으로 저장/조회한다.
- 로그아웃 시 서버 Todo 캐시와 outbox는 삭제한다.
- 로그아웃 시 `todo_sync_cursor`도 삭제한다.
- 로그아웃 시 `LOCAL_ONLY` Todo는 삭제하지 않는다.
- 다른 계정 로그인 시 이전 계정의 서버 Todo/outbox/cursor는 노출되지 않아야 한다.

## 8. Android 동기화 알고리즘

### 8.1 앱 시작/로그인 후
1. auth session이 있고 onboarding이 끝났는지 확인한다.
2. `SyncTodosUseCase` 또는 repository sync method를 호출한다.
3. pull을 수행한다.
4. outbox를 push한다.
5. 마지막 pull을 수행한다.

401 또는 `AUTH_REQUIRED` 수신 시:
1. outbox는 보존한다.
2. sync는 `AUTH_REQUIRED` halt 상태로 전환한다.
3. 일반 네트워크 실패 retryCount와 분리한다.
4. 자동 재시도는 중단한다.
5. auth session 변경, 재로그인, 또는 명시적 session 복구 이후에만 sync를 재개한다.

### 8.2 오프라인 생성
1. 사용자가 Todo를 추가한다.
2. Android는 `clientId=UUID`를 생성한다.
3. `TodoEntity(serverId=null, syncStatus=PENDING_CREATE)`로 저장한다.
4. `TodoOutboxEntity(type=CREATE, clientMutationId=UUID, payloadJson=latest todo payload)`를 저장한다.
5. 화면에는 즉시 표시한다.

온라인 복귀:
1. CREATE mutation을 push한다.
2. 서버가 `id`, `revision`을 반환한다.
3. Android는 해당 로컬 Todo에 `serverId`, `serverRevision`, `syncStatus=SYNCED`를 반영한다.
4. outbox row를 삭제한다.

### 8.3 오프라인 생성 후 수정
아직 서버에 없는 `PENDING_CREATE` Todo를 수정하면 UPDATE mutation을 새로 만들지 않는다. 기존 CREATE payload를 최신 값으로 병합한다.

```text
PENDING_CREATE + edit -> update local Todo + update CREATE outbox payload
```

### 8.4 오프라인 생성 후 삭제
아직 서버에 없는 `PENDING_CREATE` Todo를 삭제하면 서버에 보낼 필요가 없다.

```text
PENDING_CREATE + delete -> delete local Todo + remove CREATE outbox
```

### 8.5 서버에 존재하는 Todo 수정
`SYNCED` Todo를 수정하면 로컬을 즉시 업데이트하고 UPDATE mutation을 outbox에 넣는다. 같은 Todo에 기존 UPDATE mutation이 있으면 payload를 최신 값으로 합친다.

### 8.6 서버에 존재하는 Todo 삭제
`SYNCED` Todo를 삭제하면 로컬에서는 즉시 목록에서 숨긴다.

```text
syncStatus=PENDING_DELETE
deletedAt=localNow
outbox DELETE 추가
```

서버 tombstone 응답을 받으면 로컬 row를 유지하되 목록 쿼리에서 제외한다. 후속 cleanup job에서 오래된 tombstone을 정리할 수 있다.

### 8.7 Outbox 상태 전이

| 현재 상태 | 사용자 동작 | 로컬 처리 | Outbox 처리 |
|---|---|---|---|
| `LOCAL_ONLY` | update/delete/toggle | 기존 로컬 전용 동작 유지 | 없음 |
| `SYNCED` | update/toggle | 즉시 로컬 반영, `PENDING_UPDATE` | UPDATE 추가 |
| `SYNCED` | delete | 목록에서 숨김, `PENDING_DELETE` | DELETE 추가 |
| `PENDING_CREATE` | update/toggle | 즉시 로컬 반영 | 기존 CREATE payload 갱신 |
| `PENDING_CREATE` | delete | 로컬 row 삭제 | CREATE outbox 삭제 |
| `PENDING_UPDATE` | update/toggle | 즉시 로컬 반영 | 기존 UPDATE payload 갱신 |
| `PENDING_UPDATE` | delete | 목록에서 숨김, `PENDING_DELETE` | UPDATE 제거 후 DELETE 추가 |
| `PENDING_DELETE` | update/toggle | 차단 | 없음 |
| `FAILED` | update | 수정값 반영, 재시도 가능 상태 | 실패 원인에 따라 CREATE/UPDATE payload 재생성 |

응답 유실 후 재시도:
- CREATE 응답이 유실되어 같은 `clientId` CREATE가 다시 전송되면 서버는 기존 Todo를 반환한다.
- Android는 `DUPLICATE_CLIENT_ID` 또는 `DUPLICATE_APPLIED` 결과를 성공 수렴으로 처리한다.

### 8.8 Remote Apply 규칙
Pull 결과를 로컬에 적용할 때 pending local 변경과 충돌할 수 있다.

- remote `DELETED` + local `PENDING_UPDATE`: local update outbox 제거, tombstone 적용
- remote `DELETED` + local `PENDING_DELETE`: delete outbox는 no-op success 기대, tombstone 적용 후 outbox 정리 가능
- remote UPDATE + local `PENDING_DELETE`: local delete 우선, DELETE outbox 유지
- remote UPDATE + local `PENDING_UPDATE`: local pending payload 유지, remote는 serverRevision 비교용으로만 반영하지 않음
- remote Todo + local `PENDING_CREATE` same `clientId`: 서버 Todo로 local row를 확정하고 CREATE outbox 정리

최종 timestamp 권위는 서버에 있다. Android local timestamp는 pending 상태의 임시 정렬/표시에만 사용하며, sync 완료 후 `createdAt`, `updatedAt`, `deletedAt`은 서버 값을 반영한다.

## 9. UI 정책

### 9.1 MVP UI 변경 최소화
기존 Todo 목록 UX를 유지한다. 서버 sync 상태는 MVP에서 작은 보조 표시만 허용한다.

표시 후보:
- `PENDING_*`: 동기화 대기 아이콘 또는 작은 텍스트
- `FAILED`: 재시도 가능 상태 표시

단, MVP 1차 구현에서는 UI 혼란을 줄이기 위해 실패 상태만 명시적으로 표시하고, 정상 pending은 조용히 둔다.

QA/debug 관측성을 위해 pending/sync 상태는 ViewModel state 또는 테스트 태그로 검증 가능해야 한다. 일반 사용자에게 과한 상태 문구를 노출하지 않는다.

### 9.2 실패 처리
서버 검증 실패는 `FAILED`로 남기고 Todo는 목록에 유지한다. 사용자가 수정하면 outbox payload를 새로 만들고 재시도할 수 있다.

네트워크 실패는 사용자에게 과한 snackbar를 반복 노출하지 않는다. 수동 새로고침 또는 앱 재진입/온라인 복귀에서 재시도한다.

## 10. 모듈 변경 범위

### 10.1 Android
- `core:model`
  - 필요 시 sync 상태 모델 추가
- `core:database`
  - `TodoEntity` sync 필드 추가
  - `TodoOutboxEntity`/DAO 추가
  - DB version 상승 및 migration/schema 갱신
- `core:datastore`
  - `todo_sync_cursor` 저장
- `core:network`
  - Sync DTO/API/DataSource 추가
- `core:data`
  - Todo sync repository/data source 조합
  - local/outbox/server mapper
  - auth access token 주입
- `core:domain`
  - `SyncTodosUseCase` 추가
  - 기존 add/update/delete/toggle use case가 sync-aware repository 동작을 사용
- `feature:todo:impl`
  - 최소한의 sync failed/pending UI가 필요할 때만 변경
- `app`
  - 로그인 후 sync trigger 또는 앱 시작 sync trigger

### 10.2 서버
- Prisma schema
  - Todo sync 필드, mutation idempotency table, user revision counter
- API routes
  - `GET /api/sync/todos`
  - `POST /api/sync/todos`
- Service
  - pull, push mutation transaction
- Tests
  - auth required
  - create idempotency
  - cursor pull
  - soft delete
  - deleted update rejection
  - mutation duplicate handling
- Docs
  - `docs/api-contract.md` Todo Sync 섹션

## 11. 테스트 계획

### 11.1 Android 단위 테스트
- Todo mapper가 server DTO를 local entity/domain으로 변환한다.
- `PENDING_CREATE` 생성 시 local Todo와 outbox가 함께 저장된다.
- `PENDING_CREATE` 수정 시 CREATE payload가 최신 값으로 병합된다.
- `PENDING_CREATE` 삭제 시 local Todo와 outbox가 제거된다.
- `SYNCED` Todo 수정 시 UPDATE outbox가 생성 또는 병합된다.
- `SYNCED` Todo 삭제 시 PENDING_DELETE와 DELETE outbox가 생성된다.
- pull tombstone 수신 시 목록에서 숨겨진다.
- push APPLIED 수신 시 serverId/revision/syncStatus가 확정된다.
- push REJECTED_VALIDATION 수신 시 FAILED로 남는다.
- 로그아웃 시 서버 Todo cache/outbox/cursor 처리 정책이 검증된다.
- 401/auth required 수신 시 outbox가 보존되고 무한 재시도가 발생하지 않는다.
- `PENDING_UPDATE + delete`는 UPDATE를 제거하고 DELETE만 남긴다.
- `PENDING_DELETE` 상태에서 edit/toggle은 차단된다.
- remote tombstone과 local pending update 충돌 시 tombstone으로 수렴한다.

### 11.2 Android DB/Migration 테스트
- 기존 DB version에서 신규 version으로 migration 성공
- 기존 로컬 Todo가 유실되지 않음
- 기존 로컬 Todo가 sync 대상에 잘못 포함되지 않음
- 기존 로컬 Todo가 `LOCAL_ONLY`로 남음
- `ownerUserId`, nullable `clientId`, server sync index가 expected schema와 일치
- outbox table 생성 검증
- Todo sync field index 검증

### 11.3 서버 테스트
- 인증 없는 sync 요청 실패
- CREATE 성공
- 같은 `clientId` CREATE 재시도 시 중복 생성 없음
- 같은 `clientMutationId` 재시도 시 duplicate result 반환
- cursor 이후 변경만 pull
- DELETE는 tombstone 생성
- 삭제된 Todo UPDATE는 rejected
- 타 사용자 Todo는 pull/push에서 격리
- 여러 mutation 처리 중 일부 실패 시 mutation별 결과 반환
- access token 만료/invalid token은 안정적인 error code로 응답
- invalid payload, title trim/length validation
- invalid/future cursor 처리
- 같은 user의 concurrent CREATE가 revision을 중복 발급하지 않음
- 같은 `clientId` concurrent CREATE가 Todo를 하나만 만듦
- mixed mutations에서 앞 mutation 성공, 뒤 mutation reject 시 앞 결과 유지

### 11.4 Android-Server 통합 테스트
서버 포함 MVP이므로 통합 성격 검증은 PR gate 필수다.

서버 repo는 test DB 기반 API integration test를 필수로 가진다. Android repo는 fake server 또는 server-backed API client contract test를 필수로 가진다.

필수 시나리오:
1. 로그인 세션 준비
2. Android sync data source로 CREATE push
3. pull로 생성 Todo 수신
4. UPDATE/COMPLETED push
5. pull로 완료 상태 수신
6. DELETE push
7. pull로 tombstone 수신

### 11.5 UI 테스트
필수 UI 테스트:
- 로그인 완료 상태에서 Todo 화면 진입
- 오프라인/fake network failure 상태에서 새 Todo 추가 시 목록에 즉시 표시
- sync 실패 fake 상태에서 실패 표시가 보임
- 완료 토글 후 화면 상태가 즉시 반영
- repository 재생성 후 서버 sync 결과가 목록에 반영

### 11.6 최종 PR 게이트
Android:
- `./gradlew :core:database:testDebugUnitTest`
- `./gradlew :core:data:testDebugUnitTest`
- `./gradlew :core:domain:testDebugUnitTest`
- `./gradlew :core:network:lintDebug`
- `./gradlew :feature:todo:impl:testDebugUnitTest`
- 필수 Todo sync UI 테스트
- PR 직전 `./gradlew testDebugUnitTest assembleDebug lint coverageVerifyAll`

Server:
- `npm test`
- `npm run build` 또는 Next.js build
- DB validate/migrate dry-run 가능 시 실행
- `npm run db:validate`

## 12. 오픈 질문
1. sync trigger를 어디에 둘 것인가?
   - 권장: 로그인 완료 직후, Todo 화면 진입, 앱 시작 시 auth session 존재, 네트워크 복귀 시 WorkManager.
   - MVP 1차는 로그인 완료 직후 + Todo 화면 진입 수동 sync로 시작하고 WorkManager는 후속 가능.
2. description을 Android 도메인 모델에 지금 추가할 것인가?
   - 권장: 서버 DTO에는 두되 Android UI/도메인에는 MVP에서 사용하지 않는다.
3. pending 상태를 UI에 얼마나 노출할 것인가?
   - 권장: 실패만 명시, pending은 조용히 처리.
4. access token refresh를 이번 Todo Sync MVP에 포함할 것인가?
   - 권장: 포함하지 않는다. 401이면 outbox를 보존하고 로그인/session 복구 후 재시도한다.
5. WorkManager 기반 백그라운드 sync를 이번 MVP에 포함할 것인가?
   - 권장: 1차는 명시적 trigger로 구현하고, WorkManager는 후속 안정화 단계에서 추가한다.

## 13. 구현 착수 조건
- 서버 에이전트가 API/Prisma/revision/idempotency 설계를 리뷰한다.
- QA 에이전트가 테스트 시나리오 누락을 리뷰한다.
- P0/P1 리뷰 항목을 TRD에 반영한다.
- 사용자에게 핵심 정책을 요약하고 승인받는다.
- 이후 Android와 서버가 병렬 구현한다.
