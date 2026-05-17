#!/usr/bin/env sh

set -eu

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"

branch="$(git symbolic-ref --quiet --short HEAD 2>/dev/null || true)"
if [ "$branch" = "main" ] || [ "$branch" = "master" ]; then
  echo "[Policy] main/master에서 직접 commit할 수 없습니다." >&2
  exit 1
fi

"$script_dir/ensure-latest-main.sh"
"$script_dir/../quality/product-harness-check.sh"
