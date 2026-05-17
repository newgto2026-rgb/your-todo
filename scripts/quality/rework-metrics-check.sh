#!/usr/bin/env sh

set -eu

repo_root="${REWORK_METRICS_REPO_ROOT:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
pr_number=""
repo_full_name=""
branch=""
strict_events=0
failures_tmp="$(mktemp)"
warnings_tmp="$(mktemp)"
threads_tmp="$(mktemp)"
comments_tmp="$(mktemp)"
commits_tmp="$(mktemp)"
body_tmp="$(mktemp)"

cleanup() {
  rm -f "$failures_tmp" "$warnings_tmp" "$threads_tmp" "$comments_tmp" "$commits_tmp" "$body_tmp"
}

trap cleanup EXIT

usage() {
  cat >&2 <<'EOF'
Usage:
  scripts/quality/rework-metrics-check.sh [--local]
  scripts/quality/rework-metrics-check.sh --strict-events
  scripts/quality/rework-metrics-check.sh --pr <number> [--repo owner/name]
EOF
}

record_failure() {
  printf "%s\n" "- $1" >> "$failures_tmp"
}

record_warning() {
  printf "%s\n" "- $1" >> "$warnings_tmp"
}

sanitize_branch() {
  printf "%s" "$1" | sed 's#[^A-Za-z0-9._-]#-#g'
}

current_branch() {
  git -C "$repo_root" symbolic-ref --quiet --short HEAD 2>/dev/null || true
}

repo_full_name_from_git() {
  if command -v gh >/dev/null 2>&1; then
    gh repo view --json nameWithOwner --jq '.nameWithOwner' 2>/dev/null || true
  fi
}

extract_summary_value() {
  label="$1"
  sed -n "s/^|[[:space:]]*$label[[:space:]]*|[[:space:]]*\`\([^\`]*\)\`[[:space:]]*|[[:space:]]*$/\1/p" "$metrics_file" | head -n 1
}

require_summary_value() {
  label="$1"
  value="$(extract_summary_value "$label")"
  if [ -z "$value" ]; then
    record_failure "Branch Summary에 '$label' 값이 없습니다."
  fi
  printf "%s" "$value"
}

require_gh() {
  if ! command -v gh >/dev/null 2>&1; then
    record_failure "PR reconciliation에는 GitHub CLI(gh)가 필요합니다."
    return 1
  fi
  return 0
}

contains_doc_ref() {
  needle="$1"
  grep -Fq "$needle" "$metrics_file"
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --local)
      shift
      ;;
    --strict-events)
      strict_events=1
      shift
      ;;
    --pr)
      pr_number="${2:-}"
      [ -n "$pr_number" ] || { usage; exit 2; }
      strict_events=1
      shift 2
      ;;
    --repo)
      repo_full_name="${2:-}"
      [ -n "$repo_full_name" ] || { usage; exit 2; }
      shift 2
      ;;
    --branch)
      branch="${2:-}"
      [ -n "$branch" ] || { usage; exit 2; }
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage
      exit 2
      ;;
  esac
done

if [ -n "$pr_number" ]; then
  require_gh || true
  if [ -z "$repo_full_name" ]; then
    repo_full_name="$(repo_full_name_from_git)"
  fi
  if [ -z "$repo_full_name" ]; then
    record_failure "PR repository를 확인할 수 없습니다. --repo owner/name을 넘기세요."
  else
    branch="$(gh pr view "$pr_number" --repo "$repo_full_name" --json headRefName --jq '.headRefName' 2>/dev/null || true)"
  fi
fi

if [ -z "$branch" ]; then
  branch="$(current_branch)"
fi

if [ -z "$branch" ]; then
  record_failure "현재 브랜치를 확인할 수 없습니다."
fi

case "$branch" in
  main|master)
    if [ -z "$pr_number" ]; then
      echo "[Rework Metrics] main/master 로컬 검사에서는 branch metrics 문서를 요구하지 않습니다."
      exit 0
    fi
    ;;
esac

safe_branch="$(sanitize_branch "$branch")"
metrics_rel_path="docs/ai-rework/branches/$safe_branch.md"
metrics_file="$repo_root/$metrics_rel_path"

if [ ! -f "$metrics_file" ]; then
  record_failure "branch metrics 문서가 없습니다: $metrics_rel_path"
else
  for section in \
    "## Branch Summary" \
    "## Rework Events" \
    "## External Event Coverage" \
    "## Non-Rework Follow-up Commits"
  do
    if ! grep -Fxq "$section" "$metrics_file"; then
      record_failure "branch metrics 문서에 필수 섹션이 없습니다: $section"
    fi
  done

  if [ "$strict_events" -eq 1 ]; then
    open_events="$(grep -nE '^[|][[:space:]]*Status[[:space:]]*[|][[:space:]]*`?(open|candidate|pending)`?[[:space:]]*[|]' "$metrics_file" || true)"
    if [ -n "$open_events" ]; then
      record_failure "open/candidate/pending rework event가 남아 있습니다:\n$open_events"
    fi
  fi
fi

