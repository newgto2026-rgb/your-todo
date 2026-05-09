# TRD - Shared Todo MVP

## 1. 문서 정보
- 문서명: 기술 요구사항 문서 - Shared Todo MVP
- 대상 프로젝트: `YourTodo` Android 앱 + `yourtodo-server`
- Android 기준 브랜치: `codex/shared-todo-mvp`
- 서버 기준 브랜치: `codex/shared-todo-mvp`
- 작성일: 2026-05-09
- 상태: PRD 반영, 구현 착수 전 계약 문서

## 2. 기술 목표
- 친구가 보내는 할 일을 bundle 단위로 생성하고 item 단위로 수락/거절/진행한다.
- Android와 서버가 같은 enum, endpoint, 상태 전이, 권한 정책을 사용한다.
- 친구가 준 할 일은 받은 사람이 수락하기 전까지 기존 개인 Todo 목록에 섞지 않는다.
- 수락한 받은 일은 기존 Todo 목록에서 조회 가능하되 `받은 일`로 구분한다.
- 수신자는 본문을 수정하지 못하고 리마인더만 개인 설정으로 수정한다.
- 서버는 모든 권한/상태 전이를 강제한다.
- 푸시 발송은 후속으로 분리 가능하지만 이벤트/딥링크 payload는 이번 계약에 포함한다.

## 3. 도메인 용어
```text
AssignmentBundle
- 친구에게 한 번에 보낸 묶음
- 알림, 딥링크, 수락 화면, 요약의 단위

AssignedTodo
- bundle 안의 실제 할 일 항목
- 수락/거절/진행/완료/취소의 단위

Received Todo
- receiver가 AssignedTodo를 수락하면 Todo 목록에 노출되는 받은 일
- 기존 개인 Todo와 구분되는 source를 가진다
```

## 4. 서버 상태 모델
### 4.1 Bundle Status
```text
DRAFT
SENT
PARTIALLY_DECIDED
ACCEPTED
REJECTED
DECIDED_PARTIAL
CANCELED
```

MVP에서 `DRAFT`는 Android 로컬 draft를 뜻한다. 서버는 최종 전송된 bundle만 저장한다. 단, 서버 enum에는 후속 확장을 위해 `DRAFT`를 남길 수 있다.

집계 규칙:
- 모든 item이 `PENDING_ACCEPTANCE`: `SENT`
- pending과 non-pending이 섞임: `PARTIALLY_DECIDED`
- 모든 item이 accepted 계열: `ACCEPTED`
- 모든 item이 `REJECTED`: `REJECTED`
- 모든 item이 `CANCELED`: `CANCELED`
- pending 없이 accepted 계열, rejected, canceled가 섞임: `DECIDED_PARTIAL`

accepted 계열은 `ACCEPTED`, `IN_PROGRESS`, `DONE`이다.

### 4.2 Assigned Todo Status
```text
PENDING_ACCEPTANCE
ACCEPTED
IN_PROGRESS
DONE
REJECTED
CANCELED
```

### 4.3 Terminal Reason
```text
REJECTED_BY_RECEIVER
DELETED_BY_RECEIVER
CANCELED_BY_SENDER
```

삭제는 별도 active state가 아니라 `REJECTED + terminalReason=DELETED_BY_RECEIVER`로 처리한다.

### 4.4 상태 전이
```text
PENDING_ACCEPTANCE -> ACCEPTED
PENDING_ACCEPTANCE -> REJECTED
PENDING_ACCEPTANCE -> CANCELED

ACCEPTED -> IN_PROGRESS
ACCEPTED -> DONE
ACCEPTED -> REJECTED (DELETED_BY_RECEIVER)
ACCEPTED -> CANCELED

IN_PROGRESS -> DONE
IN_PROGRESS -> REJECTED (DELETED_BY_RECEIVER)
IN_PROGRESS -> CANCELED
```

`DONE`은 terminal이다. sender는 완료된 항목을 취소할 수 없다.

## 5. 서버 API 계약
모든 endpoint는 `Authorization: Bearer <accessToken>`을 요구한다.

### 5.1 Bundle 생성
```http
POST /api/assignment-bundles
Idempotency-Key: <client-generated-uuid>
```

