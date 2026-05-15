# PRD - Direct Assignment MVP

## 1. 문서 정보
- 문서명: 제품 요구사항 문서 - Direct Assignment MVP
- 대상 프로젝트: `YourTodo` Android 앱 + `yourtodo-server`
- Android 기준 브랜치: `codex/force-assignment`
- 작성일: 2026-05-15
- 상태: 구현/검증 중 - Profile 권한 UX 제거, Friends 목록 중심 자동수락 UX로 재설계

## 2. 제품 한 줄 정의
할당받을 사람이 친구별로 `자동수락`을 켜면, 그 친구가 보낸 할 일이 별도 수락 없이 내 Todo에 바로 추가되는 자동 할당 기능.

## 3. 사용자 문제
기존 shared todo는 받은 사람이 항목별로 수락해야 안전하다. 그러나 가족, 팀원, 반복적인 협업 관계처럼 이미 신뢰가 형성된 관계에서는 매번 수락하는 절차가 번거롭다.

반대로 친구 관계라는 이유만으로 누구나 내 할 일에 항목을 추가할 수 있으면 개인 Todo 공간의 통제감이 무너진다. 따라서 자동 할당은 친구 관계와 별개의 명시적 권한이어야 하며, 언제든 끌 수 있어야 한다.

## 4. 용어 결정
- 사용자 노출명은 `강제 할당`이 아니라 `자동 할당`으로 한다.
- 서버/코드 용어는 `DIRECT` assignment mode를 사용한다.
- `REQUEST`: 기존처럼 받은 사람이 수락해야 Todo에 들어가는 요청 방식.
- `DIRECT`: 할당받을 사람이 친구별 자동수락을 켜 두어 수락 단계를 건너뛰고 Todo에 바로 들어가는 자동 할당 방식.
- `DIRECT + ACCEPTED`는 화면에서 `수락됨`이 아니라 `할당됨` 또는 `자동 할당`으로 표현한다.

## 5. 목표
- Friends 목록에서 친구별 자동수락 상태를 확인할 수 있다.
- 할당받을 사람은 친구별로 `자동수락 허용`을 켜거나 끌 수 있다.
- 자동수락 권한 요청/허용/거절 대기 UX는 제거한다.
- 권한이 있는 방향에서만 할 일을 자동 할당할 수 있다.
- 자동 할당된 할 일은 Todo, Calendar, Widget, Friends 상세에서 일관되게 보인다.
- 자동 할당된 항목은 받은 요청 수락/거절 블록에 섞이지 않는다.
- 앱 재시작과 캐시 재진입 후에도 `REQUEST`와 `DIRECT` 구분이 유지된다.

## 6. 비목표
- abuse rate limit, 감사 로그 상세 화면, 관리자 도구.
- 푸시 문구 세분화와 알림 채널 확장.
- 서버 권한 정책의 Android 단독 대체. 서버가 최종 판정한다.
- 자동수락 권한 만료 정책의 상세 UX. `EXPIRED` 표시는 가능하지만 만료 계산은 서버 정책이다.

## 7. 권한 방향
자동 할당 권한은 방향성이 있다.

- `grantedToMe = ACTIVE`: 내가 이 친구에게 할 일을 자동 할당할 수 있다.
- `grantedByMe = ACTIVE`: 이 친구가 내게 할 일을 자동 할당할 수 있다.
- 반대 방향 권한이 있다고 해서 내가 친구에게 자동 할당할 수 있는 것은 아니다.

상태:
```text
NONE
PENDING
ACTIVE
REVOKED
EXPIRED
```

Android 신규 UX에서는 `PENDING`을 만들지 않는다. 서버 하위 호환 response에 `PENDING`이 내려오더라도 Friends UI는 요청/거절 대기 흐름을 노출하지 않고 `꺼짐` 계열로 수렴한다.

## 8. 핵심 사용자 플로우
### 8.1 Friends 목록 자동수락 설정
1. 사용자가 Friends 탭을 연다.
2. 친구 목록의 각 친구 행에서 `할 일 추가`와 `자동수락` 상태를 함께 본다.
3. `자동수락`을 켜면 그 친구가 내게 보낸 DIRECT 할 일은 수락 없이 내 Todo에 바로 추가된다.
4. `자동수락`을 끄면 이후 그 친구가 보내는 할 일은 기존 요청 방식으로만 처리된다.
5. 성공/실패는 snackbar로 피드백하고, 목록/상세는 같은 서버 상태로 갱신한다.

### 8.2 Friends 상세 모니터링
1. 사용자가 친구 행을 눌러 상세를 연다.
2. 상세는 보낸/받은 할 일, pending decision, history를 모니터링하는 표면이다.
3. 자동수락 설정은 팝업/상세 action이 아니라 친구 목록 행의 스위치에서 처리한다.
4. 권한 요청/거절/대기 UI는 노출하지 않는다.

### 8.3 자동 할당 전송
1. 사용자가 `할 일 보내기` sheet를 연다.
2. 친구가 나에게 자동수락을 허용한 상태면 전송 방식은 자동으로 `DIRECT`가 된다.
3. 친구가 자동수락을 허용하지 않았으면 기존처럼 `REQUEST`로 전송된다.
4. 사용자는 전송 방식 선택 UI를 보지 않고 `할 일 보내기`만 실행한다.
5. `DIRECT`이면 서버에 `assignmentMode=DIRECT`로 bundle을 생성한다.
6. 서버 성공 응답은 item status를 `ACCEPTED`로 내려준다.
7. 성공한 항목은 상대방 Todo/Calendar/Widget에 즉시 표시된다.

