# AI 재작업 측정

## 목적

이 문서는 AI-assisted development에서 재작업을 장기 지표로 남기는 방법을 정의한다.

재작업 문서는 PR 마지막에 기억을 되살려 작성하는 문서가 아니다. 리뷰, CI 실패, 사용자 피드백, follow-up commit 같은 외부 흔적이 생길 때마다 branch metrics 문서에 reconciliation되어야 한다. 시스템은 AI의 중간 컨텍스트가 사라졌는지를 직접 알 수 없으므로, GitHub/CI/git에 남은 외부 이벤트와 문서의 불일치를 누락 신호로 판단한다.

## Source of Truth

| 외부 흔적 | 누락 판정 방식 |
|---|---|
| GitHub review thread | thread id가 branch metrics 문서에 없으면 confirmed missing |
| PR comment | 액션성 comment id가 문서에 없으면 suspected missing |
| Follow-up commit | 첫 PR commit 이후 commit hash가 rework event 또는 non-rework commit 목록에 없으면 confirmed missing. 단, 현재 HEAD commit은 최종 hash를 자기 자신 안에 기록할 수 없으므로 `HEAD` 마커를 임시 허용한다. 다음 follow-up commit이 생기면 이전 `HEAD`는 실제 hash로 고정해야 한다. |
| Open rework event | strict check에서 `open`, `candidate`, `pending` 상태가 남아 있으면 fail |
| PR body | `AI Rework Metrics` 섹션과 branch metrics 문서 요약이 불일치하면 fail |

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
- 리뷰/CI/사용자 피드백이 들어오면 수정 전에 rework event를 추가한다.
- 수정 후 event에 fix commit, fix size, verification, lesson을 채운다.
- 재작업이 아닌 follow-up commit도 `Non-Rework Follow-up Commits`에 이유와 함께 남긴다.
- 현재 작업 중인 최신 follow-up commit은 `HEAD`로 표시할 수 있지만, 다음 commit을 만들기 전 실제 hash로 고정한다.
- PR ready 전에는 open event가 0이어야 한다.
- 월간/분기 리포트는 branch metrics 문서를 source로 생성한다.

## 리포트 생성

```sh
scripts/quality/rework-metrics-report.py --output docs/ai-rework/reports/2026-05.md
```