if [ -n "$pr_number" ] && [ -n "$repo_full_name" ] && [ -f "$metrics_file" ] && command -v gh >/dev/null 2>&1; then
  gh pr view "$pr_number" --repo "$repo_full_name" --json body --jq '.body // ""' > "$body_tmp"

  if ! grep -Fq "## AI Rework Metrics" "$body_tmp"; then
    record_failure "PR 본문에 '## AI Rework Metrics' 섹션이 없습니다."
  fi
  if ! grep -Fq "$metrics_rel_path" "$body_tmp"; then
    record_failure "PR 본문이 branch metrics 문서를 링크하지 않습니다: $metrics_rel_path"
  fi

  rework_count="$(require_summary_value "Rework Count")"
  p0_count="$(require_summary_value "P0 Issues")"
  p1_count="$(require_summary_value "P1 Issues")"
  p2_count="$(require_summary_value "P2 Issues")"
  automation_possible="$(require_summary_value "Automation Possible Issues")"
  automation_added="$(require_summary_value "Automation Added Issues")"
  open_events_count="$(require_summary_value "Open Events")"

  [ -z "$rework_count" ] || grep -Eq "Rework count:[[:space:]]*\`?$rework_count\`?" "$body_tmp" ||
    record_failure "PR 본문의 Rework count가 branch metrics 문서와 일치하지 않습니다: $rework_count"
  [ -z "$p0_count$p1_count$p2_count" ] || grep -Eq "P0/P1/P2:[[:space:]]*\`?$p0_count/$p1_count/$p2_count\`?" "$body_tmp" ||
    record_failure "PR 본문의 P0/P1/P2 요약이 branch metrics 문서와 일치하지 않습니다: $p0_count/$p1_count/$p2_count"
  [ -z "$automation_possible" ] || grep -Eq "Automation possible:[[:space:]]*\`?$automation_possible\`?" "$body_tmp" ||
    record_failure "PR 본문의 Automation possible 값이 branch metrics 문서와 일치하지 않습니다: $automation_possible"
  [ -z "$automation_added" ] || grep -Eq "Automation added:[[:space:]]*\`?$automation_added\`?" "$body_tmp" ||
    record_failure "PR 본문의 Automation added 값이 branch metrics 문서와 일치하지 않습니다: $automation_added"
  [ -z "$open_events_count" ] || grep -Eq "Open events:[[:space:]]*\`?$open_events_count\`?" "$body_tmp" ||
    record_failure "PR 본문의 Open events 값이 branch metrics 문서와 일치하지 않습니다: $open_events_count"

  owner="$(printf "%s" "$repo_full_name" | cut -d/ -f1)"
  name="$(printf "%s" "$repo_full_name" | cut -d/ -f2)"
  gh api graphql \
    -f owner="$owner" \
    -f name="$name" \
    -F number="$pr_number" \
    -f query='query($owner:String!, $name:String!, $number:Int!) { repository(owner:$owner, name:$name) { pullRequest(number:$number) { reviewThreads(first:100) { nodes { id isResolved isOutdated } } } } }' \
    --jq '.data.repository.pullRequest.reviewThreads.nodes[] | [.id, (.isResolved|tostring), (.isOutdated|tostring)] | @tsv' \
    > "$threads_tmp"

  while IFS="$(printf '\t')" read -r thread_id is_resolved is_outdated; do
    [ -z "$thread_id" ] && continue
    if ! contains_doc_ref "$thread_id"; then
      record_failure "GitHub review thread가 branch metrics 문서에 없습니다: $thread_id resolved=$is_resolved outdated=$is_outdated"
    fi
  done < "$threads_tmp"

  gh pr view "$pr_number" --repo "$repo_full_name" --json comments \
    --jq '.comments[] | select(.body | test("(?i)(fix|missing|should|need|fail|broken|please|수정|누락|실패)")) | .id' \
    > "$comments_tmp" 2>/dev/null || true
  while IFS= read -r comment_id; do
    [ -z "$comment_id" ] && continue
    if ! contains_doc_ref "$comment_id"; then
      record_warning "액션성 PR comment가 branch metrics 문서에 없을 수 있습니다: $comment_id"
    fi
  done < "$comments_tmp"

  gh pr view "$pr_number" --repo "$repo_full_name" --json commits --jq '.commits[].oid' > "$commits_tmp"
  head_commit_oid="$(tail -n 1 "$commits_tmp" 2>/dev/null || true)"
  commit_index=0
  while IFS= read -r commit_oid; do
    [ -z "$commit_oid" ] && continue
    commit_index=$((commit_index + 1))
    if [ "$commit_index" -eq 1 ]; then
      continue
    fi
    short_oid="$(printf "%.7s" "$commit_oid")"
    if [ "$commit_oid" = "$head_commit_oid" ] && contains_doc_ref "HEAD"; then
      continue
    fi
    if ! contains_doc_ref "$commit_oid" && ! contains_doc_ref "$short_oid"; then
      record_failure "첫 PR commit 이후 follow-up commit이 branch metrics 문서에 없습니다: $short_oid"
    fi
  done < "$commits_tmp"
fi

if [ -s "$warnings_tmp" ]; then
  cat >&2 <<EOF
[Rework Metrics] WARN
$(cat "$warnings_tmp")
EOF
fi

if [ -s "$failures_tmp" ]; then
  cat >&2 <<EOF
[Rework Metrics] FAIL

중간 컨텍스트 누락 가능성을 줄이기 위해 GitHub/CI/git 외부 흔적과 branch metrics 문서를 대조했습니다.
아래 항목은 외부 이벤트가 문서에 reconciliation되지 않은 상태입니다.

$(cat "$failures_tmp")
EOF
  exit 1
fi

echo "[Rework Metrics] PASS: $metrics_rel_path"