Request:
```json
{
  "receiverUserId": "receiver-user-id",
  "items": [
    {
      "clientItemId": "uuid-1",
      "title": "발표 자료 정리",
      "description": "금요일 전까지 초안",
      "dueDate": "2026-05-15",
      "priority": "HIGH",
      "category": "work",
      "checklist": [
        {
          "clientItemId": "check-1",
          "title": "목차 작성"
        }
      ]
    }
  ]
}
```

정책:
- `items`는 1개 이상이다. 즉시 보내기는 item 1개짜리 bundle이다.
- `reminder` 필드는 request에 포함하지 않는다. 포함되면 `INVALID_REQUEST`로 거부한다.
- receiver는 active friend여야 한다.
- 같은 요청 재시도는 `Idempotency-Key`로 같은 응답을 반환한다.
- 같은 idempotency key와 다른 payload는 `409 IDEMPOTENCY_CONFLICT`를 반환한다.
- bundle 생성은 all-or-nothing이다. 한 item이라도 validation 실패하면 전체 실패하고 알림 이벤트도 만들지 않는다.
- 성공 시 `ASSIGNMENT_BUNDLE_RECEIVED` 이벤트를 bundle 기준 1개 생성한다.

Response:
```json
{
  "bundle": {
    "id": "bundle-id",
    "sender": {
      "id": "sender-id",
      "nickname": "neo"
    },
    "receiver": {
      "id": "receiver-id",
      "nickname": "monday"
    },
    "status": "SENT",
    "summary": {
      "totalCount": 2,
      "pendingCount": 2,
      "acceptedCount": 0,
      "rejectedCount": 0,
      "canceledCount": 0,
      "doneCount": 0
    },
    "createdAt": "2026-05-09T00:00:00.000Z",
    "updatedAt": "2026-05-09T00:00:00.000Z"
  },
  "items": [
    {
      "id": "assigned-todo-id",
      "bundleId": "bundle-id",
      "title": "발표 자료 정리",
      "description": "금요일 전까지 초안",
      "dueDate": "2026-05-15",
      "priority": "HIGH",
      "category": "work",
      "status": "PENDING_ACCEPTANCE",
      "progressPercent": 0,
      "terminalReason": null,
      "checklist": [
        {
          "id": "checklist-id",
          "title": "목차 작성",
          "sortOrder": 0,
          "completed": false
        }
      ],
      "createdAt": "2026-05-09T00:00:00.000Z",
      "updatedAt": "2026-05-09T00:00:00.000Z"
    }
  ]
}
```

### 5.2 Bundle 상세 조회
```http
GET /api/assignment-bundles/{bundleId}
```

권한:
- sender 또는 receiver만 조회 가능.
- 제3자는 `404 RESOURCE_NOT_FOUND` 또는 `403` 대신 존재 노출을 막기 위해 `404`를 권장한다.

### 5.3 Todo 목록 통합 정책
기존 `/api/sync/todos`는 개인 Todo만 반환한다. 수락한 받은 일은 아래 received assigned todo feed로 별도 조회하고 Android repository/ViewModel에서 합성한다.

```http
GET /api/assigned-todos/received?status=active|pending|history
GET /api/assigned-todos/sent?status=active|pending|history
```

`GET /api/assigned-todos/received?status=active` 응답 item은 Todo 목록 렌더링에 필요한 `source`, `sender`, `readOnlyFields`, `reminder`를 포함한다.

```json
{
  "items": [
    {
      "id": "assigned-todo-id",
      "source": "ASSIGNED",
      "sender": {
        "id": "sender-id",
        "nickname": "neo"
      },
      "title": "장보기",
      "description": "우유랑 계란 사기",
      "dueDate": "2026-05-10",
      "status": "ACCEPTED",
      "progressPercent": 0,
      "readOnlyFields": ["title", "description", "dueDate", "priority", "category", "checklistTitle"],
      "reminder": null
    }
  ]
}
```

### 5.4 친구별 Bundle/Item 조회
```http
GET /api/friends/{friendUserId}/assignment-bundles?direction=sent|received
GET /api/friends/{friendUserId}/assigned-todos?direction=sent|received&status=active|pending|history
GET /api/friends/{friendUserId}/assignment-summary
```

`status=active`:
- received: `ACCEPTED`, `IN_PROGRESS`
- sent: `PENDING_ACCEPTANCE`, `ACCEPTED`, `IN_PROGRESS`

`status=pending`:
- `PENDING_ACCEPTANCE`

