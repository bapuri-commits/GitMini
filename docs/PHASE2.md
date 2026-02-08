# Phase 2: Git 연동 핵심

> **상태**: 완료  
> **결과물**: UI 없이 서비스 계층이 완전 동작. 단위 테스트로 검증 (133개 PASSED)

---

## 1. 계획

### 목표 (DESIGN.md §7)

| 작업 | 상세 |
|------|------|
| GitExecutor | ProcessBuilder 래퍼 — LC_ALL=C, 타임아웃, stdout/stderr 분리, exit code |
| GitCommandBuilder | fluent API — `GitCommandBuilder.status().porcelain().build()` |
| 파서들 | StatusParser, BranchParser, LogParser, DiffParser |
| GitService | status, add, commit, push, pull, fetch, branch, checkout, log, diff, discard, amend |
| TaskManager | JavaFX Task 래핑 — 비동기 실행 + UI 스레드 복귀 + 에러 콜백 |
| EventBus | 발행/구독 구현 |
| Git 설치 확인 | 앱 시작 시 `git --version` 체크 |
| Command Log 기록 | 모든 git 명령 실행을 GitCommandRecord로 기록 |

### 결과물 정의

- UI 없이 서비스 계층이 완전 동작한다.
- 단위 테스트로 검증한다.

---

## 2. 과정

### 생성된 클래스

| 패키지 | 클래스 | 역할 |
|--------|--------|------|
| `git/` | `GitResult` | git 실행 결과 record (exitCode, stdout, stderr, command, durationMs) |
| `git/` | `GitExecutor` | ProcessBuilder 래퍼 — LC_ALL=C, 타임아웃(30초/120초), stdout/stderr 분리, git 2.23.0+ 확인, Command Log 리스너 |
| `git/` | `GitCommandBuilder` | Fluent API — `GitCommandBuilder.git().status().porcelain().build()` |
| `git/parser/` | `StatusParser` | `git status --porcelain` → `List<FileChange>` |
| `git/parser/` | `BranchParser` | `git branch -vv` → `List<BranchInfo>`, rev-list → ahead/behind |
| `git/parser/` | `LogParser` | `git log --format=...` → `List<CommitInfo>` |
| `git/parser/` | `DiffParser` | `git diff` → `List<DiffEntry>` (파일→hunk→line) |
| `model/` | `DiffEntry`, `DiffHunk`, `DiffLine` | diff 구조화 모델 |
| `service/` | `GitService` | status, add, addAll, unstage, commit, amend, push, pull, fetch, branches, currentBranch, checkout, createBranch, aheadBehind, log, diffUnstaged, diffStaged, diffFile, discard |

### 수정된 기존 파일

| 파일 | 수정 내용 |
|------|----------|
| `EventBus` | `enableFxThreadDispatch()` — 백그라운드에서 publish 시 FX 스레드로 디스패치 |
| `GitMiniApp` | `getTaskManager()`, `getConfigManager()` static getter + EventBus FX 안전장치 활성화 |
| `build.gradle.kts` | Mockito 의존성 추가 (mockito-core, mockito-junit-jupiter 5.11.0) |

### Step 단위 진행

- GitCommandBuilder → GitExecutor → 파서들(Status, Branch, Log, Diff) → GitService 순으로 구현.
- 각 단계마다 단위 테스트 작성 후 `gradlew test`로 검증.

---

## 3. 종료

### 검증

- `gradlew build` 성공.
- `gradlew test` — **133개 테스트 전체 PASSED.**

| 테스트 클래스 | 수 | 범위 |
|--------------|-----|------|
| ConfigManagerTest | 5 | Phase 1 |
| TokenManagerTest | 5 | Phase 1 |
| EventBusTest | 7 | Phase 1 |
| GitCommandBuilderTest | 23 | 명령어 조립 |
| GitExecutorTest | 20 | 실행, 타임아웃, Command Log, 버전 비교 |
| StatusParserTest | 13 | porcelain 파싱 |
| BranchParserTest | 15 | branch -vv, ahead/behind |
| LogParserTest | 11 | log 파싱 |
| DiffParserTest | 10 | diff 파싱 |
| GitServiceTest | 24 | 서비스 메서드 (GitExecutor 모킹) |

### 완료 요약

- Git CLI 연동 및 서비스 계층 완성. Command Log 기록·git 버전 확인 포함.
- Phase 3 UI에서 사용할 비동기·EventBus 패턴이 준비됨.

### 다음 Phase 시 주의사항

- **GitService 인스턴스**: `GitMiniApp`에서 생성 후 Controller에 주입. `getGitService()` static getter 추가.
- **비동기**: Controller에서는 반드시 `TaskManager.run()`으로 Git 작업 감싸기.
- **EventBus 구독**: Controller 해제 시 `unsubscribe` 필수 (메모리 누수 방지).
- **Command Log UI**: `executor.addCommandListener` → `Platform.runLater`로 UI 갱신.
- **Git 설치 확인**: `GitMiniApp.start()`에서 `executor.isGitVersionSupported()` 호출, 실패 시 Alert.
