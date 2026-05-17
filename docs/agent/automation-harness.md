# 자동화 하네스

## 목적

이 문서는 YourTodo에서 자동화할 수 있는 품질 규칙과 사람이 직접 판단해야 하는 영역을 구분한다.

YourTodo는 작은 Todo 앱을 최소 구현하는 프로젝트가 아니라, AI-assisted development에서 모듈 경계, 테스트 가능성, 변경 관찰성, 자동 검증이 어떻게 작동하는지 실험하는 Android 하네스다. 따라서 자동화의 목적은 개발자의 사고를 대체하는 것이 아니라, AI와 사람이 반복해서 놓치기 쉬운 구조 위반과 누락을 빠르게 드러내는 것이다.

자동화 기준은 단순하다.

- 기계적으로 판정 가능한 규칙은 script, hook, CI로 내린다.
- 의미, 의도, 우선순위, tradeoff 판단은 PR 설명과 리뷰에서 사람이 맡는다.
- 자동화는 가능한 한 빠르게 실패하고, 실패 이유를 사람이 바로 이해할 수 있어야 한다.
- 자동화가 현재의 정당한 테스트 구조를 막는다면 실패 범위를 줄이고 문서화한다.

## 자동화 계층

```text
Codex edit hook
  -> TDD Guard
  -> local pre-commit
  -> local pre-push
  -> GitHub Actions CI
  -> human PR review
```

이 흐름은 같은 규칙을 여러 위치에서 반복 실행하기 위한 것이다. 로컬 hook은 빠른 피드백을 제공하고, CI는 원격 기준에서 재현성을 보장하며, PR 리뷰는 자동화가 판단하지 못하는 설계 의도를 확인한다.

## 현재 자동화된 항목

| 자동화 | 왜 자동화하는가 | 자동화 결과 | 기술 방식 | 연결 위치 |
|---|---|---|---|---|
| 모듈 가이드 정합성 | AI가 변경 대상 모듈의 `AGENTS.md`를 찾지 못하면 작업 경계가 흐려진다. | `settings.gradle.kts`에 포함된 모든 Gradle 모듈은 모듈별 `AGENTS.md`를 가져야 하며, 루트 `AGENTS.md` 인덱스에도 나타나야 한다. | `settings.gradle.kts`의 `include(...)` 호출에서 모든 `:<module>` 값을 추출한다. 한 줄에 여러 모듈이 있거나 single quote를 쓰는 경우도 놓치지 않도록 호출 단위 추출 후 comma split을 수행한다. | `scripts/quality/product-harness-check.sh`, pre-commit, pre-push, CI |
| Gradle 의존 방향 | 모듈 경계 위반은 컴파일은 되더라도 아키텍처를 무너뜨린다. | `core:* -> feature:*`, `app -> feature:*:impl`, `feature:*:api -> impl/entry`, `feature:*:impl -> entry`, 다른 feature로 향하는 `entry` 의존을 실패 처리한다. | 각 `build.gradle.kts`의 모든 `project(...)` 호출에서 `:<module>` 값을 추출한다. 한 줄에 여러 dependency가 있거나 `project(path = "...")`, single quote가 섞여도 검사 대상에 포함한다. | `scripts/quality/product-harness-check.sh`, pre-commit, pre-push, CI |
| source import 경계 | Gradle 의존성 외에도 source import로 구현 상세가 새는지 조기에 확인해야 한다. | `core` source에서 feature 패키지를 참조하거나, `app/src/main`이 feature impl 패키지를 직접 참조하거나, feature api source가 impl/entry 패키지를 참조하면 실패한다. | Kotlin/Java source를 대상으로 금지 패키지 패턴을 검색한다. source 파일이 없는 모듈에서도 CI가 멈추지 않도록 `/dev/null` dummy 입력을 붙여 `xargs grep`을 실행한다. 앱 계측 테스트의 feature impl resource 참조는 제품 코드 경계 위반이 아니므로 `app/src/main`만 실패 대상으로 삼는다. | `scripts/quality/product-harness-check.sh`, pre-commit, pre-push, CI |
| TDD Guard | AI가 운영 코드를 먼저 바꾸고 테스트를 나중으로 미루는 흐름을 막는다. | `*/src/main/*.kt`, `*/src/main/*.java` 변경 전에 같은 모듈의 테스트 변경이 없으면 Codex edit/write/patch 단계에서 거부할 수 있다. | patch 입력과 도구 인자를 읽어 변경 대상 파일을 추출하고, Git working tree의 테스트 변경과 같은 모듈인지 비교한다. | `scripts/codex-hooks/tdd-guard.sh` |
| main 직접 작업 차단 | 실험 작업이 main에 직접 섞이면 PR 단위 검증과 회고가 사라진다. | `main`/`master`에서 commit/push를 거부한다. | Git hook에서 현재 branch와 push target ref를 확인한다. | `scripts/git-hooks/pre-commit.sh`, `scripts/git-hooks/pre-push.sh` |
| 최신 main 포함 확인 | 오래된 기준 위의 AI 작업은 충돌과 회귀 위험이 크다. | 현재 브랜치가 최신 `origin/main`을 포함하지 않으면 commit/push를 막는다. | `origin/main`을 fetch한 뒤 merge-base 관계를 검사한다. | `scripts/git-hooks/ensure-latest-main.sh` |
| 영향 범위 lint | 모든 push에서 전체 검증만 실행하면 느리고, 아무 검증도 없으면 위험하다. | 변경 파일을 기준으로 관련 모듈 `lintDebug`를 실행하고, Gradle/CI/hook 변경처럼 영향이 넓은 경우 전체 `lint`로 폴백한다. | changed file path를 app/core/feature 모듈로 분류해 Gradle lint task 목록을 만든다. | `scripts/git-hooks/pre-push.sh` |
| CI build/test/lint/coverage | 로컬 환경 차이나 hook 누락에 상관없이 PR 기준 최종 검증이 필요하다. | 단위 테스트, debug APK 빌드, strict lint, 핵심 non-view coverage gate를 원격에서 실행한다. | GitHub Actions가 JDK/Gradle을 준비하고 `testDebugUnitTest`, `assembleDebug`, `lint`, `coverageVerifyAll`을 실행한다. 실패 report는 artifact로 남긴다. | `.github/workflows/android-ci.yml` |
| PR 설명 템플릿 | 자동화 결과와 사람이 판단한 tradeoff가 PR에 남아야 다음 작업이 이어진다. | 문제, 범위, 설계, 테스트, 커버리지, 리스크, 마이그레이션, 하네스 결과, 인간 판단 영역을 기록한다. | `.github/pull_request_template.md`가 PR 작성 시 필수 구조를 제공한다. | GitHub PR |