`status=history`:
- `DONE`, `REJECTED`, `CANCELED`

### 5.5 묶음 항목 결정
```http
POST /api/assignment-bundles/{bundleId}/decide-items
Idempotency-Key: <client-generated-uuid>
```

Request:
```json
{
  "decisions": [
    {
      "assignedTodoId": "item-1",
      "decision": "ACCEPT"
    },
    {
      "assignedTodoId": "item-2",
      "decision": "REJECT"
    }
  ]
}
```

정책:
- receiver만 호출 가능.
- decision은 `ACCEPT` 또는 `REJECT`.
- 같은 request 안에 같은 `assignedTodoId`가 중복되면 `400 INVALID_REQUEST`.
- 모든 item은 bundle에 속해야 한다.
- 모든 item은 `PENDING_ACCEPTANCE` 상태여야 한다.
- 선택 action 단위는 all-or-nothing이다. 하나라도 이미 처리/취소된 item이 있으면 전체 요청은 실패한다.
- 실패 응답은 공통 error shape를 유지하고 `error.details.bundle`에 최신 bundle snapshot을 포함한다.
- 같은 idempotency key와 같은 payload 재시도는 이전 응답을 반환한다.
- 같은 idempotency key와 다른 payload는 `409 IDEMPOTENCY_CONFLICT`를 반환한다.
- 수락된 item은 received Todo로 노출된다.
- 거절된 item은 Todo로 노출되지 않는다.

Response:
```json
{
  "bundle": {
    "id": "bundle-id",
    "status": "PARTIALLY_DECIDED",
    "summary": {
      "totalCount": 5,
      "pendingCount": 2,
      "acceptedCount": 2,
      "rejectedCount": 1,
      "canceledCount": 0,
      "doneCount": 0
    }
  },
  "items": [
    {
      "id": "item-1",
      "status": "ACCEPTED",
      "terminalReason": null
    },
    {
      "id": "item-2",
      "status": "REJECTED",
      "terminalReason": "REJECTED_BY_RECEIVER"
    }
  ]
}
```

실패 응답 예:
```json
{
  "error": {
    "code": "INVALID_STATUS_TRANSITION",
    "message": "Some items are no longer pending.",
    "details": {
      "bundle": {
        "id": "bundle-id",
        "status": "PARTIALLY_DECIDED",
        "summary": {
          "totalCount": 5,
          "pendingCount": 2,
          "acceptedCount": 2,
          "rejectedCount": 1,
          "canceledCount": 0,
          "doneCount": 0
        }
      }
    }
  }
}
```

### 5.6 받은 일 삭제
```http
POST /api/assigned-todos/{assignedTodoId}/delete-received
Idempotency-Key: <client-generated-uuid>
```

정책:
- receiver만 호출 가능.
- `ACCEPTED`, `IN_PROGRESS`에서만 가능.
- 서버는 `REJECTED + terminalReason=DELETED_BY_RECEIVER`로 전이한다.
- 받은 사람 Todo 목록에서는 제거된다.
- sender 화면에는 거절 계열 상태로 표시된다.

### 5.7 진행/완료
```http
POST /api/assigned-todos/{assignedTodoId}/start
PATCH /api/assigned-todos/{assignedTodoId}/checklist/{checklistItemId}
POST /api/assigned-todos/{assignedTodoId}/complete
```

정책:
- receiver만 호출 가능.
- `start`는 `ACCEPTED`에서만 가능하며 결과 상태는 `IN_PROGRESS`다.
- `checklist`는 `ACCEPTED`, `IN_PROGRESS`에서만 가능하다. 체크 시 필요하면 `IN_PROGRESS`로 전이한다.
- `complete`는 `ACCEPTED`, `IN_PROGRESS`에서 가능하며 결과 상태는 `DONE`이다.
- `PENDING_ACCEPTANCE`, `REJECTED`, `CANCELED`, `DONE`에서는 진행 mutation을 거부한다.
- 본문 수정은 불가.
- 체크리스트 title 수정은 불가.
- 체크리스트 완료 여부만 변경 가능.
- progressPercent는 서버가 계산한다.
- `complete` 성공 시 `ASSIGNED_TODO_COMPLETED` 이벤트를 생성한다.

`PATCH /checklist/{checklistItemId}` request:
```json
{
  "completed": true
}
```

