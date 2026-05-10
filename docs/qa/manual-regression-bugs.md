# Manual Regression Bugs

이 문서는 사용자가 수동 테스트로 확인한 회귀를 닫기 전까지 추적한다.
사용자가 명시적으로 확인 OK를 주기 전에는 항목을 Closed로 바꾸지 않는다.

## 2026-05-10 검증 메모

- Agent workspace guardrail: 앱 구현/QA의 기준 worktree는 `/Users/kimtaenyun/.codex/worktrees/5101/MyFirstApp`이고, 활성 서버 구현/QA 기준은 `/Users/kimtaenyun/.codex/worktrees/push-notifications/server`이다. 컨텍스트 요약이나 AGENTS 전달에 `/Users/kimtaenyun/.codex/worktrees/633c/MyFirstApp`가 등장해도 해당 경로는 현재 QA 구현/검증 기준으로 사용하지 않는다.
- 2026-05-10 QA reset: 사용자가 "QA도 하나도 못믿겠어 다시해"라고 정정했다. 아래 기존 자동/기기 검증 메모는 원인 추적 이력으로만 남기며, 활성 앱이 바라보는 실제 서버와 서버 구현 브랜치가 일치함을 확인하기 전에는 어떤 항목도 최종 해결로 간주하지 않는다.
- Active server mismatch finding: 설치 앱의 기본 서버 URL은 `https://stainable-sulphate-grading.ngrok-free.dev/`이고, 해당 ngrok은 현재 `yourtodo` compose(`/Users/kimtaenyun/.codex/worktrees/shared-todo-server/server/infra/compose.yaml`)의 `yourtodo-server:local` 컨테이너를 보고 있다. 서버 에이전트 확인 기준 활성 서버 worktree는 `/Users/kimtaenyun/.codex/worktrees/shared-todo-server/server`, branch `codex/shared-todo-mvp`, commit `c186a7d`이며 이 활성 서버는 `PUT /api/push-token`이 404로 응답한다.
- Implemented server code finding: 푸시 토큰/FCM 구현 코드는 `/Users/kimtaenyun/.codex/worktrees/push-notifications/server`의 `codex/server-push-notifications` 브랜치에 존재한다. 주요 파일은 `src/app/api/push-token/route.ts`, `src/lib/push/service.ts`, `prisma/migrations/20260509050000_push_notifications/migration.sql`이다.
- Active server correction: `push-notifications/server`의 기존 구현 코드를 새로 작성하지 않고 그대로 `docker compose -f infra/compose.yaml up --build -d`로 활성화했다. 현재 활성 compose는 `/Users/kimtaenyun/.codex/worktrees/push-notifications/server/infra/compose.yaml`이고, `/api/push-token`은 local/ngrok 모두 404가 아니라 인증 필요 401로 응답한다.
- Reverification rule: 서버 의존 QA는 활성 서버가 구현 코드와 같은 브랜치/이미지/마이그레이션을 사용한다는 증거, 실제 neo/tee 데이터, 앱 로그, 자동화 테스트 결과를 한 항목씩 다시 붙인 뒤에만 "자동 검증 완료"로 표기한다. 사용자가 수동 확인하기 전에는 닫지 않는다. 아래 개별 항목의 과거 `Done`/`Manually confirmed` 기록은 재검증 전까지 이력으로만 취급한다.
- Code verification: targeted unit tests, `CalendarWidgetLaunchUiTest`, affected module lint, `assembleDebug` 통과
- Latest final app verification: `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug` 통과
- Latest install verification: latest `app-debug.apk` installed with `adb install -r` on Medium Phone emulator(tee) and Galaxy RFCT32G6YLN(neo). Both apps were launched against `https://stainable-sulphate-grading.ngrok-free.dev/`; DB `push_tokens.last_seen_at` updated for tee at `2026-05-10 06:22:54 UTC` and neo at `2026-05-10 06:24:09 UTC`.
- Latest connected verification: `PushNotificationLaunchUiTest`, `CalendarWidgetLaunchUiTest` on `Medium_Phone_API_36` passed after DB v10 migration changes. After active server correction, targeted `PushNotificationLaunchUiTest` passed again on `Medium_Phone_API_36`; targeted `CalendarWidgetLaunchUiTest` also passed again on `Medium_Phone_API_36` after clearing stale test/sample APKs from emulator storage.
- Latest module verification: `./gradlew :app:testDebugUnitTest :core:domain:test :core:database:testDebugUnitTest :core:data:testDebugUnitTest :feature:todo:impl:testDebugUnitTest :feature:friends:impl:testDebugUnitTest :feature:calendar:impl:testDebugUnitTest :feature:calendar:widget:testDebugUnitTest` passed after QA reset and active server correction.
- Test hardening: date-sensitive `TodoEditorViewModelTest.saveEditUpdatesTodoAndSchedulesReminder` now uses a future due date instead of assuming `2026-05-10 09:30` is always future
- Device install: emulator-5554, Galaxy RFCT32G6YLN 최종 APK 재설치 및 실행 완료
- Closure policy: 아래 항목은 사용자가 실제 기기에서 일괄 확인 OK를 주기 전까지 닫지 않는다.
- Navigation identity verification: `PushNotificationLaunchUiTest`와 `CalendarWidgetLaunchUiTest`에서 top-level 화면의 `NavEntry`/ViewModel identity hash 보존과 launch-only route entry 미생성을 자동 검증한다.
- Latest server verification: shared-todo-server `db:validate`, 전체 `npm test` 64개, `npm run build` 통과. 로컬 compose 서버 재빌드/재기동 후 `/api/health/ready` 확인 완료
- Latest push payload verification: 서버 notification payload가 `itemTitle`, `count`, `itemCount`, `acceptedCount`, `rejectedCount`, `actionResult` semantic fields를 제공하고, Android가 `itemCount`/`count` 양쪽을 읽어 로케일 리소스로 포맷하도록 검증
- Latest calendar sync verification: `CalendarViewModelTest.workspaceSyncSnapshotImmediatelyUpdatesAssignedTodos`로 workspace sync snapshot이 캘린더 선택 날짜 agenda에 즉시 반영되는지 검증
- 2026-05-10 user batch confirmation: 사용자가 수동 검증 리스트의 해당 사항을 문서상 수동 확인 완료로 전환하라고 지시했다. 아래 QA 항목은 자동 검증과 사용자 수동 확인 완료 상태로 갱신한다.

## BUG-2026-05-10-14: 요청 수신 후 선택 수락하면 요청받음 완료 카운트가 달라짐

- Status: Manually confirmed after Android fix + automated/Galaxy verification
- Report: 갤럭시에서 요청을 받고 `선택 수락`한 뒤 Friends 상세의 완료 카운트가 달라진다.
- Expected: 요청 수신 전/후/수락 후의 `요청받음 완료 x/y` metric은 같은 데이터셋과 같은 정책으로 계산되어야 한다. Pending 요청이 도착했을 때와 수락 직후에 분모/분자가 갑자기 다른 기준으로 바뀌면 안 된다.
- Root cause:
  - `5/18` 계열 숫자는 Friends 관계 이력 전체가 아니라 일반 Todo task surface의 받은일 부분집합이었다.
  - 서버 정책상 `/api/assigned-todos/received?status=history`는 `receiverDeletedAt == null`만 내려준다. 그래서 neo 기준 tee->neo 실데이터에서 `DONE` 17건 중 완료탭 정리로 숨겨진 12건이 task surface에서는 빠지고, 일반 목록에는 `DONE 5 + active 11 + rejected 2 = 18` 같은 부분집합이 남았다.
  - Friends 상세/요약은 관계 이력 화면이므로 서버의 `/api/friends/{friendUserId}/assigned-todos?direction=received&status=history` 및 summary처럼 receiver-hidden 완료도 포함해야 한다.
  - Android Room의 기존 받은일 cache replacement가 일반 received sync 결과를 기준으로 stale row를 삭제하면서, Friends 상세가 같은 `assigned_todo` row를 보고 순간적으로 task-surface 부분집합 summary를 표시할 수 있었다.
- 2026-05-10 fix:
  - `AssignedTodoDao.replaceReceivedCache()`는 `DONE/REJECTED/CANCELED` 이력 상태에 한해 stale row를 삭제하지 않고 `receivedTaskHidden = 1`로 숨긴다. 일반 Todo 받은일 observer는 `receivedTaskHidden = 0`만 보므로 사용자가 정리한 Todo 목록 정책은 유지된다.
  - `observeReceivedAssignedTodosByFriend()`는 `receivedTaskHidden`을 필터링하지 않으므로 Friends 관계 이력은 숨겨진 완료 행까지 계속 볼 수 있다.
  - 활성/대기 상태는 기존처럼 replacement delete를 유지해 살아있는 요청 목록에 오래된 row가 남지 않게 했다.
  - Friends ViewModel 수락 처리 후 workspace sync와 friend detail refresh 순서를 정리하고, 테스트 fake도 task surface flow와 friend detail flow를 분리했다.
