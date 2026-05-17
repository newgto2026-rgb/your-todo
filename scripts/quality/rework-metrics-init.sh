#!/usr/bin/env sh

set -eu

repo_root="${REWORK_METRICS_REPO_ROOT:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
branch="${1:-$(git -C "$repo_root" symbolic-ref --quiet --short HEAD 2>/dev/null || true)}"

if [ -z "$branch" ]; then
  echo "[Rework Metrics] 브랜치를 확인할 수 없습니다." >&2
  exit 1
fi

safe_branch="$(printf "%s" "$branch" | sed 's#[^A-Za-z0-9._-]#-#g')"
metrics_dir="$repo_root/docs/ai-rework/branches"
metrics_file="$metrics_dir/$safe_branch.md"

mkdir -p "$metrics_dir"

if [ -f "$metrics_file" ]; then
  echo "[Rework Metrics] 이미 존재합니다: ${metrics_file#"$repo_root"/}"
  exit 0
fi

cat > "$metrics_file" <<EOF
# Rework Metrics: $branch

## Branch Summary

| Field | Value |
|---|---|
| Branch | \`$branch\` |
| PR | \`TBD\` |
| Primary AI Model | \`TBD\` |
| Task Type | \`TBD\` |
| Rework Count | \`0\` |
| P0 Issues | \`0\` |
| P1 Issues | \`0\` |
| P2 Issues | \`0\` |
| Automation Possible Issues | \`0\` |
| Automation Added Issues | \`0\` |
| Open Events | \`0\` |

## Rework Events

No rework events recorded yet.

## External Event Coverage

### Review Threads

No review threads recorded yet.

### Actionable PR Comments

No actionable PR comments recorded yet.

### Check Failures

No check failures recorded yet.

## Non-Rework Follow-up Commits

No non-rework follow-up commits recorded yet.
EOF

echo "[Rework Metrics] 생성 완료: ${metrics_file#"$repo_root"/}"
