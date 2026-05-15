# TRD - Direct Assignment MVP

## 1. 문서 정보
- 문서명: 기술 요구사항 문서 - Direct Assignment MVP
- 대상 프로젝트: `YourTodo` Android 앱 + `yourtodo-server`
- Android 기준 브랜치: `codex/force-assignment`
- 작성일: 2026-05-15
- 상태: PRD 및 최종 에이전트 리뷰 반영, Android 구현 검증 완료

## 2. 기술 목표
- 기존 shared todo bundle 생성 계약에 `assignmentMode`를 추가한다.
- `REQUEST`와 `DIRECT`를 상태가 아니라 생성 방식으로 분리한다.
- `DIRECT` 성공 항목은 `status=ACCEPTED`로 내려오며, Android는 `assignmentMode=DIRECT`를 함께 보존한다.
- 자동 할당 권한 summary는 Friend model에서 소비한다.
- Todo, Calendar, Widget, Friends detail이 같은 cache/model을 보고 일관되게 렌더링한다.

## 3. 모델 계약
```text
AssignmentMode
- REQUEST
- DIRECT
```

```text
DirectAssignmentConsentState
- NONE
- PENDING
- ACTIVE
- REVOKED
- EXPIRED
```

```text
DirectAssignmentConsentSummary
- grantedByMe: friend can direct-assign to me
- grantedToMe: I can direct-assign to friend
```

권한 판단은 Android UI에서 편의를 위해 표시하지만, 서버가 항상 최종 판정한다.

## 4. 서버 API 계약
### 4.1 Bundle 생성
```http
POST /api/assignment-bundles
Idempotency-Key: <uuid>
Authorization: Bearer <accessToken>
```

Request:
```json
{
  "receiverUserId": "friend-user-id",
  "assignmentMode": "DIRECT",
  "items": [
    {
      "clientItemId": "uuid",
      "title": "자료 검토",
      "description": null,
      "dueDate": "2026-05-20",
      "dueTimeMinutes": 870,
      "priority": "MEDIUM",
      "category": null
    }
  ]
}
```

정책:
- `assignmentMode` 기본값은 `REQUEST`다.
- `REQUEST` 성공 item status는 기존처럼 `PENDING_ACCEPTANCE`다.
- `DIRECT` 성공 item status는 `ACCEPTED`다.
- `DIRECT`는 receiver가 sender에게 자동 할당 권한을 허용한 경우에만 성공한다.
- 권한이 없거나 철회/만료되면 서버는 실패를 반환하고 Android는 editor를 유지한다.
- Android는 bundle 생성 호출마다 `Idempotency-Key`와 item별 `clientItemId`를 전송한다.
- 서버는 같은 `Idempotency-Key` 재처리를 idempotent하게 응답해야 하며, timeout 후 사용자가 같은 intent를 다시 제출하는 경우를 위해 receiver/sender/items/mode 기준 semantic duplicate 방지 정책도 가져야 한다.
- Android MVP는 수동 재시도 간 안정적인 operation id를 영속화하지 않는다. 따라서 DIRECT 중복 생성 방지는 서버 계약이며, 서버가 최종 보장하지 않으면 Todo/Calendar/Widget에 같은 자동 할당이 중복 노출될 수 있다.
- 같은 `Idempotency-Key`에 다른 payload가 오면 서버는 기존 생성 결과를 재사용하지 말고 conflict로 처리해야 한다.
- semantic duplicate 판정에는 `assignmentMode`가 포함되어야 한다. 같은 item이라도 `REQUEST`와 `DIRECT`는 서로 다른 사용자 의미를 가진다.

### 4.2 권한 action
```http
POST /api/friends/{friendUserId}/direct-assignment-consent/request
POST /api/friends/{friendUserId}/direct-assignment-consent/accept
POST /api/friends/{friendUserId}/direct-assignment-consent/reject
POST /api/friends/{friendUserId}/direct-assignment-consent/revoke
```

Response:
```json
{
  "directAssignment": {
    "grantedByMe": "ACTIVE",
    "grantedToMe": "PENDING"
  }
}
```

Idempotency/상태 전이 정책:
- Android는 consent action마다 `Idempotency-Key`를 보낸다.
- 같은 key와 같은 friend/action replay는 같은 결과 summary를 반환한다.
- 같은 key로 다른 friend/action이 오면 서버는 conflict로 처리한다.
- `request`: 이미 `PENDING`이면 같은 summary를 반환하고, 이미 `ACTIVE`면 성공 summary로 수렴한다.
- `accept`: 이미 `ACTIVE`면 성공 summary로 수렴한다.
- `reject`: 이미 `REVOKED`이거나 요청이 사라진 경우에도 최신 summary를 반환해 UI가 새 상태로 수렴하게 한다.
- `revoke`: 이미 `REVOKED/NONE`이어도 최신 summary를 반환한다.
- 모든 consent action은 권한 방향을 서버에서 재검증한다.