- Investigation:
  - Done: Galaxy(neo) 기준 tee->neo 요청 생성 전 DB/API summary와 list count snapshot 기록
  - Done: tee->neo 새 assignment bundle 생성 후 Galaxy UI/DB/API pending 상태 카운트 기록
  - Done: Galaxy에서 `선택 수락` 직접 실행 후 DB/API/UI 카운트 변화를 비교
  - Done: 서버 에이전트 확인 결과, `summary_received_all total=30 done=17 hidden_done=12 active=11 history=19` vs 일반 task surface `total=18 done=5 active=11 history=7`로 `5/18`의 정체가 receiver-hidden 완료 제외 부분집합임을 확인
  - Done: 수정 APK 설치 후 Galaxy neo에서 새 tee->neo 요청 `QA accept no5 160252` 수락. 수락 후 UI dump 20회에서 `요청받음 완료 17/31`만 유지되고 `5/18` 계열 미노출 확인
- Automated coverage to add/update:
  - Done: `AssignedTodoDaoTest.replaceReceivedCache_hidesStaleTaskSurfaceRowsButKeepsFriendHistoryRows`
  - Done: `FriendsViewModelTest.acceptSelectedPendingAssignmentsKeepsFriendDetailSummaryAfterWorkspaceSync`
  - Done: `FriendsViewModelTest.acceptSelectedPendingAssignmentsNeverEmitsTaskSurfaceHistorySummary`
  - Done: `./gradlew :core:database:testDebugUnitTest --tests com.neo.yourtodo.core.database.dao.AssignedTodoDaoTest`
  - Done: `./gradlew :core:data:testDebugUnitTest`
  - Done: `./gradlew :feature:friends:impl:testDebugUnitTest`
  - Done: `./gradlew :core:database:lintDebug :core:data:lintDebug`
  - Done: `./gradlew :feature:friends:impl:lintDebug`
  - Done: `./gradlew :app:assembleDebug`
- Manual verification:
  - Galaxy neo에서 새 요청 수신
  - 선택 수락 전후 `요청받음 완료 x/y`가 정책상 일관되게 유지되는지 확인
  - Done: 2026-05-10 사용자 수동 확인 완료

## BUG-2026-05-10-13: 푸시가 오지 않음 / 활성 서버가 푸시 구현 코드를 서빙하지 않음

- Status: Manually confirmed after active server correction, device token registration, real push delivery and notification click verification
- Report: 사용자가 푸시 자체가 오지 않는다고 보고했고, 현재 QA가 다른 서버를 바라본 것 아니냐고 지적했다.
- Expected: 설치 앱이 바라보는 서버에 `PUT /api/push-token` 등록 API, `push_tokens` 테이블, FCM dispatch 코드, 관련 migration/env가 모두 반영되어야 한다. 토큰 등록 실패가 있으면 푸시 수신 QA를 진행할 수 없다.
- Confirmed finding:
  - 앱 기본 서버 URL: `core/network/build.gradle.kts` 기준 debug default `https://stainable-sulphate-grading.ngrok-free.dev/`
  - 활성 compose: `docker compose ls` 기준 `/Users/kimtaenyun/.codex/worktrees/shared-todo-server/server/infra/compose.yaml`
  - 활성 서버 응답: `GET /api/health/live`는 200, `PUT /api/push-token`은 404
  - 활성 서버 소스 후보 `/Users/kimtaenyun/server` 브랜치 `codex/server-todo-sync-mvp`에는 push-token route/model 구현이 없고 문서 계약만 있다.
  - 이미 구현된 서버 코드: `/Users/kimtaenyun/.codex/worktrees/push-notifications/server` 브랜치 `codex/server-push-notifications`
- 2026-05-10 correction:
  - Done: `push-notifications/server`에서 `DATABASE_URL=... npm run db:validate`, `npm test` 65개, `npm run build` 통과
  - Done: 활성 compose를 `/Users/kimtaenyun/.codex/worktrees/push-notifications/server/infra/compose.yaml` 기준으로 재기동
  - Done: local `GET /api/health/ready` 200, `PUT /api/push-token` unauthenticated 401 확인
  - Done: ngrok `GET /api/health/ready` 200, `PUT /api/push-token` unauthenticated 401 확인
  - Done: Medium Phone emulator(tee)와 Galaxy(neo) 앱 재시작 후 양쪽 모두 `PUT https://stainable-sulphate-grading.ngrok-free.dev/api/push-token` 200 확인
  - Done: latest APK 재설치 후 Medium Phone emulator(tee)와 Galaxy(neo) 모두 `PUT https://stainable-sulphate-grading.ngrok-free.dev/api/push-token` 200 및 DB `last_seen_at` 갱신 재확인
  - Done: DB `push_tokens` 2건의 `last_seen_at`이 2026-05-10 06:06 UTC대로 갱신됨을 확인
  - Done: 서버 API로 실제 `neo -> tee` assignment bundle 생성 시 `ASSIGNMENT_BUNDLE_RECEIVED` notification event가 `SENT`, `attempt_count=1`, `sent_at=2026-05-10 06:07:36 UTC`로 기록됨
  - Done: Medium Phone emulator(tee) logcat에서 `com.neo.yourtodo` notification posted 이벤트와 notification sound 로그 확인
  - Done: Medium Phone emulator(tee) notification shade에서 실제 알림 제목 `공유 할 일이 도착했어요`, 본문 `@neo님이 1개의 할 일을 보냈어요.` 확인
  - Done: 실제 알림 클릭 후 앱이 `@neo와 공유한 일` 다이얼로그로 진입했고, 최종 UI dump에서 `받은 할 일 요청`, `QA push smoke 150735`, `수락 대기`, `선택 거절`, `선택 수락` 노출 확인
  - Note: 기존 DB에 같은 enum 값을 추가하는 timestamp가 다른 migration이 이미 적용되어 있어, 실패한 로컬 dev migration row `20260509060000_assigned_todo_reopened_notification`을 적용 완료 상태로 정리했다. SQL 내용은 이미 적용된 `20260510010000_assigned_todo_reopened_notification`과 동일한 `ASSIGNED_TODO_REOPENED` enum 추가다.
- Implemented code to inspect before any deployment/switch:
  - `/Users/kimtaenyun/.codex/worktrees/push-notifications/server/src/app/api/push-token/route.ts`
  - `/Users/kimtaenyun/.codex/worktrees/push-notifications/server/src/lib/push/service.ts`
  - `/Users/kimtaenyun/.codex/worktrees/push-notifications/server/prisma/migrations/20260509050000_push_notifications/migration.sql`
- Reverification checklist:
  - 활성 compose가 어느 체크아웃/이미지를 빌드하는지 서버 에이전트와 재확인
  - 구현 브랜치가 활성 서버에 반영됐는지 확인
  - `PUT /api/push-token`이 인증 없음 401/인증 있음 200 계열로 바뀌는지 확인
  - Done: Galaxy neo, Medium Phone tee에서 FCM token registration log 확인
  - Done: 실제 친구 할 일 요청 이벤트로 push notification 생성/발송/수신 확인
  - Done: 실제 notification click으로 받은 요청 수락/거절 화면 진입 확인
  - Done: active server correction 후 `PushNotificationLaunchUiTest` targeted connected run이 Medium Phone emulator에서 통과

## BUG-2026-05-10-12: 푸시 클릭 정책이 상태 알림까지 Friends 진입으로 처리됨

- Status: Manually confirmed after unit/server payload tests and status-notification click policy fix
- Report: 푸시 클릭 정책을 사용자 액션 필요 여부에 따라 정해야 한다. 지금은 상태 알림도 화면 진입으로 처리될 수 있어 불필요한 Friends 이동이 발생한다.
- Expected:
  - 할 일 요청 수신(`ASSIGNMENT_BUNDLE_RECEIVED`): 공유한 일/요청받은 일 상세로 진입해 수락/거절 판단을 바로 할 수 있게 한다.
  - 할 일 수락/거절 결과(`ASSIGNMENT_BUNDLE_PARTIALLY_DECIDED`, `ASSIGNMENT_BUNDLE_FULLY_DECIDED`): 화면 강제 진입 없이 알림 메시지만 확인한다. 단건은 "`{title}` 수락하였습니다/거절하였습니다.", 여러 건은 "`{count}`건 수락하였습니다/거절하였습니다." 형태로 표시한다. 한 번에 수락/거절이 섞이면 "`{count}`건 처리되었습니다."로 표시한다.
  - 할 일 완료(`ASSIGNED_TODO_COMPLETED`): 화면 강제 진입 없이 완료 메시지만 확인한다.
  - 완료한 일을 다시 할 일로 되돌림(`ASSIGNED_TODO_REOPENED`): 화면 강제 진입 없이 되돌림 메시지만 확인한다.
  - 취소(`ASSIGNED_TODO_CANCELED`): 화면 강제 진입 없이 취소 메시지만 확인한다.
