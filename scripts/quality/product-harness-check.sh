#!/usr/bin/env sh

set -eu

repo_root="${PRODUCT_HARNESS_REPO_ROOT:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
settings_file="$repo_root/settings.gradle.kts"
root_agents_file="$repo_root/AGENTS.md"
failures_tmp="$(mktemp)"
modules_tmp="$(mktemp)"

cleanup() {
  rm -f "$failures_tmp" "$modules_tmp"
}

trap cleanup EXIT

record_failure() {
  printf "%s\n" "- $1" >> "$failures_tmp"
}

print_pass() {
  printf "%s\n" "[Product Harness] PASS: $1"
}

require_file() {
  if [ ! -f "$1" ]; then
    record_failure "$2"
  fi
}

module_dir_for_gradle_path() {
  module="$1"
  printf "%s\n" "${module#:}" | tr ':' '/'
}

module_for_build_file() {
  rel_path="$1"

  case "$rel_path" in
    app/build.gradle.kts)
      printf "%s\n" ":app"
      ;;
    core/*/build.gradle.kts)
      name="$(printf "%s" "$rel_path" | cut -d/ -f2)"
      printf "%s\n" ":core:$name"
      ;;
    feature/*/*/build.gradle.kts)
      feature_name="$(printf "%s" "$rel_path" | cut -d/ -f2)"
      layer_name="$(printf "%s" "$rel_path" | cut -d/ -f3)"
      printf "%s\n" ":feature:$feature_name:$layer_name"
      ;;
    *)
      printf "%s\n" ""
      ;;
  esac
}

feature_name_of() {
  printf "%s" "$1" | cut -d: -f3
}

feature_layer_of() {
  printf "%s" "$1" | cut -d: -f4
}

is_feature_module() {
  case "$1" in
    :feature:*) return 0 ;;
    *) return 1 ;;
  esac
}

is_core_module() {
  case "$1" in
    :core:*) return 0 ;;
    *) return 1 ;;
  esac
}

is_feature_layer() {
  module="$1"
  layer="$2"
  if is_feature_module "$module" && [ "$(feature_layer_of "$module")" = "$layer" ]; then
    return 0
  fi
  return 1
}

check_required_files() {
  require_file "$settings_file" "settings.gradle.kts를 찾을 수 없습니다."
  require_file "$root_agents_file" "루트 AGENTS.md를 찾을 수 없습니다."
}

collect_gradle_modules() {
  if [ ! -f "$settings_file" ]; then
    return
  fi

  grep -Eo 'include[[:space:]]*\([^)]*\)' "$settings_file" |
    sed 's/^[^(]*(//;s/)[[:space:]]*$//' |
    tr ',' '\n' |
    sed -n "s/^[[:space:]]*['\"]\(:[^'\"]*\)['\"][[:space:]]*$/\1/p" |
    sort -u > "$modules_tmp"
}

check_module_guides() {
  if [ ! -s "$modules_tmp" ] || [ ! -f "$root_agents_file" ]; then
    return
  fi

  while IFS= read -r module; do
    [ -z "$module" ] && continue

    module_dir="$(module_dir_for_gradle_path "$module")"
    if [ ! -f "$repo_root/$module_dir/AGENTS.md" ]; then
      record_failure "$module 모듈의 AGENTS.md가 없습니다: $module_dir/AGENTS.md"
    fi

    if ! grep -Fq "\`$module\`" "$root_agents_file"; then
      record_failure "루트 AGENTS.md 모듈 인덱스에 $module 항목이 없습니다."
    fi
  done < "$modules_tmp"
}

