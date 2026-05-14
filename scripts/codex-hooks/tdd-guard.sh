#!/usr/bin/env sh

set -eu

repo_root="${TDD_GUARD_REPO_ROOT:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
input_tmp="$(mktemp)"
paths_tmp="$(mktemp)"
changed_tests_tmp="$(mktemp)"
modules_tmp="$(mktemp)"

cleanup() {
  rm -f "$input_tmp" "$paths_tmp" "$changed_tests_tmp" "$modules_tmp"
}

trap cleanup EXIT

if [ "${TDD_GUARD_BYPASS:-}" = "1" ]; then
  echo "[TDD Guard] TDD_GUARD_BYPASS=1 이 설정되어 guard를 건너뜁니다." >&2
  exit 0
fi

operation=""
case "${1:-}" in
  edit|write|patch|apply_patch|Update|Write|Edit|Patch)
    operation="$1"
    shift
    ;;
esac

cat > "$input_tmp" || true

add_path() {
  path="$1"
  [ -z "$path" ] && return

  case "$path" in
    "$repo_root"/*)
      path="${path#"$repo_root"/}"
      ;;
    ./*)
      path="${path#./}"
      ;;
  esac

  case "$path" in
    ""|/*|*..*)
      return
      ;;
  esac

  printf "%s\n" "$path" >> "$paths_tmp"
}

for arg in "$@"; do
  add_path "$arg"
done

sed -n 's/^\*\*\* \(Add\|Update\|Delete\) File: //p' "$input_tmp" |
while IFS= read -r path; do
  add_path "$path"
done

sed -n 's/^[[:space:]]*"[^"]*[Ff]ile[^"]*"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$input_tmp" |
while IFS= read -r path; do
  add_path "$path"
done

sed -n 's/^[[:space:]]*"path"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$input_tmp" |
while IFS= read -r path; do
  add_path "$path"
done

if [ ! -s "$paths_tmp" ]; then
  exit 0
fi

is_test_path() {
  case "$1" in
    */src/test/*|*/src/androidTest/*|*/src/sharedTest/*)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

is_guarded_production_path() {
  case "$1" in
    */src/main/*.kt|*/src/main/*.java)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

module_dir_for_path() {
  printf "%s\n" "${1%%/src/*}"
}

git_changed_files() {
  git -C "$repo_root" diff --name-only --diff-filter=ACMRTUXB HEAD 2>/dev/null || true
  git -C "$repo_root" ls-files --others --exclude-standard 2>/dev/null || true
}

git_changed_files |
while IFS= read -r changed_file; do
  if is_test_path "$changed_file"; then
    printf "%s\n" "$(module_dir_for_path "$changed_file")" >> "$changed_tests_tmp"
  fi
done

while IFS= read -r candidate; do
  if is_test_path "$candidate"; then
    printf "%s\n" "$(module_dir_for_path "$candidate")" >> "$changed_tests_tmp"
  fi
done < "$paths_tmp"

while IFS= read -r candidate; do
  if is_guarded_production_path "$candidate"; then
    printf "%s\n" "$(module_dir_for_path "$candidate")" >> "$modules_tmp"
  fi
done < "$paths_tmp"

if [ ! -s "$modules_tmp" ]; then
  exit 0
fi

missing_modules=""
while IFS= read -r module_dir; do
  [ -z "$module_dir" ] && continue

  if ! grep -Fxq "$module_dir" "$changed_tests_tmp"; then
    missing_modules="${missing_modules}
- ${module_dir}"
  fi
done <<EOF
$(sort -u "$modules_tmp")
EOF

if [ -n "$missing_modules" ]; then
  cat >&2 <<EOF
[TDD Guard] ${operation:-file change} 거부: 운영 코드 변경 전에 같은 모듈의 테스트를 먼저 추가/수정해야 합니다.

테스트 변경이 필요한 모듈:${missing_modules}

허용되는 테스트 경로:
- <module>/src/test/...
- <module>/src/androidTest/...

먼저 실패하는 테스트를 추가하거나 수정한 뒤 다시 시도하세요.
EOF
  exit 1
fi

exit 0