### 4.3 Friend response
Friend list/detail response는 다음 summary를 포함한다.

```json
{
  "friendshipId": "friendship-id",
  "userId": "friend-user-id",
  "nickname": "monday",
  "status": "ACTIVE",
  "directAssignment": {
    "grantedByMe": "NONE",
    "grantedToMe": "ACTIVE"
  }
}
```

### 4.4 Assigned todo feed
`NetworkAssignedTodo`와 mutation item은 `assignmentMode`를 포함한다.

```json
{
  "id": "assigned-id",
  "status": "ACCEPTED",
  "assignmentMode": "DIRECT"
}
```

Mutation response가 `assignmentMode`를 생략하면 Android는 기존 cache의 mode를 보존한다.

### 4.5 Notification event
권한 변경과 할 일 도착은 별도 이벤트로 분리한다. `ASSIGNMENT_BUNDLE_RECEIVED`는 REQUEST 수락 플로우 전용 의미가 있으므로 DIRECT에는 재사용하지 않는다.

```text
DIRECT_ASSIGNMENT_CONSENT_REQUESTED
DIRECT_ASSIGNMENT_CONSENT_ACCEPTED
DIRECT_ASSIGNMENT_CONSENT_REJECTED
DIRECT_ASSIGNMENT_CONSENT_REVOKED
DIRECT_ASSIGNMENT_RECEIVED
```

공통 payload:
```json
{
  "type": "DIRECT_ASSIGNMENT_CONSENT_REQUESTED",
  "notificationEventId": "event-id",
  "actorUserId": "friend-user-id",
  "actorNickname": "monday",
  "deepLink": "yourtodo://friends/direct-assignment"
}
```

DIRECT 할 일 도착 payload:
```json
{
  "type": "DIRECT_ASSIGNMENT_RECEIVED",
  "notificationEventId": "event-id",
  "bundleId": "bundle-id",
  "assignedTodoId": "assigned-id",
  "actorUserId": "friend-user-id",
  "actorNickname": "monday",
  "itemTitle": "자료 검토",
  "assignmentMode": "DIRECT"
}
```

Android 라우팅:
- `DIRECT_ASSIGNMENT_RECEIVED`는 Todo surface로 이동하고 workspace sync를 요청한다.
- `DIRECT_ASSIGNMENT_CONSENT_REQUESTED`는 Friends top-level로 이동한 뒤 Profile 권한 관리 drawer를 열고 workspace sync를 요청한다.
- 다른 consent 결과 이벤트는 Friends/Profile 권한 상태를 다시 확인할 수 있도록 Friends surface로 이동하고 workspace sync를 요청한다.
- `ASSIGNMENT_BUNDLE_RECEIVED`만 incoming assignment decision route를 연다.

## 5. Android 계층별 변경
### 5.1 core:model
- `AssignmentMode` enum 추가.
- `AssignedTodo.assignmentMode` 추가, 기본값 `REQUEST`.
- `Friend.directAssignment` summary 추가.

### 5.2 core:domain
- `CreateAssignmentBundleUseCase`가 `assignmentMode`를 받는다.
- `ManageDirectAssignmentConsentUseCase` 추가.
- `AssignmentRepository`에 direct consent action을 추가한다.
- 기존 호출 편의를 위해 `REQUEST` 기본 create helper는 유지하되, repository 구현체와 test fake는 mode-aware `createBundle(receiverUserId, items, assignmentMode)`를 반드시 구현한다.
- 이렇게 하지 않으면 DIRECT 호출이 default `REQUEST`로 조용히 떨어지는 회귀가 생긴다.
- 프로필과 친구 탭은 같은 direct consent use case를 사용한다.

### 5.3 core:network
- create bundle request에 `assignmentMode` 추가.
- assigned todo DTO와 mutation DTO에 `assignmentMode` 추가.
- friend DTO에 direct consent summary 추가.
- direct consent action endpoint 추가.

### 5.4 core:data
- network DTO를 domain `AssignmentMode`로 매핑한다.
- mutation partial response에서는 기존 entity의 `assignmentMode`를 보존한다.
- friend response의 direct consent summary를 domain으로 매핑한다.

### 5.5 core:database
- Room version `10 -> 11`.
- `assigned_todo.assignmentMode TEXT NOT NULL DEFAULT 'REQUEST'`.
- migration test로 기존 row default를 검증한다.

### 5.6 feature:friends:impl
- 친구 상세에 자동 할당 권한 상태와 action을 노출한다.
- 할 일 전송 sheet에서 `REQUEST`/`DIRECT` mode를 선택한다.
- 권한이 없는 방향의 `DIRECT` 전송은 UI에서 차단하고, 서버 실패도 성공 처리하지 않는다.
- detail 카드에는 mode chip과 status chip을 함께 보여준다.
- pending decision list는 `REQUEST + PENDING_ACCEPTANCE`만 포함한다.

