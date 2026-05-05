#!/usr/bin/env sh

set -eu

zero_sha="0000000000000000000000000000000000000000"
should_run_lint=0
should_run_full_lint=0
changed_files_tmp="$(mktemp)"
lint_tasks_tmp="$(mktemp)"

cleanup() {
  rm -f "$changed_files_tmp" "$lint_tasks_tmp"
}

trap cleanup EXIT

add_lint_task() {
  task="$1"
  if [ -n "$task" ]; then
    printf "%s\n" "$task" >> "$lint_tasks_tmp"
  fi
}

resolve_base_ref() {
  origin_head_ref="$(git symbolic-ref --quiet refs/remotes/origin/HEAD 2>/dev/null || true)"
  if [ -n "$origin_head_ref" ]; then
    printf "%s\n" "$origin_head_ref"
    return
  fi

  if git show-ref --verify --quiet refs/remotes/origin/main; then
    printf "%s\n" "refs/remotes/origin/main"
    return
  fi

  if git show-ref --verify --quiet refs/remotes/origin/master; then
    printf "%s\n" "refs/remotes/origin/master"
    return
  fi

  printf "%s\n" ""
}

collect_changed_files_from_base() {
  local_sha="$1"
  base_ref="$2"
  if [ -z "$base_ref" ]; then
    return 1
  fi

  base_sha="$(git merge-base "$local_sha" "$base_ref" 2>/dev/null || true)"
  if [ -z "$base_sha" ]; then
    return 1
  fi

  git diff --name-only "$base_sha..$local_sha" >> "$changed_files_tmp"
}

while read -r local_ref local_sha remote_ref remote_sha; do
  [ -z "$local_ref" ] && continue

  case "$remote_ref" in
    refs/heads/main|refs/heads/master)
      echo "[Policy] main/master로 직접 push할 수 없습니다. PR 브랜치를 사용하세요." >&2
      exit 1
      ;;
  esac

  if [ "$local_sha" != "$zero_sha" ]; then
    should_run_lint=1
  fi

  if [ "$local_sha" = "$zero_sha" ]; then
    continue
  fi

  if [ "$remote_sha" = "$zero_sha" ]; then
    base_ref="$(resolve_base_ref)"
    if ! collect_changed_files_from_base "$local_sha" "$base_ref"; then
      should_run_full_lint=1
      echo "[Quality Gate] 기준 브랜치를 찾을 수 없어 전체 lint로 폴백합니다." >&2
    fi
  else
    if git cat-file -e "${remote_sha}^{commit}" 2>/dev/null; then
      git diff --name-only "$remote_sha..$local_sha" >> "$changed_files_tmp"
    else
      base_ref="$(resolve_base_ref)"
      if ! collect_changed_files_from_base "$local_sha" "$base_ref"; then
        should_run_full_lint=1
        echo "[Quality Gate] 원격 기준 SHA를 로컬에서 찾지 못해 전체 lint로 폴백합니다." >&2
      fi
    fi
  fi
done

if [ "$should_run_lint" -eq 1 ]; then
  sorted_changed_files_tmp="$(mktemp)"
  sort -u "$changed_files_tmp" > "$sorted_changed_files_tmp"
  while IFS= read -r changed_file; do
    [ -z "$changed_file" ] && continue

    case "$changed_file" in
      .github/workflows/*|gradle/*|gradlew|gradlew.bat|settings.gradle|settings.gradle.kts|build.gradle|build.gradle.kts|buildSrc/*|scripts/git-hooks/*|.husky/*)
        should_run_full_lint=1
        ;;
      app/*)
        add_lint_task ":app:lintDebug"
        ;;
      core/*)
        core_module="$(printf "%s" "$changed_file" | cut -d/ -f2)"
        case "$core_module" in
          data|database|datastore|designsystem|network|ui)
            add_lint_task ":core:${core_module}:lintDebug"
            ;;
          model|domain|testing)
            should_run_full_lint=1
            ;;
        esac
        ;;
      feature/*/*)
        feature_name="$(printf "%s" "$changed_file" | cut -d/ -f2)"
        feature_module="$(printf "%s" "$changed_file" | cut -d/ -f3)"
        case "$feature_module" in
          api|impl|entry)
            add_lint_task ":feature:${feature_name}:${feature_module}:lintDebug"
            ;;
          *)
            should_run_full_lint=1
            ;;
        esac
        ;;
    esac
  done < "$sorted_changed_files_tmp"
  rm -f "$sorted_changed_files_tmp"

  if [ "$should_run_full_lint" -eq 1 ]; then
    echo "[Quality Gate] 변경 영향이 커서 전체 lint를 실행합니다..."
    ./gradlew --stacktrace lint
  elif [ -s "$lint_tasks_tmp" ]; then
    lint_tasks="$(sort -u "$lint_tasks_tmp" | tr '\n' ' ')"
    echo "[Quality Gate] 변경 모듈 lint를 실행합니다: $lint_tasks"
    ./gradlew --stacktrace $lint_tasks
  else
    echo "[Quality Gate] lint 대상 변경이 없어 lint를 건너뜁니다."
  fi
fi
