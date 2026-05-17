# Rework Metrics: codex/todo-sharing-status-copy

## Branch Summary

| Field | Value |
|---|---|
| Branch | `codex/todo-sharing-status-copy` |
| PR | `TBD` |
| Primary AI Model | `GPT-5` |
| Task Type | `UX copy refinement` |
| Rework Count | `1` |
| P0 Issues | `0` |
| P1 Issues | `0` |
| P2 Issues | `1` |
| Automation Possible Issues | `0` |
| Automation Added Issues | `0` |
| Open Events | `0` |

## Rework Events

### R1 - Shared Todo Monitor Label Needed Clearer Product Copy

| Field | Value |
|---|---|
| Source | `user feedback` |
| Priority | `P2` |
| Status | `verified` |
| Detected Phase | `review` |
| Feedback Source | `Codex thread` |
| Attribution | `Mixed` |
| Root Cause Category | `UX copy clarity` |
| Automation Possible | `no` |
| Automation Added | `no` |
| Fix Scope | `feature:friends:impl resources` |
| Fix Size | `small` |
| Rework Commit | `HEAD` |
| Verification | `./gradlew :feature:friends:impl:lintDebug` |

#### External Refs

- User Feedback: `주고받은 할일 보다는 공유 요약 이런식이 좀 더 고급지지않나?`
- User Feedback: `그럼 명확하게 할일 공유 현황`

#### Feedback Summary

The `주고받은 일` label was clear enough functionally but did not feel as polished or explicit as the desired product wording.

#### Fix Summary

Renamed the monitor entry and related dialog/loading copy to `할 일 공유 현황`, with matching English resources.

#### Lesson

When a compact action opens a status surface, prefer a noun phrase that names the surface directly instead of describing the exchange mechanics.

## External Event Coverage

### Review Threads

No review threads recorded yet.

### Actionable PR Comments

No actionable PR comments recorded yet.

### Check Failures

No check failures recorded yet.

## Non-Rework Follow-up Commits

No non-rework follow-up commits recorded yet.
