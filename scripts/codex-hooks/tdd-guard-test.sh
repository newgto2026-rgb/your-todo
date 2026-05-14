#!/usr/bin/env sh

set -eu

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
guard_script="$script_dir/tdd-guard.sh"
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

run_guard() {
  TDD_GUARD_REPO_ROOT="$tmp_dir/repo" "$guard_script" "$@"
}

mkdir -p "$tmp_dir/repo"
cd "$tmp_dir/repo"
git init -q
git config user.email "tdd-guard@example.com"
git config user.name "TDD Guard Test"

mkdir -p feature/todo/impl/src/main/java/com/example
cat > feature/todo/impl/src/main/java/com/example/TodoViewModel.kt <<'EOF'
package com.example

class TodoViewModel
EOF
git add .
git commit -q -m "initial"

if run_guard edit feature/todo/impl/src/main/java/com/example/TodoViewModel.kt >/tmp/tdd-guard.out 2>/tmp/tdd-guard.err; then
  fail "production edit without a test change should be denied"
fi
pass "denies production edit before a test exists in the change set"

mkdir -p feature/todo/impl/src/test/java/com/example
cat > feature/todo/impl/src/test/java/com/example/TodoViewModelTest.kt <<'EOF'
package com.example

class TodoViewModelTest
EOF

run_guard edit feature/todo/impl/src/main/java/com/example/TodoViewModel.kt >/tmp/tdd-guard.out 2>/tmp/tdd-guard.err ||
  fail "production edit should pass after a same-module test change"
pass "allows production edit after a same-module test change"

git add .
git commit -q -m "add todo test"

if run_guard edit feature/todo/impl/src/main/java/com/example/TodoViewModel.kt >/tmp/tdd-guard.out 2>/tmp/tdd-guard.err; then
  fail "existing committed tests alone should not satisfy TDD guard"
fi
pass "requires a current test change, not only existing committed tests"

patch_input='*** Begin Patch
*** Update File: feature/todo/impl/src/main/java/com/example/TodoViewModel.kt
@@
 class TodoViewModel
*** Add File: feature/todo/impl/src/test/java/com/example/TodoViewModelNewTest.kt
+package com.example
*** End Patch'

printf "%s\n" "$patch_input" | run_guard apply_patch >/tmp/tdd-guard.out 2>/tmp/tdd-guard.err ||
  fail "single patch containing production and test changes should pass"
pass "allows one patch containing production and test changes"

run_guard edit docs/notes.md >/tmp/tdd-guard.out 2>/tmp/tdd-guard.err ||
  fail "documentation edits should not be guarded"
pass "ignores documentation edits"