진행 mutation response:
```json
{
  "item": {
    "id": "assigned-todo-id",
    "status": "IN_PROGRESS",
    "progressPercent": 50,
    "checklist": [
      {
        "id": "checklist-id",
        "completed": true,
        "completedAt": "2026-05-09T00:00:00.000Z"
      }
    ],
    "updatedAt": "2026-05-09T00:00:00.000Z"
  },
  "bundle": {
    "id": "bundle-id",
    "status": "ACCEPTED",
    "summary": {
      "totalCount": 1,
      "pendingCount": 0,
      "acceptedCount": 1,
      "inProgressCount": 1,
      "doneCount": 0,
      "rejectedCount": 0,
      "canceledCount": 0
    }
  }
}
```

### 5.8 리마인더
```http
PUT /api/assigned-todos/{assignedTodoId}/my-reminder
DELETE /api/assigned-todos/{assignedTodoId}/my-reminder
```

정책:
- receiver만 호출 가능.
- reminder는 개인 설정이며 sender에게 노출하지 않는다.
- `reminderAt`은 ISO-8601 UTC instant로 저장한다.

### 5.9 보낸 일 취소
```http
POST /api/assigned-todos/{assignedTodoId}/cancel
Idempotency-Key: <client-generated-uuid>
```

정책:
- sender만 호출 가능.
- `PENDING_ACCEPTANCE`, `ACCEPTED`, `IN_PROGRESS`에서 가능.
- `DONE`, `REJECTED`, `CANCELED`에서는 불가.
- 성공 시 `ASSIGNED_TODO_CANCELED` 이벤트를 생성한다.

## 6. 서버 DB 모델 초안
```text
AssignmentBundle
- id
- senderUserId
- receiverUserId
- status
- createdAt
- updatedAt
- decidedAt nullable
- canceledAt nullable

AssignedTodo
- id
- bundleId
- senderUserId
- receiverUserId
- clientItemId
- title
- description nullable
- dueDate nullable
- priority nullable
- category nullable
- status
- terminalReason nullable
- acceptedAt nullable
- startedAt nullable
- completedAt nullable
- canceledAt nullable
- progressPercent
- createdAt
- updatedAt

AssignedTodoChecklistItem
- id
- assignedTodoId
- title
- sortOrder
- completed
- completedAt nullable

AssignedTodoReminder
- id
- assignedTodoId
- userId
- reminderAt
- enabled
- updatedAt

NotificationEvent
- id
- type
- actorUserId nullable
- receiverUserId
- bundleId nullable
- assignedTodoId nullable
- payload json
- status
- createdAt
- sentAt nullable
```

## 7. Android 아키텍처 영향
### 7.1 영향 모듈
- `:core:model`: bundle, assigned todo, summary, reminder 모델 추가.
- `:core:domain`: shared todo repository 계약과 use case 추가.
- `:core:network`: Retrofit API/DTO 추가.
- `:core:data`: repository 구현, DTO 매핑, local draft 저장 전략.
- `:core:database`: MVP에서는 기존 Todo table과 섞지 않고 received assigned todo cache/draft가 필요할 때 별도 entity를 사용한다.
- `:feature:friends:impl`: 친구 작업 bottom sheet, 보낸 일/받은 일/진행 상황 진입.
- `:feature:todo:impl`: `전체/내 할 일/받은 일` 필터와 받은 일 라벨.
- 신규 feature가 필요하면 `:feature:sharedtodo:*`를 추가할 수 있으나, MVP는 `friends`와 `todo`에 걸친 기능이므로 공개 route/entry 설계를 먼저 확정한다.

의존 방향:
```text
feature:*:impl -> core:domain, core:model
core:data -> core:domain + core:network + storage modules
core:network -> DTO/Retrofit only
core:* -> feature:* 금지
```

### 7.2 Android 로컬 Draft
MVP에서는 서버 draft bundle을 만들지 않는다.

로컬 draft 정책:
- friendUserId별 draft bundle을 Android 로컬 상태로 관리한다.
- 앱 재시작 후 draft 유지가 필요하면 DataStore/Room 중 하나를 선택한다.
- 첫 구현에서는 ViewModel 상태만으로 시작할 수 있지만, 사용자가 외출/작성 중 이탈할 가능성을 고려하면 Room draft entity가 더 안전하다.
- draft에는 reminder를 저장하지 않는다.

