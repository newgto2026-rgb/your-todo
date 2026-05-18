# Rework Metrics: codex/manual-pdf-update

## Branch Summary

| Field | Value |
|---|---|
| Branch | `codex/manual-pdf-update` |
| PR | `https://github.com/newgto2026-rgb/your-todo/pull/110` |
| Primary AI Model | `Codex` |
| Task Type | `Documentation` |
| Rework Count | `2` |
| P0 Issues | `0` |
| P1 Issues | `0` |
| P2 Issues | `2` |
| Automation Possible Issues | `2` |
| Automation Added Issues | `2` |
| Open Events | `0` |

## Rework Events

### R2026051901 - Rework metrics should analyze feedback at intake time

| Field | Value |
|---|---|
| Status | `verified` |
| Detected Phase | `user feedback` |
| Feedback Source | `Codex thread` |
| Severity | `P2` |
| Attribution | `Mixed` |
| Root Cause Category | `process feedback loop` |
| Automation Possible | `Yes` |
| Automation Added | `Yes: pre-commit no longer treats metrics as commit-time bookkeeping; capture template now prompts intake analysis` |
| Fix Scope | `docs + hook + capture script` |
| Fix Size | `small` |
| Rework Commit | `6504b0c` |
| Verification | `scripts/quality/rework-metrics-check.sh --local`, `git diff --check -- AGENTS.md README.md docs/agent/rework-metrics.md docs/agent/quality-gates.md docs/agent/automation-harness.md scripts/git-hooks/pre-commit.sh scripts/quality/rework-metrics-capture.sh docs/ai-rework/branches/codex-manual-pdf-update.md` |

#### External Refs

- User Feedback: `재작업 규칙을 좀 바꿔줄래? 커밋할때가 아니라 피드백이 나왔을때 그 피드백에 대한 분석이 들어가야해 compound engineering 의 개념이야`
- User Feedback: `주기적으로 그 agents.md에 그 해당 문서를 참조해서 다시 같은 문제가 안일어나도록 피드백 루프를 만들어줘`

#### Compound Engineering Intake

| Field | Analysis |
|---|---|
| Feedback Signal | Rework metrics were being enforced as commit-time bookkeeping, while the user wanted feedback-time analysis. |
| Product/Engineering Impact | Agents could satisfy a hook by adding a metrics file late without using the feedback to change the next engineering move. |
| Root Cause Hypothesis | The process emphasized reconciliation checks over intake analysis and did not route lessons back into AGENTS.md. |
| System Gap | Pre-commit ran `rework-metrics-check.sh --local`; capture templates lacked a dedicated intake analysis block; AGENTS.md did not require periodic lesson review. |
| Automation Hypothesis | Move commit-time enforcement out of pre-commit, keep explicit reconciliation checks for PR/CI, and make the capture template ask for feedback signal, impact, root cause, system gap, automation, and decision. |
| Decision | Update the hook, docs, capture template, and AGENTS feedback loop so future agents read and promote lessons before repeating issues. |

#### Feedback Summary

The rework system should behave as a compound engineering feedback loop: when feedback arrives, record analysis immediately, use it as implementation input, and periodically feed recurring lessons back into AGENTS.md and agent playbooks.

#### Fix Summary

Removed commit-time local rework metrics enforcement from pre-commit, documented intake-first semantics, added an intake analysis table to captured events, and added an AGENTS.md loop for reading branch metrics lessons and promoting recurring patterns.

#### Lesson

Rework observability is useful only when it changes the next action. Reconciliation checks are guardrails, but the engineering value comes from capturing feedback as a signal and feeding recurring lessons into the agent operating rules.

### R2026051902 - Preserve instructions across automatic context compaction

| Field | Value |
|---|---|
| Status | `verified` |
| Detected Phase | `user feedback` |
| Feedback Source | `Codex thread` |
| Severity | `P2` |
| Attribution | `Mixed` |
| Root Cause Category | `context continuity` |
| Automation Possible | `Yes` |
| Automation Added | `Yes: ignored temp context convention plus AGENTS cleanup rule` |
| Fix Scope | `docs + ignore rule + temp handoff` |
| Fix Size | `small` |
| Rework Commit | `HEAD` |
| Verification | `git diff --check -- .gitignore AGENTS.md README.md docs/agent/rework-metrics.md docs/ai-rework/branches/codex-manual-pdf-update.md`, `scripts/quality/product-harness-check.sh` |

#### External Refs

- User Feedback: `컨텍스트 자동압축을 대비해 컨텍스트를 잊지않도록 모든 명령사항과 핵심 내용들을 임시파일에 저장해주고 작업트리 정리를 요구하면 함께 지워줄수있도록 하자. 메인 Agents.md에 카파시에 대한 내용도 지워줘`
- User Feedback: `readme에 메뉴얼 업데이트하는것도 잊지말고`
- User Feedback: `임시 산출물 정리는 agents.md의 영역인거같은데`
- User Feedback: `아니 메뉴얼을 어떻게 갱신하는지가 아니라 메뉴얼 자체를 소개로 올려달라고`
- User Feedback: `그리고 말이 자꾸 꼬이는데 임시 산출물은 결국 컨텍스트 정리 규칙으로 나온 임시 산출물일거고 컨텍스트 내용을 저장한것을 임시 산출물이라고 부르는거야`

#### Compound Engineering Intake

| Field | Analysis |
|---|---|
| Feedback Signal | The user wants long-running agent work to survive automatic context compaction by persisting instructions and key state in a temporary file, wants that temporary context file treated as the temporary artifact to clean up, and wants README to introduce the manual itself rather than document the update procedure. |
| Product/Engineering Impact | A compacted or resumed agent could lose late instructions such as manual update requirements, PR state, or cleanup expectations, causing repeated correction cycles. |
| Root Cause Hypothesis | The project rules emphasize persistent branch metrics and AGENTS lessons, but do not define a lightweight temporary handoff file for in-progress conversational context. |
| System Gap | AGENTS.md lacks a precise context-compaction continuity rule defining the temporary artifact as the saved context file, README does not introduce the manual contents, and no ignored temp context filename convention exists. |
| Automation Hypothesis | Add an ignored `tmp/agent-context-*.md` convention and make AGENTS require updating it for multi-step work; cleanup can then remove predictable temp context files. |
| Decision | Keep manual content introduction in README, define the temporary artifact in AGENTS.md as the saved context file, add `.gitignore`, and create the current temp context file before continuing PR/merge work. |

#### Feedback Summary

The project needs a durable but non-committed handoff for long-running AI work so automatic context compaction does not erase active user requirements, while README should introduce the user manual as a product document.

#### Fix Summary

Added a temporary context file convention to AGENTS.md, defined that context file as the temporary artifact for cleanup, ignored the temporary context files in `.gitignore`, kept README focused on introducing the manual PDF, and created the current branch handoff file under `tmp/`.

#### Lesson

README should describe project artifacts for readers, while AGENTS.md should define temporary continuity files and their cleanup semantics precisely.

## External Event Coverage

### Review Threads

No review threads recorded yet.

### Actionable PR Comments

No actionable PR comments recorded yet.

### Check Failures

No check failures recorded yet.

### User Feedback

- `Codex thread`: feedback-time analysis should replace commit-time bookkeeping.
- `Codex thread`: AGENTS.md should periodically reference rework metrics lessons to prevent repeat issues.
- `Codex thread`: context-compaction continuity should use a temporary handoff file.
- `Codex thread`: the temporary artifact is the context file created by the context-compaction rule.
- `Codex thread`: README should introduce the manual itself.

## Non-Rework Follow-up Commits

No non-rework follow-up commits recorded yet.