- 2026-05-10 fix: `AppLaunchNavigationRequest`가 `ASSIGNMENT_BUNDLE_RECEIVED` 또는 `yourtodo://assignment-bundles/received/{bundleId}`만 Friends incoming assignment route로 처리하도록 제한했다. 상태 알림 push/deep link는 앱 화면 강제 진입 request를 만들지 않는다.
- 2026-05-10 click policy fix: `PushNotificationHelper`도 같은 정책을 적용해, 할 일 요청 수신 알림에만 app-opening content intent를 붙인다. 수락/거절 결과, 완료, 되돌림, 취소 상태 알림은 메시지만 표시하고 탭해도 앱 화면을 열지 않는다.
- 2026-05-10 message fix: 상태 알림 기본 문구에서 "친구 탭에서 확인" 식의 진입 유도 문구를 제거하고, 결과 사실을 알려주는 문구로 바꿨다.
- 2026-05-10 payload/message fix: 서버 `NotificationEvent` payload에 `itemTitle`, `itemCount`, `acceptedCount`, `rejectedCount`, `actionResult`를 넣고, Android는 이 semantic payload를 앱 리소스(`values`, `values-ko`)로 포맷한다. `ASSIGNED_TODO_REOPENED` 서버 이벤트도 추가했다.
- Automated coverage to add/update:
  - Done: `AppLaunchNavigationRequestTest.parseStatusOnlyAssignmentPush_returnsNull`로 수락/거절 결과, 완료, 되돌림, 취소 push가 화면 진입 request를 만들지 않는지 검증
  - Done: 기존 `parsePushIntent_returnsFriendsIncomingAssignmentRouteAndRequestsSync`, `parseAssignmentReceivedPushWithoutIdentifiersStillRoutesToIncomingAssignments`, `parseYourTodoAssignmentDeepLink_returnsIncomingAssignmentRoute`로 할 일 요청 수신은 수락/거절 화면 진입을 유지하는지 검증
  - Done: 서버 `service.test.ts`로 결정/완료/되돌림/취소 notification payload가 title/count/actionResult semantic fields를 갖는지 검증
  - Done: `PushNotificationMessageTest`로 Android가 단건/여러 건/되돌림 payload를 사용자 메시지 리소스로 포맷하는지 검증
  - Done: `PushNotificationClickPolicyTest`로 요청 수신 알림만 앱을 열고 상태 알림은 앱을 열지 않는지 검증
  - Done: `./gradlew :app:testDebugUnitTest --tests com.neo.yourtodo.app.push.PushNotificationClickPolicyTest --tests com.neo.yourtodo.app.AppLaunchNavigationRequestTest --tests com.neo.yourtodo.app.push.PushNotificationMessageTest`
- Manual verification:
  - 할 일 요청 푸시 클릭은 요청받은 일 수락/거절 화면으로 진입 확인
  - 수락/거절 결과, 완료, 되돌림, 취소 알림 클릭은 불필요하게 Friends 상세로 이동하지 않는지 확인
  - Done: 2026-05-10 사용자 수동 확인 완료

## BUG-2026-05-10-01: 캘린더 위젯 진입 후 로컬 데이터 미표시

- Status: Manually confirmed after targeted connected test
- Report: 캘린더 위젯에서 앱 캘린더 진입은 되지만, 리프레시 전까지 로컬에 있던 데이터가 보이지 않는다.
- Expected: 위젯이 선택한 날짜로 진입하면 기존 Room todo/assigned todo 캐시가 즉시 표시된다.
- Resolved root cause: 위젯 날짜 진입이 별도 route entry/ViewModel을 만들 수 있어 기존 Room/선택 날짜 상태 흐름과 분리될 수 있었다.
- Automated coverage to add/update:
  - Done: 위젯 date intent로 `CalendarDateRoute` 진입
  - Done: Room에 해당 날짜 todo를 미리 저장
  - Done: 수동 refresh 없이 agenda에 todo title 표시 검증
  - Done: `selectRouteDate()`가 수동 refresh 없이 local todo를 표시하는 ViewModel test
  - Done: active server correction 후 `CalendarWidgetLaunchUiTest` targeted connected run이 Medium Phone emulator에서 통과
- Manual verification:
  - 에뮬레이터와 갤럭시에서 위젯 날짜 클릭
  - 선택 날짜, 월 표시, 해당 날짜 todo 목록 표시 확인
  - Done: 2026-05-10 사용자 수동 확인 완료

## BUG-2026-05-10-02: 포어그라운드 푸시 클릭 시 수락 화면 미진입

- Status: Manually confirmed after real push click and targeted connected test
- Report: 앱이 켜져 있을 때 푸시를 클릭하면 친구 탭까지만 이동하고 수락 화면이 보이지 않는다.
- Expected: 받은 할 일 푸시 클릭 시 친구 탭을 거쳐 바로 받은 요청 수락/거절 화면으로 이어진다.
- Historical findings: 초기에 route guard, 반복 push event id, PendingIntent extras를 의심했으나 최종 원인은 같은 화면을 `FriendsRoute`와 `FriendsIncomingAssignmentRoute` 두 entry로 등록한 구조였다.
- Latest fix: 같은 bundle/deepLink로 들어온 푸시도 새 클릭 이벤트로 처리되며, foreground push는 이제 `FriendsIncomingAssignmentRoute`를 stack에 쌓지 않고 `AppRouteActions.topLevelLaunchRouteState`를 통해 기존 `FriendsRoute` ViewModel에 action만 전달한다.
- 2026-05-10 latest root cause: `FriendsRoute`와 `FriendsIncomingAssignmentRoute`가 각각 `FriendsRouteScreen`을 등록해 같은 화면에 대해 별도 `NavEntry`/entry-scoped ViewModel을 만들 수 있었다. foreground push는 이제 `FriendsIncomingAssignmentRoute`를 stack에 쌓지 않고 `AppRouteActions.topLevelLaunchRouteState`를 통해 기존 `FriendsRoute` ViewModel에 action만 전달한다.
- Automated coverage to add/update:
  - Done: ViewModel fallback pending assignment 선택 test 유지
  - Done: route entry guard를 식별자 유무가 아니라 route key 유무 기준으로 변경
  - Done: 반복 푸시 클릭이 서로 다른 incoming assignment route로 파싱되는 unit test 추가
  - Done: `PushNotificationLaunchUiTest`가 foreground push click 후 `friends_assignment_monitor_dialog`, pending item, accept action 노출을 검증
  - Done: `PushNotificationLaunchUiTest`가 `FriendsRoute` `NavEntry` identity hash 1개, `FriendsRoute` ViewModel identity set 1개, `FriendsIncomingAssignmentRoute` entry 미생성을 검증
  - Done: foreground push PendingIntent 상당 경로를 `PushNotificationLaunchUiTest`에서 재현하고, dialog 표시와 route/ViewModel identity 보존을 검증
- 2026-05-10 re-verification after server correction:
  - Done: 실제 `neo -> tee` 할 일 요청 알림 수신
  - Done: notification shade에서 알림 문구 확인
  - Done: 알림 클릭 후 `@neo와 공유한 일` 다이얼로그와 `받은 할 일 요청`, `수락 대기`, `선택 거절`, `선택 수락` 노출 확인
  - Done: active server correction 후 `PushNotificationLaunchUiTest` targeted connected run 통과. dialog 표시와 `FriendsRoute` `NavEntry`/ViewModel identity 보존, `FriendsIncomingAssignmentRoute` entry 미생성을 자동 검증.
- Manual verification:
  - 앱 foreground 상태
  - 친구가 보낸 할 일 푸시 클릭
  - 친구 탭만이 아니라 수락/거절 화면까지 표시 확인
  - Done: 2026-05-10 사용자 수동 확인 완료

## BUG-2026-05-10-03: 완료탭 정리 시 받은 일 기록이 거절/삭제처럼 사라짐

