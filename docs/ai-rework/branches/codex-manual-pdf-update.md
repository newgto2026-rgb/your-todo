# Rework Metrics: codex/manual-pdf-update

## Branch Summary

| Field | Value |
|---|---|
| Branch | `codex/manual-pdf-update` |
| PR | `https://github.com/newgto2026-rgb/your-todo/pull/110` |
| Primary AI Model | `Codex` |
| Task Type | `Documentation` |
| Rework Count | `8` |
| P0 Issues | `0` |
| P1 Issues | `5` |
| P2 Issues | `3` |
| Automation Possible Issues | `8` |
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

- User Feedback Summary: feedback should be analyzed at intake time, not reconstructed at commit time.
- User Feedback Summary: recurring rework lessons should feed back into AGENTS.md and agent playbooks.

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
| Rework Commit | `687b900` |
| Verification | `git diff --check -- .gitignore AGENTS.md README.md docs/agent/rework-metrics.md docs/ai-rework/branches/codex-manual-pdf-update.md`, `scripts/quality/product-harness-check.sh` |

#### External Refs

- User Feedback Summary: preserve long-running task context in an ignored temporary context file.
- User Feedback Summary: remove the Karpathy attribution from the main AGENTS.md.
- User Feedback Summary: README should introduce the manual itself, not describe the manual update process.
- User Feedback Summary: the temporary artifact in this rule is the saved context file itself.

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

- `PRRT_kwDOSAf4v86C4yfG`: exact intake field names in `AGENTS.md`
- `PRRT_kwDOSAf4v86C4yfh`: exact Title Case fix fields in `AGENTS.md`
- `PRRT_kwDOSAf4v86C4yfo`: exact intake field names in `docs/agent/rework-metrics.md`
- `PRRT_kwDOSAf4v86C4yfz`: `Field | Analysis` intake table header in `docs/agent/rework-metrics.md`
- `PRRT_kwDOSAf4v86C4yf2`: exact Title Case post-fix fields in `docs/agent/rework-metrics.md`

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
- `Codex thread`: summarize user feedback instead of copying raw wording, and make agent-to-agent feedback reflection explicit.

## Non-Rework Follow-up Commits

No non-rework follow-up commits recorded yet.

## Rework Events

### R2026051903 - Summarize feedback and emphasize agent review reflection

| Field | Value |
|---|---|
| Status | `verified` |
| Detected Phase | `user feedback` |
| Feedback Source | `Codex thread` |
| Severity | `P2` |
| Attribution | `Mixed` |
| Root Cause Category | `feedback recording hygiene` |
| Automation Possible | `Yes` |
| Automation Added | `No: documented as an operating rule; future doc-lint can enforce raw quote limits` |
| Fix Scope | `docs + branch metrics` |
| Fix Size | `small` |
| Rework Commit | `HEAD` |
| Verification | `git diff --check -- AGENTS.md docs/agent/rework-metrics.md docs/ai-rework/branches/codex-manual-pdf-update.md`, `scripts/quality/rework-metrics-check.sh --local` |

#### External Refs

- User Feedback Summary: branch metrics should store concise feedback signals, not raw user wording.
- User Feedback Summary: agent-to-agent and review-bot feedback should be captured and reflected carefully.

#### Compound Engineering Intake

| Field | Analysis |
|---|---|
| Feedback Signal | Feedback records were too verbatim and did not emphasize that agent/review-bot feedback must be reflected, not merely captured. |
| Product/Engineering Impact | Raw phrasing can make metrics noisy and less reusable, while agent feedback can be lost if treated as secondary to human feedback. |
| Root Cause Hypothesis | The capture process optimized for traceability but did not distinguish concise summaries from raw transcript copying. |
| System Gap | AGENTS.md and the rework guide did not define feedback summarization hygiene or agent-to-agent feedback treatment. |
| Automation Hypothesis | A future doc-lint rule could detect overly long `User Feedback` quote blocks and require `User Feedback Summary` labels. |
| Decision | Summarize existing user feedback refs and update the operating rules to require concise summaries plus explicit agent/review feedback reflection. |

#### Feedback Summary

Store the essential signal of user feedback and give agent/review-bot feedback first-class treatment in the rework loop.

#### Fix Summary

Replaced raw user feedback refs with summaries, added feedback recording guidance to AGENTS.md and the rework guide, and documented the agent feedback reflection expectation.

#### Lesson

Rework records should preserve decisions and traceability, not raw conversation noise. Agent feedback is still feedback and needs a visible closure path.

### R20260519004925-1 - Review thread: AGENTS.md

| Field | Value |
|---|---|
| Status | `verified` |
| Detected Phase | `review` |
| Feedback Source | `gemini-code-assist` |
| Severity | `P1` |
| Attribution | `AI` |
| Root Cause Category | `documentation consistency` |
| Automation Possible | `Yes` |
| Automation Added | `No: existing review and rework reconciliation caught this; doc-lint rule can be a future improvement` |
| Fix Scope | `docs` |
| Fix Size | `small` |
| Rework Commit | `HEAD` |
| Verification | `git diff --check -- AGENTS.md docs/agent/rework-metrics.md docs/ai-rework/branches/codex-manual-pdf-update.md`, `scripts/quality/rework-metrics-check.sh --local` |