### 5.6.1 app profile menu
- 프로필 메뉴는 내 Todo에 바로 할당할 수 있는 친구와 받은 권한 요청을 전역으로 보여준다.
- 프로필에서 요청 허용/거절/권한 끄기를 할 수 있다.
- 프로필 권한 목록은 서버 friend summary를 읽고 direct consent action은 친구 탭과 같은 use case를 사용한다.
- 로그아웃/세션 없음 상태에서는 권한 목록을 노출하지 않는다.

### 5.7 feature:todo:impl
- 받은 assigned todo row에 mode별 source label을 보여준다.
- `REQUEST`: `요청 수락 · @nickname`
- `DIRECT`: `자동 할당 · @nickname`
- edit sheet title은 DIRECT일 때 `할당받은 할 일`이다.

### 5.8 feature:calendar:impl
- selected-date agenda는 Todo row와 같은 mode/source label을 보여준다.
- month indicator는 mode별 색상 차이를 두지 않고 count에만 반영한다.
- pending request는 visible assigned todo use case에서 제외되어 Calendar에 보이지 않는다.

### 5.9 feature:calendar:widget
- visible received assigned todo를 합성하므로 DIRECT due date 항목은 count/chip에 포함된다.
- REQUEST pending은 visible use case에서 제외되어 widget에 보이지 않는다.

## 6. 테스트 기준
- domain: direct mode create delegation, consent action delegation, pending 제외/visible 포함 정책.
- network: `assignmentMode` serialization/deserialization.
- data: direct create request, cache round-trip, mutation partial response mode preservation, consent action mapping.
- database: 10->11 migration default.
- friends: directional consent, direct send success/failure, direct pending exclusion.
- todo: direct row/editor mode 유지.
- calendar: direct selected-date agenda/month summary 포함, pending 제외.
- widget: direct due date 포함, pending 제외.
- app profile: incoming direct consent 목록, 허용/거절/끄기 action, 로그아웃 노출 차단.
- push: direct consent/direct received title/body, direct received click routing이 decision route로 가지 않는지, consent request click이 Profile 권한 관리 표면을 여는지.

## 6.1 Android 검증 결과
- `./gradlew :core:domain:test :core:network:testDebugUnitTest :core:database:testDebugUnitTest :core:data:testDebugUnitTest :feature:friends:impl:testDebugUnitTest :feature:todo:impl:testDebugUnitTest :feature:calendar:impl:testDebugUnitTest :feature:calendar:widget:testDebugUnitTest :app:testDebugUnitTest`
- `./gradlew :app:lintDebug :core:network:lintDebug :core:database:lintDebug :core:data:lintDebug :feature:friends:impl:lintDebug :feature:todo:impl:lintDebug :feature:calendar:impl:lintDebug :feature:calendar:widget:lintDebug`
- `./gradlew assembleDebug :app:assembleDebugAndroidTest`
- `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.neo.yourtodo.PushNotificationLaunchUiTest`
- 마지막 cold-start/Profile drawer 및 Friends copy 보강 후 `./gradlew :app:testDebugUnitTest :feature:friends:impl:testDebugUnitTest :app:lintDebug :feature:friends:impl:lintDebug assembleDebug` 재통과.

## 7. 구조 점검 기준
- Todo/Calendar/Widget은 `GetAssignedTodosUseCase.observeVisibleReceived()` 결과를 공유해 pending 제외 정책이 갈라지지 않아야 한다.
- Friends/Profile은 같은 consent action use case를 사용해 권한 mutation path가 갈라지지 않아야 한다.
- `AssignmentMode`는 lifecycle status와 섞지 않는다. 새 status를 추가하지 않고 mode chip/source label로 표현한다.
- Push routing은 이벤트 의미를 기준으로 분기한다. REQUEST received는 decision route, DIRECT received는 Todo surface, DIRECT consent request는 Profile permission surface다.
- 프로필 메뉴는 전역 권한 관리만 담당하고 할 일 전송/할당 정책 판단은 Friends/ViewModel/use case에 둔다.

## 8. 서버 후속 조건
- 서버는 `DIRECT` 권한 판정을 Android보다 우선한다.
- received active feed, sent active feed, friend active feed에 direct accepted item을 포함한다.
- refresh workspace가 pending/active/history received feed를 모두 당겨 Todo/Calendar/Widget이 같은 snapshot에 수렴하게 한다.
- 권한 action은 audit/rate limit 후속 보강 대상이다.
- DIRECT bundle 생성은 `Idempotency-Key` replay와 semantic duplicate를 모두 고려해 중복 생성을 막아야 한다.
- 권한 action repository가 MVP에서는 AssignmentRepository에 있지만, 친구 권한 도메인이 커지면 `FriendPermissionRepository` 등으로 분리할 수 있다.