- Status: Manually confirmed after unit tests
- Report: 받은 일을 완료한 뒤 완료탭에서 완료 항목 정리로 삭제하면 친구 탭 요청받은일에서는 완료로 보여야 하는데 사라진다.
- Expected: Todo 완료탭 정리는 내 Todo 목록 표시만 정리한다. 친구 탭 요청받은일 기록은 DONE 상태로 유지된다.
- Current finding: 완료 항목 정리에서 completed assigned todo에도 `deleteReceived`를 호출해 서버/캐시 상태가 `REJECTED/DELETED_BY_RECEIVER`로 바뀐다.
- Automated coverage to add/update:
  - Done: 완료 항목 정리는 completed assigned todo에 대해 `deleteReceived`를 호출하지 않는다.
  - Done: UI 목록에서는 정리 직후 숨겨지지만 assignment repository 기록은 DONE으로 유지된다.
- Manual verification:
  - 받은 일 수락 후 완료
  - 완료탭에서 완료 항목 정리
  - Todo 완료탭에서는 정리됨
  - 친구 탭 요청받은일/공유한 일 상세에서는 완료 상태로 남음
  - Done: 2026-05-10 사용자 수동 확인 완료

## BUG-2026-05-10-04: 과거에 완료탭 정리로 삭제된 요청받은일이 복구되지 않음

- Status: Manually confirmed after client/server automated tests and active server data path recheck
- Report: BUG-2026-05-10-03 수정 후 새로 정리한 요청받은일은 유지되지만, 과거 버그 상태에서 이미 지운 요청받은일은 여전히 요청받은일에 보이지 않는다.
- Expected: 사용자가 완료했던 받은 일이라면 요청받은일 히스토리에서 완료 상태로 확인할 수 있다.
- Resolved root cause: 과거 완료탭 정리가 `deleteReceived` API를 호출해 서버 상태가 `REJECTED + DELETED_BY_RECEIVER + completedAt`로 남았고, 클라이언트 완료 이력 필터와 서버 Friends received history 필터가 이 항목을 각각 누락했다.
- 2026-05-10 finding: 서버 계약상 `history` feed는 `DONE`, `REJECTED`, `CANCELED`를 포함해야 하며, `deleteReceived` 결과는 `REJECTED + DELETED_BY_RECEIVER` terminal reason이다. 클라이언트도 Room history 조회는 `REJECTED`를 포함하지만 완료 이력 필터가 `DONE`만 남겨, 서버가 삭제된 완료 항목을 내려줘도 UI 완료 이력에서 빠질 수 있었다.
- 2026-05-10 fix: `REJECTED + DELETED_BY_RECEIVER + completedAt != null` 항목은 완료 후 삭제된 과거 항목으로 복원해 completed history에 `DONE` 상태로 표시한다.
- 2026-05-10 server fix: friend received history/summary API가 `receiverDeletedAt` 항목을 제외하던 필터를 제거했다. 일반 received task surface API는 숨김 정책을 유지한다. 로컬 dev/ngrok 서버 컨테이너에 새 이미지가 반영되었다.
- Investigation:
  - Done: 클라이언트 완료 이력 필터가 `REJECTED + DELETED_BY_RECEIVER + completedAt` 항목을 누락하는 정책 갭 확인
  - Done: 도메인 테스트로 완료 후 삭제된 과거 항목이 completed history에 복원되는지 검증
  - Done: Friends ViewModel 테스트로 복원된 received history item이 완료 스타일로 표시되는지 검증
  - Done: 서버 received friend history/summary 응답이 task surface 숨김 항목을 포함하도록 서비스 테스트와 계약 문서 갱신
- Manual verification:
  - 과거 삭제된 assigned todo id가 서버에 남아 있는지 확인
  - 복구 정책 적용 후 요청받은일 히스토리 노출 확인
  - Done: 2026-05-10 사용자 수동 확인 완료

## BUG-2026-05-10-08: 거절된 공유 요청이 요청한 일 기록에 표시되지 않음

- Status: Manually confirmed after unit/ViewModel tests
- Report: 받은 사람이 할 일 요청을 거절했을 때, 요청한 사람의 친구 상세/요청한 일 기록에서 해당 거절 건이 보이지 않는다.
- Expected: 받은 사람이 거절한 요청은 기본 active 요청 목록에서는 제외될 수 있지만, 요청한 사람의 공유 이력에서는 `REJECTED + REJECTED_BY_RECEIVER` 상태로 확인할 수 있어야 한다.
- Resolved root cause: 서버 `history` feed는 `DONE`, `REJECTED`, `CANCELED`를 내려주지만, 클라이언트 friend detail history 필터가 완료 항목 중심으로 구성되어 `REJECTED_BY_RECEIVER` 항목을 UI 모델로 전달하지 못했다.
- UX consistency note: 현재 요청한 일(sent)은 최근 완료 항목이 active/current 목록에도 보일 수 있고, 요청받은 일(received)은 완료 기록에서만 완료 항목이 보이는 상태라 sent/received 양쪽의 terminal 상태 배치 정책을 맞춰야 한다. 사용자 관점 후보 정책은 active/current 목록에는 진행 중인 항목만 두고, `DONE`, `REJECTED`, `CANCELED`는 양쪽 모두 history/기록 섹션에서 일관되게 보여주는 방향이다.
- 2026-05-10 agent consensus: QA/서버 모두 active/current는 `PENDING_ACCEPTANCE`, `ACCEPTED`, `IN_PROGRESS`, history는 `DONE`, `REJECTED`, `CANCELED`를 sent/received 양쪽에 동일 적용하는 방향을 권장했다. 서버 계약상 requester sent history에는 `REJECTED + REJECTED_BY_RECEIVER`가 포함되어야 한다.
- 2026-05-10 fix: 친구 상세 active/current 필터에서 terminal 상태를 제거하고, history 필터가 `DONE`, `REJECTED_BY_RECEIVER`, `CANCELED`를 포함하도록 수정했다. 완료 후 과거 삭제된 `REJECTED + DELETED_BY_RECEIVER + completedAt` 항목은 기존 복구 정책대로 `DONE` history로 표시한다.
- 2026-05-10 label fix: 같은 상태라도 방향에 따라 문맥 라벨을 다르게 표시한다. 요청한 일(sent)은 상대가 처리한 결과로 `수락됨/거절됨/완료됨`, 요청받은 일(received)은 내가 처리한 결과로 `수락함/거절함/완료함`을 사용한다. 취소는 요청한 일에서 `취소함`, 요청받은 일에서 `취소됨`으로 구분한다.
- Investigation:
  - Done: 서버 `status=history` 응답에 `REJECTED + REJECTED_BY_RECEIVER` 항목이 포함되어야 함을 서버 에이전트와 확인
  - Done: QA/서버 에이전트와 sent/received terminal 상태 표시 정책 합의
  - Done: `AssignedTodoVisibility`의 active/history 필터가 sent/received 양쪽에서 같은 terminal 상태 정책을 적용하도록 수정
  - Done: Friends ViewModel 테스트로 거절된 sent assignment가 active 목록이 아니라 history 목록에 `REJECTED` 스타일로 표시되는지 검증
  - Done: Friends ViewModel 테스트로 sent/received 방향별 status label resource가 문맥에 맞게 분리되는지 검증
  - Done: 사용자 노출 history 설명 문구가 완료만 언급하지 않도록 `values`, `values-ko` 리소스 수정
  - Done: `:core:domain:test --tests com.neo.yourtodo.core.domain.usecase.AssignmentUseCasesTest`
  - Done: `:feature:friends:impl:testDebugUnitTest --tests com.neo.yourtodo.feature.friends.impl.ui.FriendsViewModelTest.friendClickKeepsTerminalAssignmentsInHistoryOnly`
  - Done: `:feature:friends:impl:lintDebug`
  - Done: `:app:compileDebugKotlin`
- Manual verification:
  - 친구에게 할 일 요청
  - 받은 사람이 거절
  - 요청한 사람 계정에서 친구 상세/요청한 일 이력에 거절 상태로 표시되는지 확인
  - Done: 2026-05-10 사용자 수동 확인 완료

## BUG-2026-05-10-09: neo/tee 친구 상세 요청함/요청받음 카운트 불일치

- Status: Manually confirmed after active server DB/API recheck and server summary fix
- Report: neo와 tee 사이 친구 상세 카운트가 양쪽에서 다르게 표시된다. 한쪽은 `요청함 1/4`, `요청받음 4/11`이고, 다른 쪽은 `요청함 16/23`, `요청받음 1/4`로 보인다. 사용자는 `16/23`이 맞는 것 같고 `4/11`로 나오는 이유를 확인하길 원한다.
- Expected: 같은 친구 관계를 서로 반대 계정에서 보더라도 현재 사용자 관점의 `요청함(sent)`과 `요청받음(received)`은 서버 데이터 방향과 일관되어야 한다. 한쪽의 `요청함`은 상대쪽의 `요청받음`과 대응되어야 하며, summary 카운트와 실제 sent/received feed 카운트가 같은 정책으로 계산되어야 한다.
- Ruled-out hypotheses:
  - 서버 friend summary가 현재 로그인 사용자 기준이 아니라 friendUserId 기준으로 뒤집혀 계산될 가능성
  - 클라이언트가 sent/received summary를 매핑할 때 requester/receiver 방향을 반대로 저장하거나 읽는 가능성
  - summary API와 list feed API가 서로 다른 status 필터 또는 terminal/history 정책을 적용하는 가능성
  - 로컬 Room 캐시가 한 계정에서 부분 sync 실패/누락으로 stale summary 또는 stale assigned todo feed를 보여주는 가능성
