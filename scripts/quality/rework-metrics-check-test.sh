#!/usr/bin/env sh

set -eu

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
init_script="$script_dir/rework-metrics-init.sh"
check_script="$script_dir/rework-metrics-check.sh"
tmp_dir="$(mktemp -d)"

cleanup() {
  rm -rf "$tmp_dir"
}

trap cleanup EXIT

fail() {
  echo "[FAIL] $1" >&2
  exit 1
}

pass() {
  echo "[PASS] $1"
}

repo="$tmp_dir/repo"
mkdir -p "$repo"
cd "$repo"
git init -q
git checkout -q -b codex/rework-test

if REWORK_METRICS_REPO_ROOT="$repo" "$check_script" --local >/tmp/rework-check.out 2>/tmp/rework-check.err; then
  fail "missing branch metrics doc should fail"
fi
pass "denies missing branch metrics docs on work branches"

REWORK_METRICS_REPO_ROOT="$repo" "$init_script" codex/rework-test >/tmp/rework-init.out
REWORK_METRICS_REPO_ROOT="$repo" "$check_script" --local >/tmp/rework-check.out 2>/tmp/rework-check.err ||
  fail "initialized branch metrics doc should pass local check"
pass "allows initialized branch metrics docs"

cat >> "$repo/docs/ai-rework/branches/codex-rework-test.md" <<'EOF'

### R999 - Open event

| Field | Value |
|---|---|
| Status | `open` |
EOF

if REWORK_METRICS_REPO_ROOT="$repo" "$check_script" --strict-events >/tmp/rework-check.out 2>/tmp/rework-check.err; then
  fail "open event should fail strict checks"
fi
pass "denies open events in strict checks"

git checkout -q -b main
rm -rf "$repo/docs"
REWORK_METRICS_REPO_ROOT="$repo" "$check_script" --local >/tmp/rework-check.out 2>/tmp/rework-check.err ||
  fail "main local check should not require branch metrics docs"
pass "does not require branch metrics docs on main local checks"