## 제품 하네스 체크

`scripts/quality/product-harness-check.sh`는 YourTodo의 구조 규칙을 빠르게 확인하는 저장소 전용 자동화다.

직접 실행:

```sh
scripts/quality/product-harness-check.sh
```

성공 결과:

```text
[Product Harness] PASS: 모듈 가이드, Gradle 의존 방향, 금지 import 검사를 통과했습니다. modules=23
```

실패 결과:

```text
[Product Harness] FAIL

자동화된 제품 하네스가 명확한 구조 규칙 위반을 발견했습니다.
아래 항목은 사람의 취향 판단이 아니라 저장소 정책으로 기계 검증 가능한 항목입니다.

- :core:data 이 :feature:todo:api 에 의존합니다. core 모듈은 feature 모듈에 의존할 수 없습니다.
```

자체 테스트:

```sh
scripts/quality/product-harness-check-test.sh
```

이 테스트는 임시 저장소를 만들고 다음 시나리오를 검증한다.

- 의도된 모듈 구조는 통과한다.
- 모듈별 `AGENTS.md`가 없으면 실패한다.
- 한 줄에 여러 `include(...)` 또는 `project(...)`가 있어도 모든 모듈/의존을 검사한다.
- `core -> feature` Gradle 의존은 실패한다.
- `app/src/main`에서 feature impl 패키지를 직접 import하면 실패한다.
- feature api source가 impl 패키지를 import하면 실패한다.
- Kotlin/Java source 파일이 없는 모듈도 hang 없이 통과한다.

## 자동화하지 않는 영역

자동화는 명확한 구조 위반을 빠르게 찾는 데 강하지만, 제품 판단을 대신하지 않는다.

| 인간 판단 영역 | 왜 사람이 봐야 하는가 | PR에 남길 내용 |
|---|---|---|
| 기능 의도 | 자동화는 "무엇을 만들 가치가 있는지"를 모른다. | 사용자 문제, 비목표, 수용 기준 |
| 복잡도 정당성 | 새 모듈이나 abstraction이 실제로 관찰성/테스트 가능성을 높이는지는 맥락 판단이 필요하다. | 선택한 구조의 이유와 버린 대안 |
| 테스트 품질 | 테스트 존재와 실행 결과는 자동화할 수 있지만, 테스트가 올바른 행위를 고정하는지는 사람이 판단해야 한다. | 실패를 고정한 시나리오, 검증하려는 행동 |
| UI/UX 자연스러움 | screenshot diff나 lint는 흐름의 자연스러움을 완전히 판단하지 못한다. | 수동 확인 시나리오와 결과 |
| AI 결과물 신뢰성 | AI가 규칙을 우연히 통과했는지, 요구사항을 실제로 이해했는지는 리뷰가 필요하다. | AI가 헷갈릴 수 있었던 지점, 리뷰 판단 |
| 릴리즈 리스크 | migration, server contract, rollout 전략은 기술 검증과 제품 영향이 함께 얽힌다. | rollback plan, migration 여부, 호환성 판단 |

## 운영 원칙

- 새 자동화는 기존 코드베이스에서 먼저 통과해야 한다.
- 기존의 정당한 테스트 구조를 막는 자동화는 실패 범위를 좁힌다.
- 자동화 실패 메시지는 해결할 파일과 이유를 드러내야 한다.
- 느린 검사는 CI 또는 pre-push로 보내고, 빠른 구조 검사는 pre-commit에 둔다.
- 자동화가 발견한 사실과 사람이 판단한 결론을 PR에 함께 남긴다.

## 확장 후보

현재 하네스는 구조 경계와 검증 루프에 집중한다. 다음 자동화는 점진적으로 추가한다.

| 후보 | 자동화 가능성 | 주의점 |
|---|---|---|
| 사용자 노출 문자열 하드코딩 탐지 | Compose/Kotlin source에서 문자열 literal을 일부 탐지 가능 | 테스트 이름, 로그, preview, key 값과 구분해야 한다. 처음에는 report-only가 적합하다. |
| Room migration 동반 검사 | Entity/schema 파일 변경 시 migration/test 변경 여부를 확인 가능 | Room schema 위치와 버전 규칙을 안정화한 뒤 gate로 올린다. |
| 변경 모듈 리포트 | PR diff에서 변경 모듈 수와 파일 수를 계산 가능 | 숫자만으로 품질을 판단하지 말고 review cue로 사용한다. |
| PR template 필수 항목 검사 | 섹션 누락과 빈 항목을 탐지 가능 | GitHub context가 필요하므로 CI PR 이벤트에서 적용한다. |
| AI coding eval set | 반복 과제를 정의하고 성공 기준을 체크 가능 | 과제가 실제 프로젝트 리스크를 대표해야 한다. |