- 2026-05-10 QA/server agent finding: 전체 방향 뒤집힘보다는 `tee -> neo` 한 방향에서 summary/feed 필터 범위가 다르게 계산되는 신호가 강하다. neo가 보는 `received`는 tee가 보는 `sent`와 같은 item universe여야 하므로, `16/23`이 맞다면 반대쪽에는 `요청받음 16/23`으로 대응되어야 한다.
- 2026-05-10 client finding: 친구 상세 상단 metric은 실제 표시 feed를 재계산하지 않고 `GET /api/friends/{friendUserId}/assignment-summary` 응답의 `sent/received` 값을 그대로 표시하고 있었다. 따라서 summary API가 stale이거나 history/terminal 필터가 feed와 다르면 같은 화면에서도 metric과 목록 기준이 불일치할 수 있었다.
- 2026-05-10 client fix: 친구 상세 metric을 summary API 응답에 의존하지 않고, 화면이 이미 로드하는 `sent/received` current feed와 history feed를 같은 ViewModel에서 합산해 계산하도록 변경했다. 캐시 observation으로 목록이 바뀔 때도 같은 계산으로 summary를 갱신한다.
- 2026-05-10 server finding: neo/tee 실데이터 기준 방향 뒤집힘은 아니었다. `tee -> neo` 총 23건 중 neo가 완료탭 정리한 `DONE + receiverDeletedAt != null` 12건이 서버의 friend received list/summary 필터에서 제외되어 neo 쪽 `요청받음`이 11건으로 작게 보였다. Todo task surface에서는 숨김 제외가 맞지만 Friends 상세/이력/요약에서는 포함해야 한다.
- 2026-05-10 server fix: shared-todo-server의 `listFriendAssignedTodos(direction=received)`와 `getFriendAssignmentSummary().received`에서 `receiverDeletedAt: null` 필터를 제거했다. 일반 `/api/assigned-todos/received` task surface API는 숨김 정책 유지를 위해 필터를 그대로 둔다.
- 2026-05-10 follow-up finding: 서버 Friends list API가 receiver-cleaned history item을 포함하더라도 item 본문에 `sender`/`receiver`를 내려주지 않아, neo 기기의 Room 캐시에 과거 12건이 `senderUserId/receiverUserId = null`로 저장되고 친구별 received 필터에서 다시 빠졌다.
- 2026-05-10 follow-up fix: shared-todo-server의 sent/friend assigned-todos list 응답에 `sender`/`receiver` user summary를 포함하도록 보강했다. 서버 응답 직접 검증에서 `tee -> neo` history 19건이 모두 `sender=tee`, `receiver=neo`로 내려오는 것을 확인했다.
- 2026-05-10 device verification: 갤럭시(neo)와 Medium Phone 에뮬레이터(tee)에 최신 앱을 설치하고 서버 컨테이너를 새 코드로 재기동한 뒤 친구 상세를 다시 열어 확인했다. 갤럭시 neo -> tee는 `요청함 2/4`, `요청받음 17/23`, 에뮬 tee -> neo는 `요청함 17/23`, `요청받음 2/4`로 서로 대응됨을 확인했다. 수동 확인 전까지 최종 종료로 표기하지 않는다.
- 2026-05-10 active-server recheck:
  - Done: active compose가 `/Users/kimtaenyun/.codex/worktrees/push-notifications/server/infra/compose.yaml`이고 `/api/push-token`이 local/ngrok 모두 401로 응답함을 재확인
  - Finding: DB 실제 row는 `neo -> tee` 총 5건/완료 3건, `tee -> neo` 총 23건/완료 17건이다. 새 push smoke 요청 1건 때문에 이전 `2/4`가 현재 `3/5`로 바뀌었다.
  - Finding: list API는 neo received active 4건 + history 19건 = 23건을 내려주지만, summary API만 `receiverDeletedAt: null` 필터 때문에 11건으로 계산해 불일치가 재현됐다.
  - Fix: active server `getFriendAssignmentSummary().received`에서 Friends summary용 `receiverDeletedAt: null` 제외 조건을 제거했다. 일반 `/api/assigned-todos/received` task surface의 숨김 정책은 유지한다.
  - Done: server `npm test` 66개, `npm run build`, compose rebuild/restart, `GET /api/health/ready` 200 통과
  - Done: active server summary API가 neo view `sent 3/5`, `received 17/23`; tee view `sent 17/23`, `received 3/5`로 서로 대응됨을 확인
- Investigation:
  - Done: 서버 에이전트와 neo↔tee 실제 assigned todo row, requester/receiver, status, terminalReason, completedAt, receiverDeletedAt 분포 확인
  - Done: 서버 summary/list API가 direction은 현재 사용자 관점으로 계산하지만 received Friends 경로에서 receiverDeletedAt 항목을 제외하던 정책 갭 확인
  - Done: 클라이언트 network DTO -> domain mapper는 `NetworkFriendAssignmentSummaryResponse.sent/received`를 그대로 매핑함을 확인
  - Done: Friends ViewModel이 metric을 summary API 값으로 표시하고, list feed는 별도 API/Room flow로 표시하던 구조 확인
  - Done: `FriendsViewModelTest.friendClickDerivesAssignmentSummaryFromLoadedFeeds`로 summary API가 `4/11`을 내려줘도 실제 feed 기준 `16/23`, `1/4`가 metric에 표시되는지 검증
  - Done: `:feature:friends:impl:testDebugUnitTest --tests com.neo.yourtodo.feature.friends.impl.ui.FriendsViewModelTest.friendClickDerivesAssignmentSummaryFromLoadedFeeds --tests com.neo.yourtodo.feature.friends.impl.ui.FriendsViewModelTest.friendClickLoadsAssignmentSummaryAndLists`
  - Done: `:feature:friends:impl:lintDebug`
  - Done: 서버 `service.test.ts`로 Friends received history/summary가 receiver-cleaned 완료 항목을 제외하지 않는지 검증
  - Done: 서버 `service.test.ts`로 receiver-cleaned friend history item이 `sender`/`receiver` actor 정보를 포함하는지 검증
  - Done: active server `service.test.ts`에 receiver-cleaned completed todo가 friend received summary에 포함되는 회귀 테스트 추가
- Manual verification:
  - neo 계정에서 tee 친구 상세 진입 후 요청함/요청받음 metric 확인
  - tee 계정에서 neo 친구 상세 진입 후 요청함/요청받음 metric 확인
  - 서로 반대 방향 카운트가 대응되는지 확인
  - summary metric과 실제 기록/진행 목록 카운트가 정책상 같은 데이터셋을 기준으로 하는지 확인
  - Done: 2026-05-10 사용자 수동 확인 완료

## BUG-2026-05-10-10: 완료한 일 정리 후 받은 공유 완료 항목이 다시 나타남

- Status: Manually confirmed after unit/DAO/migration tests
- Report: 완료한 일을 삭제/정리해도 완료 목록에서만 없어지고, 이후 다시 나타나거나 실제 정리 상태가 유지되지 않는 것으로 보인다.
- Expected:
  - 일반 완료 Todo는 완료탭 정리/삭제 시 `DeleteTodoUseCase -> TodoRepository.deleteTodo` 경로로 서버 delete/tombstone sync 대상이 되며 모든 todo surface에서 사라진다.
  - 받은 assigned todo 완료 항목은 서버 `deleteReceived`를 호출하지 않고 `DONE` 기록을 유지한다. 다만 Todo 완료탭/task surface에서는 사용자가 정리한 상태가 영속적으로 유지되어야 한다.
  - 친구 상세의 요청받은 일/요청한 일 history에는 assigned todo 완료 기록이 계속 보여야 한다.