#### External Refs

- Review Thread: `PRRT_kwDOSAf4v86C4yfG`
- Review Comment: `3260174685`
- Review URL: https://github.com/newgto2026-rgb/your-todo/pull/110#discussion_r3260174685
- Review Path: `AGENTS.md:65`
- Resolved At Capture: `false`
- Outdated At Capture: `false`

#### Compound Engineering Intake

| Field | Analysis |
|---|---|
| Feedback Signal | AGENTS.md used Korean paraphrases for intake fields instead of exact names like Feedback Signal and Product/Engineering Impact. |
| Product/Engineering Impact | Agents could fill inconsistent field names, weakening the capture template and metrics reconciliation. |
| Root Cause Hypothesis | The prose was localized without preserving exact schema names. |
| System Gap | No doc lint enforces field-name consistency across AGENTS.md, the rework guide, and capture output. |
| Automation Hypothesis | A future doc consistency check could assert required field names appear verbatim in the operating rules. |
| Decision | Replace paraphrased field names with exact English Title Case names. |

#### Feedback Summary

Use exact intake field names in AGENTS.md so agents can map prose instructions to the branch metrics template without ambiguity.

#### Fix Summary

Updated AGENTS.md to list Feedback Signal, Product/Engineering Impact, Root Cause Hypothesis, System Gap, Automation Hypothesis, and Decision verbatim.

#### Lesson

Schema-like documentation fields should remain exact even inside localized prose.

### R20260519004925-2 - Review thread: AGENTS.md

| Field | Value |
|---|---|
| Status | `verified` |
| Detected Phase | `review` |
| Feedback Source | `gemini-code-assist` |
| Severity | `P1` |
| Attribution | `AI` |
| Root Cause Category | `documentation consistency` |
| Automation Possible | `Yes` |
| Automation Added | `No: existing review and rework reconciliation caught this; doc-lint rule can be a future improvement` |
| Fix Scope | `docs` |
| Fix Size | `small` |
| Rework Commit | `HEAD` |
| Verification | `git diff --check -- AGENTS.md docs/agent/rework-metrics.md docs/ai-rework/branches/codex-manual-pdf-update.md`, `scripts/quality/rework-metrics-check.sh --local` |

#### External Refs

- Review Thread: `PRRT_kwDOSAf4v86C4yfh`
- Review Comment: `3260174715`
- Review URL: https://github.com/newgto2026-rgb/your-todo/pull/110#discussion_r3260174715
- Review Path: `AGENTS.md:67`
- Resolved At Capture: `false`
- Outdated At Capture: `false`

#### Compound Engineering Intake

| Field | Analysis |
|---|---|
| Feedback Signal | AGENTS.md used lowercase/paraphrased fix fields instead of Title Case names such as Fix Scope, Verification, and Lesson. |
| Product/Engineering Impact | Inconsistent field casing makes it harder for agents and scripts to align event updates. |
| Root Cause Hypothesis | The prose treated metrics fields as ordinary words rather than schema labels. |
| System Gap | No automated doc check verifies Title Case field names in guidance prose. |
| Automation Hypothesis | A future doc consistency check could scan for required Title Case labels. |
| Decision | Update AGENTS.md to use exact Title Case field names. |

#### Feedback Summary

Use exact Title Case update field names in AGENTS.md.

#### Fix Summary

Updated AGENTS.md to say Fix Scope, Verification, and Lesson, and to preserve Lesson casing when describing promotion.

#### Lesson

Operational rules should treat metrics field names as stable labels.

### R20260519004925-3 - Review thread: docs/agent/rework-metrics.md

| Field | Value |
|---|---|
| Status | `verified` |
| Detected Phase | `review` |
| Feedback Source | `gemini-code-assist` |
| Severity | `P1` |
| Attribution | `AI` |
| Root Cause Category | `documentation consistency` |
| Automation Possible | `Yes` |
| Automation Added | `No: existing review and rework reconciliation caught this; doc-lint rule can be a future improvement` |
| Fix Scope | `docs` |
| Fix Size | `small` |
| Rework Commit | `HEAD` |
| Verification | `git diff --check -- AGENTS.md docs/agent/rework-metrics.md docs/ai-rework/branches/codex-manual-pdf-update.md`, `scripts/quality/rework-metrics-check.sh --local` |

#### External Refs

- Review Thread: `PRRT_kwDOSAf4v86C4yfo`
- Review Comment: `3260174723`
- Review URL: https://github.com/newgto2026-rgb/your-todo/pull/110#discussion_r3260174723
- Review Path: `docs/agent/rework-metrics.md:7`
- Resolved At Capture: `false`
- Outdated At Capture: `false`

