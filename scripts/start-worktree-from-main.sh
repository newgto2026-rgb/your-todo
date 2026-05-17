#!/usr/bin/env sh

set -eu

remote="${LATEST_MAIN_REMOTE:-origin}"
main_branch="${LATEST_MAIN_BRANCH:-main}"

usage() {
  echo "Usage: $0 <branch-name> [worktree-path]" >&2
  echo "Example: $0 codex/my-feature ../worktrees/my-feature" >&2
}

if [ "$#" -lt 1 ] || [ "$#" -gt 2 ]; then
  usage
  exit 2
fi

branch="$1"
worktree_path="${2:-}"

case "$branch" in
  main|master)
    echo "[Policy] main/master는 작업 브랜치로 사용할 수 없습니다." >&2
    exit 1
    ;;
  "")
    usage
    exit 2
    ;;
esac

if git show-ref --verify --quiet "refs/heads/${branch}"; then
  echo "[Policy] 이미 존재하는 브랜치입니다: ${branch}" >&2
  exit 1
fi

echo "[Policy] 최신 ${remote}/${main_branch}을 가져옵니다..."
git fetch "$remote" "$main_branch"

remote_main="refs/remotes/${remote}/${main_branch}"
if ! git show-ref --verify --quiet "$remote_main"; then
  echo "[Policy] ${remote}/${main_branch} 참조를 찾을 수 없습니다." >&2
  exit 1
fi

if [ -z "$worktree_path" ]; then
  repo_name="$(basename "$(git rev-parse --show-toplevel)")"
  safe_branch="$(printf "%s" "$branch" | tr '/: ' '---')"
  worktree_path="../${safe_branch}/${repo_name}"
fi

echo "[Policy] ${remote}/${main_branch} 기준으로 worktree를 생성합니다..."
git worktree add -b "$branch" "$worktree_path" "$remote_main"
if [ -x "$worktree_path/scripts/quality/rework-metrics-init.sh" ]; then
  (cd "$worktree_path" && scripts/quality/rework-metrics-init.sh "$branch")
fi
echo "[Policy] 준비 완료: ${worktree_path}"