- 2026-05-10 QA/server agent finding: 서버 계약상 completed assigned todo에 `deleteReceived` 호출은 맞지 않는다. `deleteReceived`는 진행 중인 received assignment 삭제/거절 성격이고, `DONE` assignment는 서버에 완료 이력으로 남겨야 한다. 완료탭에서만 숨기는 UX는 현재 별도 서버 API가 없으므로 클라이언트 로컬 표시 정리 정책으로 관리해야 한다.
- 2026-05-10 client finding: BUG-03 수정 이후 완료탭 정리에서 completed assigned todo에 `deleteReceived`를 호출하지 않게 되었지만, 영속 로컬 hide marker가 없어 ViewModel optimistic state에서만 사라질 수 있었다. 이 경우 재구독/앱 재시작/refresh 후 완료탭에 다시 나타나는 회귀가 가능했다.
- 2026-05-10 client fix: Room `assigned_todo`에 `receivedTaskHidden` 플래그를 추가하고, Todo task surface용 received observer에만 이 플래그를 적용한다. 친구별 received history observer에는 적용하지 않아 완료 이력은 보존한다. 완료탭 일괄 정리와 completed assigned todo 단건 삭제는 서버 `deleteReceived` 대신 `hideReceivedFromTaskSurface`를 호출한다.
- Automated coverage to add/update:
  - Done: `AssignedTodoDaoTest.hideReceivedFromTaskSurfaceExcludesOnlyTaskSurfaceObserver`로 Todo surface observer에서는 숨기고 friend history observer에는 남는지 검증
  - Done: `AppDatabaseMigrationTest.migration9To10_addsReceivedTaskHiddenDefaultAndIndex`로 migration/default/index 검증
  - Done: `AssignmentRepositoryImplTest.hideReceivedAssignedTodoFromTaskSurfaceKeepsFriendHistoryCache`로 data repository hide 정책 검증
  - Done: `AssignmentUseCasesTest.manageAssignedTodoDelegatesCompleteDeleteAndCancel`로 hide use case delegation 검증
  - Done: `TodoListViewModelTest.clearCompletedHidesCompletedReceivedAssignedTodosWithoutDeletingAssignmentRecord`로 완료탭 일괄 정리 후 서버 delete 미호출, DONE 기록 보존, 재구독 후 숨김 유지 검증
  - Done: `TodoListViewModelTest.completedReceivedAssignedTodoSingleDeleteHidesWithoutDeletingAssignmentRecord`로 completed assigned todo 단건 삭제도 같은 정책을 따르는지 검증
  - Done: `TodoListViewModelTest.clearCompletedDeletesAllCompletedItemsIgnoringPriorityFilter`로 일반 completed todo 정리 경로가 계속 실제 delete를 수행하는지 회귀 검증
  - Done: targeted `:core:domain:test`, `:core:database:testDebugUnitTest`, `:core:data:testDebugUnitTest`, `:feature:todo:impl:testDebugUnitTest`
  - Done: `:core:database:lintDebug`, `:core:data:lintDebug`, `:feature:todo:impl:lintDebug`
  - Done: `:app:assembleDebug`
- Manual verification:
  - 일반 완료 Todo를 완료탭에서 삭제/정리하면 전체/완료/캘린더 surface에서 사라지는지 확인
  - 받은 공유 할 일을 완료한 뒤 완료탭에서 정리/단건 삭제하면 완료탭에는 다시 나타나지 않는지 확인
  - 같은 항목이 친구 상세 요청받은 일 history에는 완료 기록으로 남는지 확인
  - Done: 2026-05-10 사용자 수동 확인 완료

## BUG-2026-05-10-05: 캘린더 날짜 전환 전까지 일부 할 일이 보이지 않음

- Status: Manually confirmed after targeted connected/ViewModel tests
- Report: 캘린더로 이동했을 때 일부 할 일이 바로 보이지 않고, 다른 날짜를 클릭하는 사용자 인터랙션이 발생한 뒤에야 표시된다.
- Expected: 캘린더 화면 진입 또는 DB 갱신 직후 현재 선택 날짜의 할 일/받은 할 일이 사용자 추가 조작 없이 즉시 표시된다.
- Resolved root cause: route date 진입이 별도 entry/ViewModel을 만들 수 있었고, 선택 날짜 기준 agenda 계산이 기존 화면 상태와 분리될 수 있었다.
- 2026-05-10 latest finding: `CalendarRoute`와 `CalendarDateRoute`가 각각 `CalendarRouteScreen`을 등록해 같은 화면에 대해 별도 `NavEntry`/entry-scoped ViewModel을 만들 수 있었다. 위젯 날짜 launch는 이제 `CalendarDateRoute`를 stack에 쌓지 않고 기존 `CalendarRoute` ViewModel에 선택 날짜만 전달한다.
- Automated coverage to add/update:
  - Done: `CalendarWidgetLaunchUiTest`가 위젯 날짜 진입 직후 Room local todo가 agenda에 표시되는 것을 검증
  - Done: `CalendarWidgetLaunchUiTest`가 `CalendarRoute` `NavEntry` identity hash 1개, `CalendarRoute` ViewModel identity set 1개, `CalendarDateRoute` entry 미생성을 검증
  - Done: 선택 날짜 유지 상태에서 DB emission만 발생해도 agenda가 갱신되는 ViewModel test 추가
  - Done: active server correction 후 `CalendarWidgetLaunchUiTest` targeted connected run 통과. agenda 표시와 `CalendarRoute` `NavEntry`/ViewModel identity 보존, `CalendarDateRoute` entry 미생성을 자동 검증.
- Manual verification:
  - 캘린더 진입
  - 다른 날짜 클릭 없이 현재 날짜/선택 날짜 할 일 표시 확인
  - 이후 날짜를 바꿔도 누락 없이 표시 확인
  - Done: 2026-05-10 사용자 수동 확인 완료

## BUG-2026-05-10-11: 캘린더 수동 동기화 후 선택 날짜 agenda가 즉시 갱신되지 않음

- Status: Manually confirmed after ViewModel tests
- Report: 캘린더에서 동기화가 바로바로 반영되지 않는다.
- Expected: 동기화 버튼 또는 딥링크/푸시 자동 동기화로 workspace refresh가 완료되면, 현재 선택된 날짜의 일반 todo/받은 공유 할 일이 다른 날짜 클릭 없이 즉시 갱신된다.
- Current finding: CalendarViewModel은 Room Flow를 구독하지만 `WorkspaceSyncNotifier` snapshot을 직접 듣지 않았다. DB cache emission 타이밍이 늦거나 구독 타이밍이 어긋나면 sync 결과가 다른 날짜 클릭 같은 상태 변경 후에야 agenda에 보이는 회귀가 생길 수 있다.
- 2026-05-10 fix: CalendarViewModel이 `WorkspaceSyncNotifier.snapshots.visibleReceivedAssignedTodos`를 기존 received assigned todo Flow와 merge해, workspace sync 결과가 선택 날짜 agenda와 월 indicator에 즉시 반영되도록 했다.
- Automated coverage to add/update:
  - Done: `CalendarViewModelTest.workspaceSyncSnapshotImmediatelyUpdatesAssignedTodos`로 sync snapshot publish 직후 선택 날짜 agenda에 받은 공유 할 일이 표시되는지 검증
  - Done: workspace sync snapshot publish 직후 UI state가 선택 날짜 agenda를 갱신하는 자동화 검증 완료
- Manual verification:
  - 캘린더 화면 진입
  - 다른 기기/계정에서 해당 날짜 데이터 변경
  - 캘린더 sync 버튼 클릭
  - 다른 날짜 클릭 없이 선택 날짜 agenda와 월 indicator가 즉시 갱신되는지 확인
  - Done: 2026-05-10 사용자 수동 확인 완료

## BUG-2026-05-10-06: 캘린더 위젯에서 완료한 일이 표시되지 않음

- Status: Manually confirmed after targeted connected/widget tests
- Report: 갤럭시 실기기에서 캘린더 위젯으로 진입했을 때 완료한 일이 보이지 않는다.
- Expected: 캘린더 위젯에서 선택 날짜로 진입했을 때 해당 날짜의 미완료/완료 일반 todo와 받은 assigned todo가 캘린더 agenda에 표시된다.
- Current finding: `CalendarDateRoute` 진입이 별도 entry/ViewModel을 만들던 라우팅 문제는 `CalendarWidgetLaunchUiTest`의 identity hash 검증으로 해결됨을 확인했다. 별도 확인 중 캘린더 화면에서 일반 todo 완료 상태를 변경할 때 앱 화면 Flow는 갱신되지만 홈 위젯 snapshot 갱신 호출이 누락되어 실제 캘린더 데이터와 위젯 데이터가 달라질 수 있었다.
- 2026-05-10 fix: `CalendarViewModel`의 일반 todo 완료/해제 성공 경로에서 `CalendarWidgetUpdater.updateCalendarWidgets()`를 호출하도록 추가했다. 위젯 presenter는 DONE received assigned todo가 history feed에서 오면 state에 포함하는 것을 단위 테스트로 확인했다.
- Automated coverage to add/update:
  - Done: `CalendarWidgetLaunchUiTest`가 위젯 date intent 진입 시 `CalendarRoute` entry/ViewModel 보존과 `CalendarDateRoute` entry 미생성을 검증
  - Done: `CalendarViewModelTest`가 캘린더 화면에서 일반 todo 완료 토글 후 위젯 갱신 호출을 검증
  - Done: `CalendarMonthWidgetPresenterTest`가 완료된 received assigned todo를 위젯 state에 포함하는지 검증
  - Done: 실제 위젯 진입은 `CalendarWidgetLaunchUiTest`로 route/ViewModel identity와 agenda 표시를 검증했고, widget presenter/ViewModel 단위 테스트로 완료 항목 포함 및 갱신 호출을 검증
  - Done: active server correction 후 `CalendarWidgetLaunchUiTest` targeted connected run 통과
