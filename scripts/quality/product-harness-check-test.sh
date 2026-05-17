#!/usr/bin/env sh

set -eu

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
guard_script="$script_dir/product-harness-check.sh"
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

write_valid_repo() {
  repo="$1"
  mkdir -p "$repo/app/src/main/java/com/neo/yourtodo/app"
  mkdir -p "$repo/core/domain/src/main/java/com/neo/yourtodo/core/domain"
  mkdir -p "$repo/feature/todo/api/src/main/java/com/neo/yourtodo/feature/todo/api"
  mkdir -p "$repo/feature/todo/impl/src/main/java/com/neo/yourtodo/feature/todo/impl"
  mkdir -p "$repo/feature/todo/entry/src/main/java/com/neo/yourtodo/feature/todo/entry"

  cat > "$repo/settings.gradle.kts" <<'EOF'
include(
    ":app",
    ':core:domain'
)
include(":feature:todo:api", ":feature:todo:impl")
include ( ':feature:todo:entry' )
EOF

  cat > "$repo/AGENTS.md" <<'EOF'
| Gradle 모듈 | 가이드 경로 | 책임 |
|---|---|---|
| `:app` | `app/AGENTS.md` | 앱 셸 |
| `:core:domain` | `core/domain/AGENTS.md` | 도메인 |
| `:feature:todo:api` | `feature/todo/api/AGENTS.md` | 계약 |
| `:feature:todo:impl` | `feature/todo/impl/AGENTS.md` | 구현 |
| `:feature:todo:entry` | `feature/todo/entry/AGENTS.md` | 진입 |
EOF

  for guide in \
    app/AGENTS.md \
    core/domain/AGENTS.md \
    feature/todo/api/AGENTS.md \
    feature/todo/impl/AGENTS.md \
    feature/todo/entry/AGENTS.md
  do
    mkdir -p "$repo/$(dirname "$guide")"
    echo "# guide" > "$repo/$guide"
  done

  cat > "$repo/app/build.gradle.kts" <<'EOF'
dependencies {
    implementation(project(":feature:todo:api")); implementation(project(
        ":feature:todo:entry"
    ))
    implementation(project(path = ":core:domain"))
}
EOF

  cat > "$repo/core/domain/build.gradle.kts" <<'EOF'
dependencies {
}
EOF

  cat > "$repo/feature/todo/api/build.gradle.kts" <<'EOF'
dependencies {
}
EOF

  cat > "$repo/feature/todo/impl/build.gradle.kts" <<'EOF'
dependencies {
    implementation(project(":feature:todo:api")); implementation(project(
        ':core:domain'
    ))
}
EOF

  cat > "$repo/feature/todo/entry/build.gradle.kts" <<'EOF'
dependencies {
    implementation(project(":feature:todo:api"))
    implementation(project(":feature:todo:impl"))
}
EOF

  cat > "$repo/app/src/main/java/com/neo/yourtodo/app/App.kt" <<'EOF'
package com.neo.yourtodo.app

import com.neo.yourtodo.feature.todo.api.TodoFeatureEntry

class App(private val entry: TodoFeatureEntry)
EOF

  cat > "$repo/feature/todo/api/src/main/java/com/neo/yourtodo/feature/todo/api/TodoFeatureEntry.kt" <<'EOF'
package com.neo.yourtodo.feature.todo.api

interface TodoFeatureEntry
EOF

  cat > "$repo/feature/todo/impl/src/main/java/com/neo/yourtodo/feature/todo/impl/TodoFeatureEntryImpl.kt" <<'EOF'
package com.neo.yourtodo.feature.todo.impl

import com.neo.yourtodo.feature.todo.api.TodoFeatureEntry

class TodoFeatureEntryImpl : TodoFeatureEntry
EOF
}

run_guard() {
  repo="$1"
  PRODUCT_HARNESS_REPO_ROOT="$repo" "$guard_script" >/tmp/product-harness.out 2>/tmp/product-harness.err
}

valid_repo="$tmp_dir/valid"
mkdir -p "$valid_repo"
write_valid_repo "$valid_repo"
run_guard "$valid_repo" || fail "valid architecture should pass"
pass "allows the intended module and source architecture"

missing_guide_repo="$tmp_dir/missing-guide"
cp -R "$valid_repo" "$missing_guide_repo"
rm "$missing_guide_repo/feature/todo/impl/AGENTS.md"
if run_guard "$missing_guide_repo"; then
  fail "missing module AGENTS.md should fail"
fi
pass "denies modules without AGENTS.md"

core_dep_repo="$tmp_dir/core-dep"
cp -R "$valid_repo" "$core_dep_repo"
cat > "$core_dep_repo/core/domain/build.gradle.kts" <<'EOF'
dependencies {
    implementation(project(
        ":feature:todo:api"
    )); implementation(project(":core:domain"))
}
EOF
if run_guard "$core_dep_repo"; then
  fail "core -> feature Gradle dependency should fail"
fi
pass "denies core to feature Gradle dependencies even when multiple projects share one line"

app_impl_repo="$tmp_dir/app-impl"
cp -R "$valid_repo" "$app_impl_repo"
cat > "$app_impl_repo/app/src/main/java/com/neo/yourtodo/app/App.kt" <<'EOF'
package com.neo.yourtodo.app

import com.neo.yourtodo.feature.todo.impl.TodoFeatureEntryImpl

class App(private val entry: TodoFeatureEntryImpl)
EOF
if run_guard "$app_impl_repo"; then
  fail "app importing feature impl should fail"
fi
pass "denies app source imports of feature impl packages"

api_impl_repo="$tmp_dir/api-impl"
cp -R "$valid_repo" "$api_impl_repo"
cat > "$api_impl_repo/feature/todo/api/src/main/java/com/neo/yourtodo/feature/todo/api/TodoFeatureEntry.kt" <<'EOF'
package com.neo.yourtodo.feature.todo.api

import com.neo.yourtodo.feature.todo.impl.TodoFeatureEntryImpl

interface TodoFeatureEntry {
    fun impl(): TodoFeatureEntryImpl
}
EOF
if run_guard "$api_impl_repo"; then
  fail "api importing feature impl should fail"
fi
pass "denies feature api source imports of impl packages"

no_source_repo="$tmp_dir/no-source"
cp -R "$valid_repo" "$no_source_repo"
rm -rf "$no_source_repo/app/src" "$no_source_repo/core/domain/src" "$no_source_repo/feature/todo/api/src" "$no_source_repo/feature/todo/impl/src"
run_guard "$no_source_repo" || fail "empty source directories should not hang or fail"
pass "allows modules without Kotlin or Java source files"
