# Rework Metrics: codex/public-repo-security-hardening

## Branch Summary

| Field | Value |
|---|---|
| Branch | `codex/public-repo-security-hardening` |
| PR | `111` |
| Primary AI Model | `GPT-5` |
| Task Type | `security-hardening` |
| Rework Count | `3` |
| P0 Issues | `0` |
| P1 Issues | `2` |
| P2 Issues | `1` |
| Automation Possible Issues | `2` |
| Automation Added Issues | `0` |
| Open Events | `0` |

## Rework Events

### R20260530174000-1 - CI gate: missing branch rework metrics document

| Field | Value |
|---|---|
| Status | `verified` |
| Detected Phase | `ci` |
| Feedback Source | `GitHub Actions: Build, Unit Test, Lint` |
| Severity | `P1` |
| Attribution | `AI` |
| Root Cause Category | `required branch metrics omission` |
| Automation Possible | `Yes` |
| Automation Added | `No: existing rework metrics CI gate already detects this` |
| Fix Scope | `docs/ai-rework/branches/codex-public-repo-security-hardening.md`, `PR #111 body` |
| Fix Size | `Tiny` |
| Rework Commit | `HEAD` |
| Verification | `scripts/quality/rework-metrics-check.sh --local`; `scripts/quality/rework-metrics-check.sh --pr 111 --repo newgto2026-rgb/your-todo` |

#### External Refs

- Check Run: `Build, Unit Test, Lint`
- Run URL: https://github.com/newgto2026-rgb/your-todo/actions/runs/26679289727/job/78636703854

#### Feedback Summary

The Android PR opened without the required branch-level AI rework metrics document and PR body summary, so the CI reconciliation gate failed.

#### Fix Summary

Added the branch metrics document, recorded the CI failure as a rework event, and updated the PR body with the required `AI Rework Metrics` section.

#### Lesson

Security/documentation PRs still need the same branch metrics setup before opening the PR because the repository treats CI/review trace reconciliation as a required release artifact.

### R20260530175000-2 - PR review: release AI URL fallback should not use dev placeholder

| Field | Value |
|---|---|
| Status | `verified` |
| Detected Phase | `review` |
| Feedback Source | `gemini-code-assist` |
| Severity | `P1` |
| Attribution | `AI` |
| Root Cause Category | `release build config hardening` |
| Automation Possible | `Yes` |
| Automation Added | `No: existing review caught this, and this PR keeps the change surgical` |
| Fix Scope | `core/network/build.gradle.kts` |
| Fix Size | `Tiny` |
| Rework Commit | `HEAD` |
| Verification | `./gradlew :core:network:compileDebugKotlin`; `./gradlew :core:network:lintDebug`; `scripts/quality/rework-metrics-check.sh --pr 111 --repo newgto2026-rgb/your-todo` |

#### External Refs

- Review Thread: `PRRT_kwDOSAf4v86F23Zy`
- Review Comment: `PRRC_kwDOSAf4v87GZFcT`
- Review URL: https://github.com/newgto2026-rgb/your-todo/pull/111#discussion_r3328464659
- Review Path: `core/network/build.gradle.kts:51`
- Resolved At Capture: `false`
- Outdated At Capture: `false`

#### Feedback Summary

Release builds should not embed the development placeholder as the AI server fallback while the main server URL falls back to an empty string.

#### Fix Summary

Changed release `YOURTODO_AI_SERVER_BASE_URL` to fall back to an empty string after `yourtodo.aiServerBaseUrl` and `yourtodo.serverBaseUrl`.

#### Lesson

Development placeholders are acceptable for debug defaults only; release fallbacks should stay empty unless explicitly configured.

### R20260530175000-3 - PR review: distinct sanitized worktree placeholders