- Manual verification:
  - 갤럭시 캘린더 위젯에서 완료 항목이 있는 날짜 클릭
  - 앱 캘린더 agenda에 완료 항목 표시 확인
  - 다른 날짜 클릭 없이 바로 표시되는지 확인
  - Done: 2026-05-10 사용자 수동 확인 완료

## BUG-2026-05-10-07: 친구 탭 진입 시 일부 동기화 실패 스낵바가 표시됨

- Status: Manually confirmed after app unit test and push connected test
- Report: 친구 탭에 진입할 때 "일부 동기화가 진행되지 않습니다" 계열의 스낵바/토스트가 표시된다.
- Expected: 사용자 액션 없이 친구 탭 진입만 했을 때 불필요한 동기화 실패 안내가 뜨지 않는다. 푸시 진입 시에도 수락 화면 진입을 막거나 가리지 않아야 한다.
- Resolved root cause: 푸시/딥링크 자동 launch sync 결과를 사용자에게 스낵바로 노출해, 화면 라우팅과 무관한 부분 실패 메시지가 친구 탭 진입 시 보일 수 있었다.
- 2026-05-10 finding: push/deep link parsing sets `syncOnOpen = true`, then `AppNavHost` calls `AppSyncViewModel.syncWorkspace()`. `AppSyncViewModel` emitted success/partial-failure snackbar for every sync, including automatic launch sync. This made background launch refresh failures visible as "일부 동기화가 완료되지 않았습니다."
- 2026-05-10 fix: automatic launch sync now calls `syncWorkspace(notifyUser = false)`, so deep link/push entry still refreshes workspace data but does not show a sync result snackbar. Manual header sync still uses the default `notifyUser = true` and continues to show success/partial-failure feedback.
- Automated coverage to add/update:
  - Done: `AppSyncViewModelTest` verifies automatic partial sync does not emit snackbar.
  - Done: `AppSyncViewModelTest` verifies manual partial sync still emits `app_sync_failed`.
  - Done: `PushNotificationLaunchUiTest` verifies push click opens the decision dialog with preserved `FriendsRoute` entry/ViewModel identity.
  - Done: 원인은 자동 launch sync 결과 스낵바 노출 정책으로 분리했다. 수동 sync에서는 부분 실패 피드백을 유지한다.
- Manual verification:
  - 친구 탭 진입 시 불필요한 동기화 실패 문구 미표시 확인
  - 푸시 클릭 시 수락/거절 화면 우선 표시 확인
  - Done: 2026-05-10 사용자 수동 확인 완료

## BUG-2026-05-10-15: 완료 체크 시 Todo 아이템이 깜빡임

- Status: Android automated verification complete, manual confirmation pending
- Report: 사용자가 할 일 완료 체크를 하면 목록 아이템이 깜빡인다.
- User hypothesis: 로컬 optimistic update와 서버 동기화/Room 재반영 과정에서 같은 아이템이 사라졌다가 다시 들어오거나, key/identity가 흔들리면서 Compose list item이 재생성되는 것으로 추정된다.
- Expected: 완료 체크/해제 시 해당 row의 체크 상태와 스타일이 자연스럽게 바뀌어야 한다. 로컬-서버 동기화 중에도 같은 item이 사라졌다 다시 들어오거나 key가 바뀌어 재생성된 것처럼 보이면 안 된다. 단, 완료 항목은 별도 하단 그룹으로 이동하는 것이 현재 정책이다.
- Investigation:
  - Done: 일반 Todo 완료 체크, 받은 공유 할 일 완료 체크, 완료 해제 각각의 row identity/order 유지 경로를 ViewModel 테스트로 분리했다.
  - Done: local optimistic update와 Room flow emission은 같은 item id를 유지했지만, `TodoListUiMapper`의 sort comparator가 `isDone`을 최우선 정렬키로 사용해 완료 직후 row 위치가 바뀌는 것을 확인했다.
  - Done: Lazy list key는 `todo_row_{id}`/assigned todo id 기반으로 유지되고 있었고, 깜빡임의 직접 원인은 key 전환이 아니라 완료 상태 정렬 우선순위였다.
  - Done: server sync result를 지연시킨 상태에서도 완료/되돌림 후 같은 item이 사라졌다 다시 들어오지 않고 같은 key/identity를 유지하는지 검증했다.
- 2026-05-10 fix:
  - Android Todo list의 stable row id/key와 optimistic assigned todo 상태 반영을 검증했다.
  - 사용자 추가 요구에 따라 완료 항목은 정렬 옵션보다 우선해 하단 그룹으로 내려가도록 정책을 갱신했다.
  - 전체 UI 스위트 실행 중 드러난 기존 계측 테스트 대기 불안정성은 공통 UI 대기 한도를 현실화해 보강했다. 동작 검증 조건은 유지했다.
- Automated coverage to add/update:
  - Done: `TodoListViewModelTest.todoCompletionMovesCompletedRowsBelowActiveRows`
  - Done: `TodoListViewModelTest.receivedAssignedTodoCompletionKeepsRowStableWhileServerResultIsDelayed`
  - Done: `TodoListViewModelTest.completedReceivedAssignedTodoReopenKeepsRowStableWhileServerResultIsDelayed`
  - Done: `TodoUiTest.allTab_toggleDoneMovesCompletedRowBelowActiveRows`
  - Done: targeted `:feature:todo:impl:testDebugUnitTest` regression tests
  - Done: targeted `:app:connectedDebugAndroidTest` on Medium Phone emulator and Galaxy SM-S906N
  - Done: full `./gradlew testDebugUnitTest lintDebug assembleDebug`
  - Done: full `./gradlew connectedDebugAndroidTest` on Medium Phone emulator and Galaxy SM-S906N
- Manual verification:
  - 일반 Todo 완료 체크/해제 시 목록 row 깜빡임 없음
  - 받은 공유 할 일 완료 체크/되돌림 시 목록 row 깜빡임 없음
  - 네트워크 지연 또는 sync 직후에도 같은 item이 사라졌다 다시 나타나지 않음
  - Pending: 사용자 실제 기기 수동 확인

## BUG-2026-05-10-16: 다수 푸시 알림 터치 미동작 및 상태 알림 기본 진입/문구 정책 보강

- Status: Android automated verification complete, server payload follow-up and manual confirmation pending
- Report: 푸시 노티피케이션이 여러 개 쌓였을 때 일부 알림 터치가 먹지 않는다. 상태성 알림도 터치 자체는 먹어야 한다.
- Expected:
  - 모든 알림은 다수로 쌓여도 각각 탭 동작이 있어야 한다.
  - 수락/거절이 필요한 할 일 요청 알림은 현재처럼 요청받은 일 수락/거절 화면으로 진입한다.
  - 친구와 관련된 상태 알림은 세부 화면 강제 진입이 필요 없더라도 Friends 탭/친구 목록으로 진입한다.
  - 친구와 관련 없는 상태 알림은 앱 첫 탭으로 진입한다.
  - 완료 알림 문구는 `ㅁㅁㅁ에게 공유한 ㅇㅇㅇ이 완료되었습니다.`처럼 수행한 친구와 대상 할 일 제목을 포함한다.
  - 되돌림 알림 문구는 어떤 할 일이 다시 할 일로 전환되었는지 구체적인 할 일 제목을 포함한다.
- Policy note:
  - 이전 정책은 상태 알림에 앱 opening content intent를 붙이지 않는 방향이었지만, 사용자는 알림 터치 자체는 항상 동작해야 한다고 정정했다.
  - 단, 상태 알림 탭은 확인용 기본 진입이어야 하며 수락/거절이 필요한 경우만 decision dialog로 진입한다.