#### Compound Engineering Intake

| Field | Analysis |
|---|---|
| Feedback Signal | The rework guide described intake fields with Korean paraphrases rather than exact schema labels. |
| Product/Engineering Impact | Readers could create branch metrics events with mismatched field names. |
| Root Cause Hypothesis | The purpose prose prioritized natural language over schema consistency. |
| System Gap | The documentation did not explicitly require exact field names in all references. |
| Automation Hypothesis | A future doc consistency check could compare prose references against the canonical intake table. |
| Decision | Replace the prose list with exact field names. |

#### Feedback Summary

The rework guide should introduce intake fields with their exact names.

#### Fix Summary

Updated the purpose section to list Feedback Signal, Product/Engineering Impact, Root Cause Hypothesis, System Gap, Automation Hypothesis, and Decision verbatim.

#### Lesson

Localized explanations can surround schema labels, but should not rename them.

### R20260519004925-4 - Review thread: docs/agent/rework-metrics.md

| Field | Value |
|---|---|
| Status | `verified` |
| Detected Phase | `review` |
| Feedback Source | `gemini-code-assist` |
| Severity | `P1` |
| Attribution | `AI` |
| Root Cause Category | `documentation consistency` |
| Automation Possible | `Yes` |
| Automation Added | `No: existing review and rework reconciliation caught this; doc-lint rule can be a future improvement` |
| Fix Scope | `docs` |
| Fix Size | `small` |
| Rework Commit | `HEAD` |
| Verification | `git diff --check -- AGENTS.md docs/agent/rework-metrics.md docs/ai-rework/branches/codex-manual-pdf-update.md`, `scripts/quality/rework-metrics-check.sh --local` |

#### External Refs

- Review Thread: `PRRT_kwDOSAf4v86C4yfz`
- Review Comment: `3260174733`
- Review URL: https://github.com/newgto2026-rgb/your-todo/pull/110#discussion_r3260174733
- Review Path: `docs/agent/rework-metrics.md:25`
- Resolved At Capture: `false`
- Outdated At Capture: `false`

#### Compound Engineering Intake

| Field | Analysis |
|---|---|
| Feedback Signal | The intake table header used Korean `설명` while generated branch metrics use `Analysis`. |
| Product/Engineering Impact | Template examples could diverge from generated event structure. |
| Root Cause Hypothesis | The guide table was written as explanatory documentation but also functions as a canonical template. |
| System Gap | No check compares guide table headers with capture script output. |
| Automation Hypothesis | A future doc consistency check could assert the canonical `Field | Analysis` header. |
| Decision | Change the table header to `Analysis`. |

#### Feedback Summary

The canonical intake table should match capture script output.

#### Fix Summary

Changed the intake table header to `Field | Analysis`.

#### Lesson

When documentation doubles as a template, examples should mirror generated output exactly.

### R20260519004925-5 - Review thread: docs/agent/rework-metrics.md

| Field | Value |
|---|---|
| Status | `verified` |
| Detected Phase | `review` |
| Feedback Source | `gemini-code-assist` |
| Severity | `P1` |
| Attribution | `AI` |
| Root Cause Category | `documentation consistency` |
| Automation Possible | `Yes` |
| Automation Added | `No: existing review and rework reconciliation caught this; doc-lint rule can be a future improvement` |
| Fix Scope | `docs` |
| Fix Size | `small` |
| Rework Commit | `HEAD` |
| Verification | `git diff --check -- AGENTS.md docs/agent/rework-metrics.md docs/ai-rework/branches/codex-manual-pdf-update.md`, `scripts/quality/rework-metrics-check.sh --local` |

#### External Refs

- Review Thread: `PRRT_kwDOSAf4v86C4yf2`
- Review Comment: `3260174739`
- Review URL: https://github.com/newgto2026-rgb/your-todo/pull/110#discussion_r3260174739
- Review Path: `docs/agent/rework-metrics.md:34`
- Resolved At Capture: `false`
- Outdated At Capture: `false`

#### Compound Engineering Intake

| Field | Analysis |
|---|---|
| Feedback Signal | The guide described post-fix fields with lowercase names instead of exact Title Case labels. |
| Product/Engineering Impact | Event completion could drift from expected field names, making summaries less reliable. |
| Root Cause Hypothesis | Field names were normalized into prose instead of preserved as labels. |
| System Gap | No automated check enforces Title Case field names in the guide. |
| Automation Hypothesis | A future doc consistency check could scan post-fix guidance for Fix Scope, Fix Size, Verification, Lesson, and Rework Commit. |
| Decision | Update post-fix guidance to use exact Title Case labels. |

#### Feedback Summary

The guide should use exact Title Case names for post-fix fields.

#### Fix Summary

Updated the post-fix prose and operating rules to use Fix Scope, Fix Size, Verification, Lesson, and Rework Commit.

#### Lesson

Keep schema labels exact wherever agents are expected to follow them mechanically.