| Field | Value |
|---|---|
| Status | `verified` |
| Detected Phase | `review` |
| Feedback Source | `gemini-code-assist` |
| Severity | `P2` |
| Attribution | `AI` |
| Root Cause Category | `documentation sanitization clarity` |
| Automation Possible | `No` |
| Automation Added | `Not Added: placeholder semantic clarity is context-specific documentation review` |
| Fix Scope | `docs/qa/manual-regression-bugs.md` |
| Fix Size | `Small` |
| Rework Commit | `HEAD` |
| Verification | tracked Android public exposure scan; `scripts/quality/rework-metrics-check.sh --pr 111 --repo newgto2026-rgb/your-todo` |

#### External Refs

- Review Thread: `PRRT_kwDOSAf4v86F23Zz`
- Review Comment: `PRRC_kwDOSAf4v87GZFcU`
- Review URL: https://github.com/newgto2026-rgb/your-todo/pull/111#discussion_r3328464660
- Review Path: `docs/qa/manual-regression-bugs.md:8`
- Review Thread: `PRRT_kwDOSAf4v86F23Z0`
- Review Comment: `PRRC_kwDOSAf4v87GZFcV`
- Review URL: https://github.com/newgto2026-rgb/your-todo/pull/111#discussion_r3328464661
- Review Path: `docs/qa/manual-regression-bugs.md:10`
- Review Thread: `PRRT_kwDOSAf4v86F23Z3`
- Review Comment: `PRRC_kwDOSAf4v87GZFcY`
- Review URL: https://github.com/newgto2026-rgb/your-todo/pull/111#discussion_r3328464664
- Review Path: `docs/qa/manual-regression-bugs.md:70`
- Review Thread: `PRRT_kwDOSAf4v86F23Z5`
- Review Comment: `PRRC_kwDOSAf4v87GZFca`
- Review URL: https://github.com/newgto2026-rgb/your-todo/pull/111#discussion_r3328464666
- Review Path: `docs/qa/manual-regression-bugs.md:75`
- Review Thread: `PRRT_kwDOSAf4v86F23Z7`
- Review Comment: `PRRC_kwDOSAf4v87GZFcc`
- Review URL: https://github.com/newgto2026-rgb/your-todo/pull/111#discussion_r3328464668
- Review Path: `docs/qa/manual-regression-bugs.md:89`
- Review Thread: `PRRT_kwDOSAf4v86F23Z8`
- Review Comment: `PRRC_kwDOSAf4v87GZFcd`
- Review URL: https://github.com/newgto2026-rgb/your-todo/pull/111#discussion_r3328464669
- Review Path: `docs/qa/manual-regression-bugs.md:251`

#### Feedback Summary

The first sanitization pass used the same placeholder for distinct active, stale, shared, and push-notification worktrees, which obscured the historical mismatch described in the QA notes.

#### Fix Summary

Replaced the ambiguous placeholders with `<active-android-worktree>`, `<stale-android-worktree>`, `<shared-todo-server-worktree>`, and `<push-notifications-server-worktree>` where the distinction matters.

#### Lesson

Sanitizing public docs should remove private paths while preserving the meaning of important topology differences.

## External Event Coverage

### Review Threads

- `PRRT_kwDOSAf4v86F23Zy` covered by `R20260530175000-2`
- `PRRT_kwDOSAf4v86F23Zz` covered by `R20260530175000-3`
- `PRRT_kwDOSAf4v86F23Z0` covered by `R20260530175000-3`
- `PRRT_kwDOSAf4v86F23Z3` covered by `R20260530175000-3`
- `PRRT_kwDOSAf4v86F23Z5` covered by `R20260530175000-3`
- `PRRT_kwDOSAf4v86F23Z7` covered by `R20260530175000-3`
- `PRRT_kwDOSAf4v86F23Z8` covered by `R20260530175000-3`

### Actionable PR Comments

No actionable PR comments recorded yet.

### Check Failures

- Run URL: https://github.com/newgto2026-rgb/your-todo/actions/runs/26679289727/job/78636703854
- Covered By: `R20260530174000-1`

## Non-Rework Follow-up Commits

No non-rework follow-up commits recorded yet.