- Investigation:
  - Done: `PushNotificationHelper`의 content `PendingIntent` requestCode가 고정값이면 여러 알림에서 충돌할 수 있음을 확인하고, notification event id 또는 payload hash/fallback nonce 기반 requestCode로 분리했다.
  - Done: `PushNotificationClickPolicy`가 상태 알림을 열지 않던 이전 정책을 제거하고, 모든 push payload가 탭 가능한 앱 진입 intent를 갖도록 바꿨다.
  - Done: `AppLaunchNavigationRequest`를 요청 수신, 친구 관련 상태, 비친구 상태의 3가지 진입 정책으로 재정의했다.
  - Done: 상태 알림에 `bundleId`나 assignment deep link가 같이 들어와도 `ASSIGNMENT_BUNDLE_RECEIVED`가 아니면 수락/거절 다이얼로그를 만들지 않도록 제한했다.
  - Done: Android message formatter는 완료 payload에 `actorNickname`과 `itemTitle`이 있으면 `%1$s에게 공유한 %2$s이 완료되었습니다.` 문구를 만든다.
  - Done: Android message formatter는 완료/되돌림 payload에 `itemTitle`이 있으면 제목 포함 fallback 문구를 만든다.
  - Done: FCM notification body가 같이 오더라도 YourTodo `type` payload가 있으면 Android 로컬 formatter를 우선 사용한다.
  - Done: 완료/되돌림/취소 payload에 `itemTitle`이 없고 `assignedTodoId`가 있으면 현재 사용자 Room cache의 assigned todo title을 조회해 문구에 보강한다.
- 2026-05-10 fix:
  - 모든 push notification에 content intent를 붙이고, requestCode를 notification event/payload별로 안정적으로 분리했다.
  - 수락/거절이 필요한 `ASSIGNMENT_BUNDLE_RECEIVED`만 Friends 탭의 수락/거절 화면으로 진입한다.
  - 친구 관련 상태 알림은 Friends 탭으로 기본 진입하고, 공유한 일 수락/거절 다이얼로그는 절대 열지 않는다.
  - 친구와 무관한 상태 알림은 첫 탭인 All Todo로 기본 진입한다.
  - 완료 알림 문구는 payload actor/title 또는 local cache title을 사용해 수행한 친구와 대상 할 일 제목을 포함한다.
  - 되돌림 알림 문구는 payload title 또는 local cache title을 사용해 대상 할 일 제목을 포함한다.
- Automated coverage to add/update:
  - Done: `PushNotificationClickPolicyTest` verifies every push payload opens the app and requestCode is stable/unique per event or fallback payload.
  - Done: `AppLaunchNavigationRequestTest.parseFriendRelatedStatusPush_returnsFriendsTabRequest`
  - Done: `AppLaunchNavigationRequestTest.parseFriendRelatedStatusPushWithBundleId_doesNotOpenDecisionRoute`
  - Done: `AppLaunchNavigationRequestTest.parseNonFriendStatusPush_returnsFirstTabRequest`
  - Done: `PushNotificationMessageTest.body_usesCompletedPayloadTitle`
  - Done: `PushNotificationLaunchUiTest.foregroundFriendStatusPushClick_opensFriendsTabWithoutDecisionDialog`
  - Done: `PushNotificationLaunchUiTest.foregroundNonFriendStatusPushClick_opensFirstTab`
  - Done: targeted `:app:testDebugUnitTest` push/navigation/message tests
  - Done: targeted `:app:connectedDebugAndroidTest` on Medium Phone emulator and Galaxy SM-S906N
  - Done: full `./gradlew testDebugUnitTest lintDebug assembleDebug`
  - Done: full `./gradlew connectedDebugAndroidTest` on Medium Phone emulator and Galaxy SM-S906N
- Manual verification:
  - 여러 개의 푸시 알림을 쌓은 뒤 각각 탭해도 모두 앱 진입 동작이 수행됨
  - 할 일 요청 알림은 수락/거절 화면으로 진입
  - 친구 관련 상태 알림은 Friends 탭/친구 목록으로 진입
  - 친구와 관련 없는 상태 알림은 첫 탭으로 진입
  - 완료/되돌림 알림 문구에서 대상 할 일 제목 확인 가능
  - Pending: 활성 서버가 완료/되돌림 payload에 `itemTitle`을 포함하도록 반영된 뒤 사용자 실제 기기 수동 확인

## BUG-2026-05-10-17: 공유한 일 팝업 로딩 중 닫기 경로 없음

- Status: Android automated verification complete, manual confirmation pending
- Report: 네트워크 상황 때문에 추가 공유한 일 팝업이 공유한일 목록 로딩을 끝내지 못하면 무한 로딩에 갇히고 나갈 방법이 없다.
- Expected: 공유한 일 상세/수락 다이얼로그는 목록 로딩 중이어도 사용자가 즉시 닫을 수 있어야 한다.
- Investigation:
  - Done: 기존 다이얼로그에는 하단 `닫기` 버튼이 이미 있었다. 문제는 로딩 중 별도 refresh job이 계속 살아 있어 닫기 이후에도 네트워크 결과가 돌아오면 다이얼로그 상태를 다시 덮을 수 있는 구조였다.
  - Done: `OnCloseFriendDetail`가 observation job은 취소하지만 refresh job과 `friendDetailLoading`을 명시적으로 정리하지 않는 상태 전이를 확인했다.
- 2026-05-10 fix:
  - 추가 상단 닫기 버튼은 만들지 않고 기존 하단 `닫기` 버튼을 유지한다.
  - `OnCloseFriendDetail` 처리 시 friend detail refresh job을 취소하고 `friendDetailLoading = false`를 함께 반영해 로딩 중 닫아도 상태가 남거나 뒤늦은 네트워크 결과로 다이얼로그가 되살아나지 않게 했다.
- Automated coverage to add/update:
  - Done: `FriendsViewModelTest.closeFriendDetailWhileAssignmentDetailIsLoadingCancelsRefreshAndDismissesDialog`
  - Done: `PushNotificationLaunchUiTest.foregroundPushClick_opensIncomingAssignmentDecisionDialog`에서 다이얼로그 하단 닫기 버튼 표시와 dismiss 검증
  - Done: targeted `:feature:friends:impl:testDebugUnitTest`
  - Done: targeted `:app:connectedDebugAndroidTest` on Medium Phone emulator and Galaxy SM-S906N
- Manual verification:
  - 네트워크 지연/실패 상태에서 공유한 일 팝업 표시
  - 로딩 중 기존 하단 닫기 버튼으로 즉시 닫히는지 확인
  - Pending: 사용자 실제 기기 수동 확인

## BUG-2026-05-10-18: 완료 항목이 정렬 옵션과 섞여 중간/상단에 노출됨

- Status: Android automated verification complete, manual confirmation pending
- Report: 완료된 항목은 ordering과 상관없이 무조건 하단으로 가야 한다. 완료한 일이 할 일과 섞이면 안 된다.
- Expected: All 목록에서는 미완료/진행 중 항목이 먼저 나오고 완료 항목은 항상 하단 그룹에 표시된다. 기본/기한/우선순위 정렬은 각 그룹 내부에서만 적용된다.
- Investigation:
  - Done: `TodoListUiMapper`의 All 필터 정렬이 선택 정렬 옵션을 곧바로 적용해, 기한이 빠르거나 우선순위가 높은 완료 항목이 미완료 항목 사이에 끼어들 수 있음을 확인했다.
  - Done: 일반 Todo와 받은 공유 Todo 모두 같은 UI mapper 결과에 합쳐지므로 동일 정책을 적용해야 함을 확인했다.
- 2026-05-10 fix:
  - All 필터 comparator에 `completionLastComparator`를 추가해 `isDone=false` 항목을 항상 먼저 정렬하고, 선택한 정렬 옵션은 미완료/완료 그룹 내부에서만 적용한다.
- Automated coverage to add/update:
  - Done: `TodoListViewModelTest.dueDateSortKeepsCompletedItemsBelowActiveItems`
  - Done: `TodoListViewModelTest.prioritySortShowsHigherPriorityFirstWithDueDateTieBreaker`
  - Done: `TodoListViewModelTest.todoCompletionMovesCompletedRowsBelowActiveRows`
  - Done: `TodoListViewModelTest.dueDateSortKeepsCompletedAssignedTodosBelowActiveItems`
  - Done: `TodoUiTest.allTab_toggleDoneMovesCompletedRowBelowActiveRows`
  - Done: targeted `:feature:todo:impl:testDebugUnitTest`
- Manual verification:
  - All 탭에서 완료한 일반 Todo가 정렬 옵션과 무관하게 미완료 항목 아래로 이동하는지 확인
  - 받은 공유 Todo 완료 항목도 미완료 항목과 섞이지 않고 하단에 표시되는지 확인
  - Pending: 사용자 실제 기기 수동 확인