check_gradle_dependency() {
  from_module="$1"
  to_module="$2"
  file_path="$3"

  if is_core_module "$from_module" && is_feature_module "$to_module"; then
    record_failure "$from_module 이 $to_module 에 의존합니다. core 모듈은 feature 모듈에 의존할 수 없습니다. ($file_path)"
  fi

  if [ "$from_module" = ":app" ] && is_feature_layer "$to_module" "impl"; then
    record_failure ":app 이 $to_module 구현 모듈에 직접 의존합니다. 앱 셸은 feature entry/api를 통해 연결해야 합니다. ($file_path)"
  fi

  if is_feature_layer "$from_module" "api"; then
    if is_feature_layer "$to_module" "impl" || is_feature_layer "$to_module" "entry"; then
      record_failure "$from_module API 계약 모듈이 $to_module 구현/진입 모듈에 의존합니다. ($file_path)"
    fi
  fi

  if is_feature_layer "$from_module" "impl" && is_feature_layer "$to_module" "entry"; then
    record_failure "$from_module 구현 모듈이 $to_module 진입 모듈에 의존합니다. impl은 entry를 몰라야 합니다. ($file_path)"
  fi

  if is_feature_layer "$from_module" "entry" && is_feature_module "$to_module"; then
    from_feature="$(feature_name_of "$from_module")"
    to_feature="$(feature_name_of "$to_module")"
    if [ "$from_feature" != "$to_feature" ]; then
      record_failure "$from_module entry 모듈이 다른 feature인 $to_module 에 의존합니다. entry는 자기 feature 연결만 담당해야 합니다. ($file_path)"
    fi
  fi
}

check_gradle_dependencies() {
  find "$repo_root" -name build.gradle.kts -not -path "*/build/*" | sort |
  while IFS= read -r build_file; do
    rel_path="${build_file#"$repo_root"/}"
    from_module="$(module_for_build_file "$rel_path")"
    [ -z "$from_module" ] && continue

    grep -Eo 'project[[:space:]]*\([^)]*\)' "$build_file" |
      sed -n "s/.*['\"]\(:[^'\"]*\)['\"].*/\1/p" |
    while IFS= read -r to_module; do
      [ -z "$to_module" ] && continue
      check_gradle_dependency "$from_module" "$to_module" "$rel_path"
    done
  done
}

grep_sources() {
  base_dir="$1"
  pattern="$2"

  if [ ! -d "$base_dir" ]; then
    return 0
  fi

  find "$base_dir" \
    \( -path "*/build/*" -o -path "*/.gradle/*" \) -prune -o \
    -type f \( -name "*.kt" -o -name "*.java" \) -print |
  xargs grep -nE "$pattern" /dev/null 2>/dev/null || true
}

check_source_imports() {
  core_imports="$(grep_sources "$repo_root/core" 'com\.neo\.yourtodo\.feature\.')"
  if [ -n "$core_imports" ]; then
    record_failure "core source에서 feature 패키지를 참조합니다:
$core_imports"
  fi

  app_impl_imports="$(grep_sources "$repo_root/app/src/main" 'com\.neo\.yourtodo\.feature\.[A-Za-z0-9_]+\.impl')"
  if [ -n "$app_impl_imports" ]; then
    record_failure "app main source에서 feature impl 패키지를 직접 참조합니다:
$app_impl_imports"
  fi

  feature_api_imports="$(grep_sources "$repo_root/feature" 'com\.neo\.yourtodo\.feature\.[A-Za-z0-9_]+\.(impl|entry)')"
  if [ -n "$feature_api_imports" ]; then
    filtered_api_imports="$(printf "%s\n" "$feature_api_imports" | grep '/api/src/' || true)"
    if [ -n "$filtered_api_imports" ]; then
      record_failure "feature api source에서 impl/entry 패키지를 참조합니다:
$filtered_api_imports"
    fi
  fi
}

main() {
  check_required_files
  collect_gradle_modules
  check_module_guides
  check_gradle_dependencies
  check_source_imports

  if [ -s "$failures_tmp" ]; then
    cat >&2 <<EOF
[Product Harness] FAIL

자동화된 제품 하네스가 명확한 구조 규칙 위반을 발견했습니다.
아래 항목은 사람의 취향 판단이 아니라 저장소 정책으로 기계 검증 가능한 항목입니다.

$(cat "$failures_tmp")
EOF
    exit 1
  fi

  module_count="$(wc -l < "$modules_tmp" | tr -d ' ')"
  print_pass "모듈 가이드, Gradle 의존 방향, 금지 import 검사를 통과했습니다. modules=$module_count"
}

main "$@"
