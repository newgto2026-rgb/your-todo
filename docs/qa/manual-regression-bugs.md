# Manual Regression Bugs

이 문서는 사용자가 수동 테스트로 확인한 회귀를 닫기 전까지 추적한다.
사용자가 명시적으로 확인 OK를 주기 전에는 항목을 Closed로 바꾸지 않는다.

## 2026-05-10 검증 메모

- Code verification: targeted unit tests, `CalendarWidgetLaunchUiTest`, affected module lint, `assembleDebug` 통과
- Device install: emulator-5554, Galaxy RFCT32G6YLN 설치 및 실행 완료
- Closure policy: 아래 항목은 사용자가 실제 기기에서 확인 OK를 주기 전까지 닫지 않는다.

## BUG-2026-05-10-01: 캘린더 위젯 진입 후 로컬 데이터 미표시

- Status: Fixed in code, awaiting manual confirmation
- Report: 캘린더 위젯에서 앱 캘린더 진입은 되지만, 리프레시 전까지 로컬에 있던 데이터가 보이지 않는다.
- Expected: 위젯이 선택한 날짜로 진입하면 기존 Room todo/assigned todo 캐시가 즉시 표시된다.
- Current hypothesis: route date 진입 시 초기 placeholder state, NavEntry/ViewModel owner, 또는 월/선택일 flow 결합 타이밍이 로컬 DB emission을 가리고 있을 수 있다.
- Automated coverage to add/update:
  - Done: 위젯 date intent로 `CalendarDateRoute` 진입
  - Done: Room에 해당 날짜 todo를 미리 저장
  - Done: 수동 refresh 없이 agenda에 todo title 표시 검증
  - Done: `selectRouteDate()`가 수동 refresh 없이 local todo를 표시하는 ViewModel test
- Manual verification:
  - 에뮬레이터와 갤럭시에서 위젯 날짜 클릭
  - 선택 날짜, 월 표시, 해당 날짜 todo 목록 표시 확인

## BUG-2026-05-10-02: 포어그라운드 푸시 클릭 시 수락 화면 미진입

- Status: Fixed in code, awaiting manual confirmation
- Report: 앱이 켜져 있을 때 푸시를 클릭하면 친구 탭까지만 이동하고 수락 화면이 보이지 않는다.
- Expected: 받은 할 일 푸시 클릭 시 친구 탭을 거쳐 바로 받은 요청 수락/거절 화면으로 이어진다.
- Current finding: 식별자 없는 `FriendsIncomingAssignmentRoute`를 ViewModel이 처리하도록 보강했지만, `FriendsRouteScreen` 입구에서 bundle/user가 없으면 action을 전달하지 않는 guard가 남아 있었다.
- Automated coverage to add/update:
  - Done: ViewModel fallback pending assignment 선택 test 유지
  - Done: route entry guard를 식별자 유무가 아니라 route key 유무 기준으로 변경
  - Todo: 실제 foreground push PendingIntent 수동 확인
- Manual verification:
  - 앱 foreground 상태
  - 친구가 보낸 할 일 푸시 클릭
  - 친구 탭만이 아니라 수락/거절 화면까지 표시 확인

## BUG-2026-05-10-03: 완료탭 정리 시 받은 일 기록이 거절/삭제처럼 사라짐

- Status: Fixed in code, awaiting manual confirmation
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
