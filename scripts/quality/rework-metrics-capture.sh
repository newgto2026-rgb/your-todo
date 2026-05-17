#!/usr/bin/env sh

set -eu

repo_root="${REWORK_METRICS_REPO_ROOT:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
pr_number=""
repo_full_name=""
severity="${REWORK_METRICS_DEFAULT_SEVERITY:-P1}"
attribution="${REWORK_METRICS_DEFAULT_ATTRIBUTION:-AI}"

usage() {
  cat >&2 <<'EOF'
Usage:
  scripts/quality/rework-metrics-capture.sh --pr <number> [--repo owner/name]

Captures missing GitHub review threads as open rework events in the current branch metrics document.
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --pr)
      pr_number="${2:-}"
      [ -n "$pr_number" ] || { usage; exit 2; }
      shift 2
      ;;
    --repo)
      repo_full_name="${2:-}"
      [ -n "$repo_full_name" ] || { usage; exit 2; }
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

if [ -z "$pr_number" ]; then
  usage
  exit 2
fi

if ! command -v gh >/dev/null 2>&1; then
  echo "[Rework Metrics] gh CLI가 필요합니다." >&2
  exit 1
fi

if [ -z "$repo_full_name" ]; then
  repo_full_name="$(gh repo view --json nameWithOwner --jq '.nameWithOwner')"
fi

branch="$(gh pr view "$pr_number" --repo "$repo_full_name" --json headRefName --jq '.headRefName')"
safe_branch="$(printf "%s" "$branch" | sed 's#[^A-Za-z0-9._-]#-#g')"
metrics_file="$repo_root/docs/ai-rework/branches/$safe_branch.md"

"$script_dir/rework-metrics-init.sh" "$branch" >/dev/null

owner="$(printf "%s" "$repo_full_name" | cut -d/ -f1)"
name="$(printf "%s" "$repo_full_name" | cut -d/ -f2)"
threads_tmp="$(mktemp)"

cleanup() {
  rm -f "$threads_tmp"
}

trap cleanup EXIT

gh api graphql \
  -f owner="$owner" \
  -f name="$name" \
  -F number="$pr_number" \
  -f query='query($owner:String!, $name:String!, $number:Int!) { repository(owner:$owner, name:$name) { pullRequest(number:$number) { reviewThreads(first:100) { nodes { id isResolved isOutdated path line comments(first:1) { nodes { url author { login } databaseId body } } } } } } }' \
  --jq '.data.repository.pullRequest.reviewThreads.nodes[] | [.id, (.isResolved|tostring), (.isOutdated|tostring), (.path // ""), ((.line // "")|tostring), (.comments.nodes[0].url // ""), (.comments.nodes[0].author.login // ""), ((.comments.nodes[0].databaseId // "")|tostring)] | @tsv' \
  > "$threads_tmp"

count=0
while IFS="$(printf '\t')" read -r thread_id is_resolved is_outdated path line url author database_id; do
  [ -z "$thread_id" ] && continue
  if grep -Fq "$thread_id" "$metrics_file"; then
    continue
  fi

  count=$((count + 1))
  event_id="R$(date +%Y%m%d%H%M%S)-$count"
  cat >> "$metrics_file" <<EOF

### $event_id - Review thread: ${path:-unknown}

| Field | Value |
|---|---|
| Status | \`open\` |
| Detected Phase | \`review\` |
| Feedback Source | \`${author:-unknown}\` |
| Severity | \`$severity\` |
| Attribution | \`$attribution\` |
| Root Cause Category | \`TBD\` |
| Automation Possible | \`TBD\` |
| Automation Added | \`TBD\` |
| Fix Scope | \`TBD\` |
| Fix Size | \`TBD\` |
| Rework Commit | \`TBD\` |
| Verification | \`TBD\` |

#### External Refs

- Review Thread: \`$thread_id\`
- Review Comment: \`${database_id:-TBD}\`
- Review URL: $url
- Review Path: \`${path:-TBD}:${line:-TBD}\`
- Resolved At Capture: \`$is_resolved\`
- Outdated At Capture: \`$is_outdated\`

#### Feedback Summary

TBD

#### Fix Summary

TBD

#### Lesson

TBD
EOF
done < "$threads_tmp"

echo "[Rework Metrics] captured=$count file=${metrics_file#"$repo_root"/}"