### 8.4 받은 자동 할당 확인
1. 받은 사람 Todo row에는 `자동 할당 · @nickname`이 표시된다.
2. Calendar agenda에도 동일하게 `자동 할당 · @nickname`이 표시된다.
3. 편집 sheet 제목은 `할당받은 할 일`이다.
4. 제목, 본문, 마감일은 읽기 전용이고 본인 리마인더만 조정할 수 있다.

## 9. 표면별 표시 정책
| 표면 | REQUEST | DIRECT |
|---|---|---|
| Profile | 자동수락 권한 UX 없음 | 자동수락 권한 UX 없음 |
| Todo row | `요청 수락 · @nickname` | `자동 할당 · @nickname` |
| Calendar agenda | `요청 수락 · @nickname` | `자동 할당 · @nickname` |
| Calendar month grid | count/indicator에 포함 | count/indicator에 포함 |
| Calendar widget | due date가 있으면 chip/count 포함 | due date가 있으면 chip/count 포함 |
| 받은 요청 블록 | `PENDING_ACCEPTANCE`만 표시 | 표시하지 않음 |
| Friends 목록 | 친구별 `자동수락` 끄기 | 친구별 `자동수락` 켜짐 |
| Friends 상세 | 할당 모니터링만 제공 | 할당 모니터링만 제공 |
| Friends 전송 sheet | `할 일 보내기` 후 요청으로 전송 | `할 일 보내기` 후 바로 추가 |
| Friends 상세 카드 | mode chip `요청` | mode chip `자동 할당`, status `할당됨` |
| 받은 일 편집 제목 | `요청받은 할 일` | `할당받은 할 일` |

## 10. 수용 기준
- [x] `assignmentMode`가 Android model, network DTO, data mapper, Room cache에 보존된다.
- [x] Profile 메뉴에서 자동수락 권한 섹션과 권한 mutation을 제거한다.
- [x] Friends 목록에서 친구별 자동수락을 켜고 끌 수 있다.
- [x] Friends 상세에서 자동수락 action UI를 제거하고 할당 모니터링만 제공한다.
- [x] 자동수락 mutation은 `set opt-in enabled/disabled`만 사용하고 요청/허용/거절 대기 UX를 노출하지 않는다.
- [x] 기존 REQUEST 전송은 회귀 없이 `PENDING_ACCEPTANCE`로 동작한다.
- [x] DIRECT 전송은 권한이 있는 방향에서만 가능하고 create request에 `DIRECT`를 보낸다.
- [x] DIRECT 서버 실패 시 editor를 닫거나 성공 snackbar를 보여주지 않는다.
- [x] DIRECT 받은 일은 Todo list에 즉시 노출되고 row에 `자동 할당 · @nickname`을 표시한다.
- [x] DIRECT 받은 일은 Calendar selected-date agenda와 month indicator에 반영된다.
- [x] DIRECT due date 항목은 Calendar widget count/chip에 반영된다.
- [x] REQUEST pending은 Todo list, Calendar, Widget에 노출되지 않는다.
- [x] DIRECT 항목은 받은 요청 수락/거절 블록에 노출되지 않는다.
- [x] Friends 상세에서 요청/허용/거절 action UI가 제거된다.
- [x] 자동수락 허용/취소와 DIRECT 할 일 도착 노티 타입이 정의된다.
- [x] DIRECT 할 일 도착 노티는 받은 요청 수락 화면으로 이동하지 않는다.
- [x] 사용자 노출 문구는 `values`, `values-ko` 리소스에 있다.

## 10.1 Notification 수용 기준
- 자동수락 허용 결과: `DIRECT_ASSIGNMENT_CONSENT_ACCEPTED`, 클릭 시 Friends로 진입해 최신 상태를 동기화한다.
- 자동수락 취소 결과: `DIRECT_ASSIGNMENT_CONSENT_REVOKED`, 클릭 시 Friends 권한 상태를 다시 확인한다.
- DIRECT 할 일 도착: `DIRECT_ASSIGNMENT_RECEIVED`, 클릭 시 Todo surface로 진입하고 수락/거절 dialog를 열지 않는다.
- 노티는 정합성 원천이 아니며 앱 진입 시 서버 동기화가 항상 최신 상태를 확인한다.

## 10.2 최종 에이전트 리뷰 반영 사항
- Planner: 사용자가 불편하다고 지적한 Profile/popup 권한 관리는 제거하고 Friends 맥락에서 친구별 자동수락을 관리한다.
- Server/Data: Android repository contract는 `assignmentMode`를 구현체가 반드시 처리하게 만들고, consent mutation은 `setDirectAssignmentOptIn(friendUserId, enabled)`로 단순화한다.
- Design: 친구 목록에는 `할 일 추가`와 `자동수락` 토글을 함께 두고, 친구 상세는 할당 모니터링에 집중한다.
- QA: Profile에 자동수락 섹션이 남지 않는지, Friends 목록 토글 방향이 뒤집히지 않는지, DIRECT 받은 할 일이 자동수락되어 Todo/Calendar/Widget에 보이는지 회귀 테스트한다.

## 11. 리스크
- 서버가 DIRECT 성공을 `ACCEPTED`로 내려주지 않으면 Android에서 pending 요청처럼 보일 수 있다.
- 권한 방향을 반대로 해석하면 상대방이 허용하지 않은 자동 할당이 열릴 수 있다.
- cache에 `assignmentMode`가 없으면 앱 재시작 후 자동 할당 라벨이 사라진다.
- Calendar/Widget 갱신이 Todo와 분리되면 사용자는 표면마다 다른 현실을 보게 된다.
- 서버가 `Idempotency-Key` 또는 semantic duplicate를 보장하지 않으면 네트워크 타임아웃 후 사용자가 재시도할 때 DIRECT 할 일이 중복 생성될 수 있다.