### 7.3 화면 구조
```text
FriendsScreen
  FriendList
  FriendActionSheet
    SendTodo
    SentTodos
    ReceivedTodos
    ProgressSummary

SendAssignmentScreen
  ModeSegmentedControl: 즉시 보내기 / 묶어서 보내기
  TodoInputWithoutReminder
  BundleDraftList
  ReviewAndSend

AssignmentBundleDecisionScreen
  BundleHeader
  SelectAllRow
  AssignedTodoCheckboxList
  BottomActionBar

AssignedTodoDetailScreen
  ReadOnlyContent
  ReceiverReminderEditor
  ProgressActions
```

### 7.4 UI State 원칙
- 화면 문구 선택, 버튼 활성화, 상태 chip, 선택 개수는 ViewModel/UI mapper에서 계산한다.
- Compose는 `UiState`를 렌더링하고 Action만 전달한다.
- loading 중 전송/수락/거절 버튼 재탭을 막는다.
- 처리 후 서버 응답으로 최신 bundle 상태를 반영한다.

## 8. 테스트 전략
### 8.1 서버 테스트
- bundle 생성 성공, 친구가 아닌 receiver 실패.
- reminder 필드 포함 시 `INVALID_REQUEST`.
- bundle 생성 all-or-nothing.
- idempotency key 중복 요청이 같은 응답 반환.
- receiver만 bundle 조회/decide 가능.
- selected decisions all-or-nothing.
- 일부 수락 후 재조회 시 summary 정확.
- 이미 처리된 item 재처리 실패.
- delete-received가 `REJECTED + DELETED_BY_RECEIVER`로 전이.
- sender cancel 권한/상태 전이.
- done item cancel 불가.
- notification event가 bundle 기준 1회 생성.

### 8.2 Android 단위 테스트
- 친구 action sheet 상태 구성.
- 즉시 보내기와 묶어서 보내기 mode별 버튼/문구.
- 묶음 draft 추가/수정/삭제.
- review 화면 item count.
- decision 화면 전체 선택/부분 선택/0개 선택 버튼 상태.
- accept/reject 성공 후 bundle summary 반영.
- stale bundle conflict 시 최신 상태 표시.
- 받은 일 상세에서 본문 수정 action 없음.
- reminder 수정 action만 repository 호출.
- Todo 필터 `전체/내 할 일/받은 일`.

### 8.3 Android UI 테스트
- 친구 탭 -> 친구 선택 -> action sheet 표시.
- 할 일 보내기에서 리마인더 UI가 보이지 않음.
- 묶어서 보내기 draft에 여러 항목 추가 후 검토 화면 표시.
- 묶음 수락 화면에서 checkbox 선택, 전체 선택, `n개 수락/거절` 문구 확인.
- 받은 일 필터에서 친구가 보낸 일만 노출.
- 친구 삭제 휴지통 아이콘과 확인 dialog 표시.

## 9. 구현 단계
### STEP 1. 계약/문서
- Android PRD/TRD 작성.
- 서버 PRD/API contract 최신화.
- enum/error code/endpoint 이름 고정.

### STEP 2. 서버 모델/API
- Prisma schema/migration 추가.
- bundle create, detail, friend summary, decide-items 구현.
- delete-received, cancel, progress API 구현.
- service 단위 테스트와 route 테스트 추가.

### STEP 3. Android Domain/Network/Data
- core model/use case/repository 계약 추가.
- Retrofit DTO/API 추가.
- repository implementation/fake 추가.
- mapper/unit test 추가.

### STEP 4. Android UI
- 친구 action sheet 확장.
- send mode/draft/review UI.
- bundle decision UI.
- todo filter/received label.
- received detail read-only/reminder action.

### STEP 5. 검증/PR
- 서버 테스트, build, prisma validate.
- Android 영향 모듈 unit test/lint.
- 필요한 UI test.
- 서버 PR과 Android PR 모두 생성.

## 10. 구현 중 주의
- 서버와 Android enum 문자열은 대소문자까지 동일해야 한다.
- Android 표시 문구는 서버 `message`에 의존하지 않고 string resource에서 관리한다.
- 받은 일 삭제를 물리 삭제로 구현하지 않는다.
- bundle progress는 수락된 item만 분모로 계산한다.
- 푸시 이벤트는 만들되 실제 FCM 발송 실패가 핵심 트랜잭션을 롤백하지 않게 설계한다.
