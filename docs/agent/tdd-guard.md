# TDD Guard Hook

`scripts/codex-hooks/tdd-guard.sh`는 Codex의 `edit`, `write`, `patch`, `apply_patch` 실행 전에 호출하기 위한 guard 스크립트다.

## 정책

- `*/src/main/*.kt`, `*/src/main/*.java` 변경은 운영 코드 변경으로 본다.
- 운영 코드를 바꾸려면 같은 Gradle 모듈의 테스트 변경이 먼저 있어야 한다.
- 테스트 변경으로 인정하는 경로는 `<module>/src/test/...`, `<module>/src/androidTest/...`, `<module>/src/sharedTest/...`다.
- 테스트 파일 자체, 문서, 스크립트, Gradle 파일 변경은 이 guard가 막지 않는다.
- 이미 커밋된 기존 테스트만으로는 통과하지 않는다. 현재 작업트리 또는 같은 patch 입력 안에 테스트 변경이 있어야 한다.

## 직접 실행

```sh
scripts/codex-hooks/tdd-guard.sh edit feature/todo/impl/src/main/java/com/neo/yourtodo/feature/todo/impl/ui/TodoListViewModel.kt
```

patch 입력도 처리한다.

```sh
scripts/codex-hooks/tdd-guard.sh apply_patch < /tmp/change.patch
```

## Codex hook 연결 예시

Codex 설정의 pre-tool hook에서 `edit`, `write`, `patch`, `apply_patch` 도구 실행 전에 아래 스크립트를 호출한다.

```sh
<repo>/scripts/codex-hooks/tdd-guard.sh
```

도구가 대상 파일 경로를 인자로 넘길 수 있다면 아래처럼 넘기는 것을 권장한다.

```sh
<repo>/scripts/codex-hooks/tdd-guard.sh edit "$TARGET_FILE"
```

긴급 우회가 필요하면 명시적으로 환경 변수를 설정한다.

```sh
TDD_GUARD_BYPASS=1 scripts/codex-hooks/tdd-guard.sh edit app/src/main/java/Example.kt
```

## 검증

```sh
scripts/codex-hooks/tdd-guard-test.sh
```
