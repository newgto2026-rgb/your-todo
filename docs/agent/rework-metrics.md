# AI 재작업 측정

## 목적

이 문서는 AI-assisted development에서 재작업을 장기 지표로 남기는 방법을 정의한다.

재작업 문서는 PR 마지막이나 commit 직전에 기억을 되살려 작성하는 문서가 아니다. Compound engineering 관점에서 재작업 기록의 핵심은 피드백이 들어온 순간 그 피드백을 engineering signal로 다루는 것이다. 리뷰, CI 실패, 사용자 피드백이 생기면 수정 전에 branch metrics 문서에 Feedback Signal, Product/Engineering Impact, Root Cause Hypothesis, System Gap, Automation Hypothesis, Decision을 먼저 기록한다.

commit/PR 검사는 이 분석을 대신하지 않는다. 검사는 이미 발생한 외부 이벤트가 문서와 불일치할 때 누락을 알려주는 후행 안전망일 뿐이다. 시스템은 AI의 중간 컨텍스트가 사라졌는지를 직접 알 수 없으므로, GitHub/CI/git에 남은 외부 이벤트와 문서의 불일치를 누락 신호로 판단한다.

## Source of Truth

| 외부 흔적 | 누락 판정 방식 |
|---|---|
| GitHub review thread | thread id가 branch metrics 문서에 없으면 confirmed missing |
| PR comment | 액션성 comment id가 문서에 없으면 suspected missing |
| Follow-up commit | 첫 PR commit 이후 commit hash가 rework event 또는 non-rework commit 목록에 없으면 confirmed missing. 단, 현재 HEAD commit은 최종 hash를 자기 자신 안에 기록할 수 없으므로 `HEAD` 마커를 임시 허용한다. 다음 follow-up commit이 생기면 이전 `HEAD`는 실제 hash로 고정해야 한다. |
| Open rework event | strict check에서 `open`, `candidate`, `pending` 상태가 남아 있으면 fail |
| PR body | `AI Rework Metrics` 섹션과 branch metrics 문서 요약이 불일치하면 fail |

## Compound Engineering Intake

피드백이 들어오면 바로 rework event를 만들고 다음 분석을 먼저 채운다.

| Field | Analysis |
|---|---|
| Feedback Signal | 피드백이 실제로 말한 문제 또는 실패 신호 |
| Product/Engineering Impact | 사용자 경험, 품질, 아키텍처, 운영 관점의 영향 |
| Root Cause Hypothesis | 왜 이런 결과가 나왔는지에 대한 초기 가설 |
| System Gap | 문서, 테스트, hook, CI, 리뷰 템플릿, 작업 루프 중 무엇이 놓쳤는지 |
| Automation Hypothesis | 자동화로 조기 탐지할 수 있는지와 가능한 형태 |
| Decision | 즉시 수정, 후속 과제, 의도적 보류 중 선택과 이유 |

이 분석은 fix commit의 부록이 아니라 수정 방향을 정하는 입력이다. 수정이 끝난 뒤에는 같은 event에 Fix Scope, Fix Size, Verification, Lesson, Rework Commit을 채운다.

## Branch Metrics 문서

브랜치마다 하나의 문서를 둔다.

```text
docs/ai-rework/branches/<sanitized-branch>.md
```

예:

```text
docs/ai-rework/branches/codex-data-architecture-doc.md
```

생성:

```sh
scripts/quality/rework-metrics-init.sh
```

검사:

```sh
scripts/quality/rework-metrics-check.sh --local
scripts/quality/rework-metrics-check.sh --pr 103 --repo newgto2026-rgb/your-todo
```

리뷰 thread 캡처:

```sh
scripts/quality/rework-metrics-capture.sh --pr 103 --repo newgto2026-rgb/your-todo
```

## 피드백 기록 방식

- 사용자 피드백은 필요한 핵심 신호만 요약한다. 원문 전체를 그대로 복사하지 않는다.
- GitHub review, 리뷰봇, 다른 에이전트의 피드백은 thread/comment id, URL, 파일 위치, 짧은 요구사항 요약을 남긴다.
- 에이전트 간 피드백이 actionable하면 사람 피드백과 동일하게 rework event로 추적하고, Fix Scope, Verification, Lesson까지 닫는다.
- 반복되는 에이전트 피드백은 `AGENTS.md`, `docs/agent/*`, 테스트, hook, CI 중 적절한 곳에 반영한다.

## 필수 요약 필드

| Field | 설명 |
|---|---|
| Branch | 원본 branch 이름 |
| PR | PR 번호 또는 URL |
| Primary AI Model | 작업에 사용한 주요 AI 모델 |
| Task Type | feature, bugfix, docs, harness 등 |
| Rework Count | 재작업 event 수 |
| P0/P1/P2 Issues | 중요도별 issue 수 |
| Automation Possible Issues | 자동화로 막거나 조기 탐지할 수 있었던 issue 수 |
| Automation Added Issues | 실제 자동화나 테스트가 추가된 issue 수 |
| Open Events | 아직 닫히지 않은 rework event 수 |

## PR 본문 필수 섹션

PR 본문은 branch metrics 문서의 상세 내용을 복사하지 않는다. 대신 검사 가능한 인덱스 역할만 한다.

```md
## AI Rework Metrics
- Branch metrics doc: `docs/ai-rework/branches/<branch>.md`
- Rework count: `0`
- P0/P1/P2: `0/0/0`
- Automation possible: `0`
- Automation added: `0`
- Open events: `0`
```

## 운영 규칙

- 새 브랜치가 시작되면 branch metrics 문서를 먼저 만든다.
- 리뷰/CI/사용자 피드백이 들어오면 수정 전에 rework event를 추가하고 Compound Engineering Intake 분석을 채운다.
- 사용자 피드백은 요약하고, 에이전트/리뷰봇 피드백은 추적 가능한 id와 요약으로 남긴다.
- 수정 전에는 해당 event를 다시 읽어 작업 입력으로 사용한다.
- 수정 후 event에 Rework Commit, Fix Size, Verification, Lesson을 채운다.
- 재작업이 아닌 follow-up commit도 `Non-Rework Follow-up Commits`에 이유와 함께 남긴다.
- 현재 작업 중인 최신 follow-up commit은 `HEAD`로 표시할 수 있지만, 다음 commit을 만들기 전 실제 hash로 고정한다.
- 반복되는 lesson은 `AGENTS.md` 또는 `docs/agent/*` 플레이북으로 승격한다.
- 주기적으로 `scripts/quality/rework-metrics-report.py` 리포트를 만들어 반복 root cause/system gap을 AGENTS 규칙, 테스트, hook, CI 후보로 되돌린다.
- 긴 작업이나 컨텍스트 압축 가능성이 있는 작업은 `tmp/agent-context-<branch>.md`에도 최신 사용자 지시와 진행 상태를 남긴다. 이때 임시 산출물은 해당 컨텍스트 파일을 뜻하며, 정리 규칙은 루트 `AGENTS.md`를 따른다.
- PR ready 전에는 open event가 0이어야 한다.
- 월간/분기 리포트는 branch metrics 문서를 source로 생성한다.

## 리포트 생성

```sh
scripts/quality/rework-metrics-report.py --output docs/ai-rework/reports/2026-05.md
```
