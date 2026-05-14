#!/usr/bin/env sh

set -eu

remote="${LATEST_MAIN_REMOTE:-origin}"
main_branch="${LATEST_MAIN_BRANCH:-main}"
remote_main="refs/remotes/${remote}/${main_branch}"
target_ref="${1:-HEAD}"

if [ "${SKIP_LATEST_MAIN_GUARD:-}" = "1" ]; then
  echo "[Policy] 최신 ${remote}/${main_branch} 검사 우회: SKIP_LATEST_MAIN_GUARD=1" >&2
  exit 0
fi

branch="$(git symbolic-ref --quiet --short HEAD 2>/dev/null || true)"
if [ -z "$branch" ]; then
  echo "[Policy] detached HEAD에서는 작업할 수 없습니다. 전용 브랜치에서 작업하세요." >&2
  exit 1
fi

echo "[Policy] 최신 ${remote}/${main_branch} 기준을 확인합니다..."
if ! git fetch --quiet "$remote" "$main_branch"; then
  echo "[Policy] ${remote}/${main_branch} 갱신에 실패했습니다. 네트워크와 remote 설정을 확인하세요." >&2
  exit 1
fi

if ! git show-ref --verify --quiet "$remote_main"; then
  echo "[Policy] ${remote}/${main_branch} 참조를 찾을 수 없습니다." >&2
  exit 1
fi

if ! git merge-base --is-ancestor "$remote_main" "$target_ref"; then
  echo "[Policy] 검사 대상이 최신 ${remote}/${main_branch} 위에 있지 않습니다: ${target_ref}" >&2
  echo "[Policy] 다음 명령으로 갱신한 뒤 다시 시도하세요:" >&2
  echo "  git fetch ${remote} ${main_branch}" >&2
  echo "  git rebase ${remote}/${main_branch}" >&2
  exit 1
fi
