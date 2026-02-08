# GitMini — Phase 3: UI 구현 계획서

> **작성일**: 2026-02-08  
> **상태**: Phase 1~2 완료, Phase 3 착수 대기  
> **이전 Phase 결과**: 133개 단위 테스트 PASSED

---

## 1. Phase 1~2 완료 상태 요약

### Phase 1 — 환경 설정 + 프로젝트 뼈대 (완료)
- JavaFX 앱 진입점 (`GitMiniApp`, `Launcher`)
- AtlantaFX Primer Dark 테마 적용
- 설정 인프라 (`ConfigManager`, `AppConfig`, `TokenManager`)
- 예외 체계 (`GitMiniException` 계층)
- `EventBus` (싱글톤, 발행/구독, FX 스레드 안전장치)
- `TaskManager` (JavaFX Task 래핑, 비동기 실행)
- 기본 FXML 레이아웃 (사이드바 + 메인 콘텐츠 + 상태바)
- 모델 클래스 8종

### Phase 2 — Git 연동 핵심 (완료, 133개 테스트 PASSED)

**생성된 클래스:**

| 패키지 | 클래스 | 역할 |
|--------|--------|------|
| `git/` | `GitResult` | git 실행 결과 record (exitCode, stdout, stderr, command, durationMs) |
| `git/` | `GitExecutor` | ProcessBuilder 래퍼 — LC_ALL=C, 타임아웃(30초/120초), stdout/stderr 분리, git 설치/버전 확인(2.23.0+), Command Log 자동 기록(리스너 패턴) |
| `git/` | `GitCommandBuilder` | Fluent API — `GitCommandBuilder.git().status().porcelain().build()` |
| `git/parser/` | `StatusParser` | `git status --porcelain` → `List<FileChange>` |
| `git/parser/` | `BranchParser` | `git branch -vv` → `List<BranchInfo>`, `rev-list` → ahead/behind |
| `git/parser/` | `LogParser` | `git log --format="%H%x00%an%x00%s%x00%aI"` → `List<CommitInfo>` |
| `git/parser/` | `DiffParser` | `git diff` → `List<DiffEntry>` (파일→hunk→line 구조화) |
| `model/` | `DiffEntry`, `DiffHunk`, `DiffLine` | diff 구조화 모델 |
| `service/` | `GitService` | status, add, addAll, unstage, commit, amend, push, pull, fetch, branches, currentBranch, checkout, createBranch, aheadBehind, log, diffUnstaged, diffStaged, diffFile, discard |

**수정된 기존 파일:**

| 파일 | 수정 내용 |
|------|----------|
| `EventBus` | `enableFxThreadDispatch()` 추가 — 백그라운드 스레드에서 publish해도 FX 스레드에서 실행 |
| `GitMiniApp` | `getTaskManager()`, `getConfigManager()` static getter 추가 + EventBus FX 안전장치 활성화 |
| `build.gradle.kts` | Mockito 의존성 추가 (mockito-core, mockito-junit-jupiter 5.11.0) |

**테스트 (133개 전체 PASSED):**

| 테스트 클래스 | 수 | 범위 |
|--------------|-----|------|
| ConfigManagerTest | 5 | Phase 1 |
| TokenManagerTest | 5 | Phase 1 |
| EventBusTest | 7 | Phase 1 |
| GitCommandBuilderTest | 23 | 명령어 조립 검증 |
| GitExecutorTest | 20 | 실행, 타임아웃, Command Log, 버전 비교 |
| StatusParserTest | 13 | porcelain 출력 파싱 (staged/unstaged/untracked/rename) |
| BranchParserTest | 15 | branch -vv 파싱, ahead/behind |
| LogParserTest | 11 | log 파싱 (날짜, 특수문자, 엣지 케이스) |
| DiffParserTest | 10 | diff 파싱 (hunk, 여러 파일, rename) |
| GitServiceTest | 24 | 모든 서비스 메서드 (Mockito로 GitExecutor 모킹) |

---

## 2. 현재 소스 현황 (main 28개 + test 10개)

### main 소스

| 패키지 | 클래스 |
|--------|--------|
| 진입점 (2) | `GitMiniApp`, `Launcher` |
| controller (1) | `MainController` (뼈대) |
| model (8) | `Repository`, `FileChange`, `BranchInfo`, `CommitInfo`, `GitCommandRecord`, `DiffEntry`, `DiffHunk`, `DiffLine` |
| git (3) | `GitExecutor`, `GitCommandBuilder`, `GitResult` |
| git/parser (4) | `StatusParser`, `BranchParser`, `LogParser`, `DiffParser` |
| service (1) | `GitService` |
| config (3) | `AppConfig`, `ConfigManager`, `TokenManager` |
| event (3) | `EventBus`, `RepoStatusChangedEvent`, `GitOperationCompletedEvent` |
| async (1) | `TaskManager` |
| exception (4) | `GitMiniException`, `GitExecutionException`, `GitParseException`, `ConfigException` |

### 리소스

| 파일 | 역할 |
|------|------|
| `fxml/main.fxml` | 사이드바 + 메인 콘텐츠 + 상태바 뼈대 |
| `css/app.css` | 커스텀 스타일 (AtlantaFX 위에 덧씌움) |
| `logback.xml` | 로깅 설정 |

### 의존성 방향
```
controller → service → git / config
    ↓            ↓          ↓
  model        model      model
    ↑            ↑
  event        async
```

---

## 3. Phase 2 코드 리뷰에서 나온 Phase 3 주의사항

### 3.1 GitService 인스턴스 관리
- `GitMiniApp`에서 `GitExecutor` → `GitService`를 생성하고, Controller에 주입해야 한다.
- `GitMiniApp`에 이미 `getTaskManager()`, `getConfigManager()` static getter가 있으므로 같은 패턴으로 `getGitService()` 추가.

### 3.2 비동기 패턴
- Controller에서 GitService를 직접 호출하면 UI 스레드 블로킹.
- 반드시 `TaskManager.run()`으로 감싸서 호출해야 한다.

```java
taskManager.run(
    () -> gitService.status(repoPath),     // 백그라운드
    changes -> updateFileList(changes),     // UI 스레드 (성공)
    error -> showError(error)               // UI 스레드 (실패)
);
```

### 3.3 EventBus 구독 생명주기
- Controller가 EventBus를 구독하면, 화면 해제 시 반드시 `unsubscribe` 해야 메모리 누수 방지.

### 3.4 Command Log UI 연동
- `executor.addCommandListener(record -> ...)` 로 실시간 피드백.
- 리스너는 백그라운드 스레드에서 호출되므로 `Platform.runLater`로 UI 갱신 필요.

### 3.5 Git 설치 확인 타이밍
- `GitMiniApp.start()`에서 `executor.isGitVersionSupported()` 호출.
- 실패 시 안내 다이얼로그 표시.

### 3.6 EventBus FX 스레드 안전장치
- 이미 활성화됨 (`GitMiniApp.start()`에서 `EventBus.getInstance().enableFxThreadDispatch(true)`).
- TaskManager 콜백(FX 스레드)에서 publish하면 안전.
- 백그라운드에서 publish해도 자동으로 FX 스레드로 디스패치됨.

---

## 4. Phase 3 구현 목록 (DESIGN.md 기준)

| 작업 | 상세 |
|------|------|
| 사이드바 | 레포 목록, 상태 아이콘, 선택 시 메인 영역 갱신 |
| 레포 추가/제거 | 폴더 선택 다이얼로그, 드래그 & 드롭 |
| 파일 변경 영역 | Unstaged / Staged 두 리스트, Stage/Unstage 버튼 |
| 커밋 영역 | 메시지 입력, Commit, Amend 체크박스 |
| 액션 바 | Push, Pull, Fetch + ahead/behind 표시 |
| 브랜치 | 현재 브랜치 표시, 드롭다운 전환, 새 브랜치 생성 |
| Diff 뷰어 | 파일 클릭 시 inline diff 표시 |
| 커밋 히스토리 | 최근 커밋 목록 (탭 또는 토글) |
| Command Log 패널 | 접이식 패널, 명령어 + 시간 + 성공/실패 |
| 상태바 | 현재 작업 상태, 브랜치, ahead/behind |
| 비동기 피드백 | 로딩 인디케이터, 버튼 비활성화, 토스트 알림 |
| 키보드 단축키 | Ctrl+Enter(Commit), Ctrl+Shift+P(Push) 등 |
| 컨텍스트 메뉴 | 파일 우클릭 → Discard / Open in Explorer |
| 자동 Fetch | 백그라운드 주기적 fetch |
| **목표** | **실제 사용 가능한 Git 클라이언트. 일상 Git 작업 모두 가능** |

---

## 5. Phase 3 Step 분해 계획

### Step 1: 인프라 준비 — GitService 주입 + Git 설치 확인

| 항목 | 내용 |
|------|------|
| 작업 | `GitMiniApp`에서 `GitExecutor` → `GitService` 생성, static getter 추가 |
| 작업 | `start()`에서 git 버전 확인 → 실패 시 Alert 다이얼로그 |
| 검증 | 컴파일 확인 (`gradlew build`) |

### Step 2: RepositoryManager 서비스

| 항목 | 내용 |
|------|------|
| 새 파일 | `service/RepositoryManager.java` |
| 역할 | 레포 목록 관리 (등록/제거/유효성 검사/상태 갱신) |
| 동기화 | `AppConfig.repoPaths`와 자동 동기화 |
| 상태 조회 | `GitService`로 branch, changes, ahead/behind 조회 → `Repository` 모델 갱신 |
| 검증 | **단위 테스트** (`RepositoryManagerTest`) + 컴파일 확인 |

### Step 3: 메인 FXML 레이아웃 재설계

| 항목 | 내용 |
|------|------|
| 작업 | `main.fxml` 전면 재구성 (DESIGN.md 와이어프레임 기준) |
| 사이드바 | 레포 목록 ListView + 하단 추가/Clone 버튼 |
| 메인 콘텐츠 | 브랜치/액션바 → 파일 변경 영역(Unstaged/Staged) → Diff → 커밋 영역 |
| Command Log | 하단 접이식 TitledPane |
| 상태바 | 브랜치 + ahead/behind + 상태 메시지 |
| 검증 | 컴파일 확인 |

### Step 4: 사이드바 구현

| 항목 | 내용 |
|------|------|
| Custom ListCell | 레포 이름, 브랜치, 변경 파일 수, 상태 아이콘 (✓/⚠) |
| 레포 추가 | DirectoryChooser → `RepositoryManager.add()` |
| 레포 제거 | 컨텍스트 메뉴 우클릭 |
| 레포 선택 | 메인 콘텐츠 영역에 상세 정보 로드 (비동기) |
| 검증 | 컴파일 확인 |

### Step 5: 파일 변경 영역

| 항목 | 내용 |
|------|------|
| Unstaged ListView | 변경 파일 목록 (M/A/D/? 아이콘 + Custom ListCell) |
| Staged ListView | 스테이징된 파일 목록 |
| Stage 버튼 | 개별(→) + 전체(→→) |
| Unstage 버튼 | 개별(←) + 전체(←←) |
| 파일 선택 | diff 뷰어와 연동 |
| 검증 | 컴파일 확인 |

### Step 6: Diff 뷰어

| 항목 | 내용 |
|------|------|
| 표시 방식 | Inline diff — 추가(초록), 삭제(빨강), 컨텍스트(기본) |
| 구현 | ScrollPane > VBox > styled Labels (monospace) |
| 연동 | 파일 변경 영역에서 파일 선택 시 자동 표시 |
| staged/unstaged | 선택된 파일이 어느 리스트에 있느냐에 따라 `diffStaged`/`diffUnstaged` 자동 전환 |
| 검증 | 컴파일 확인 |

### Step 7: 커밋 영역

| 항목 | 내용 |
|------|------|
| 커밋 메시지 | TextArea + placeholder ("커밋 메시지를 입력하세요") |
| Commit 버튼 | staged 파일 없거나 메시지 비어있으면 비활성화 |
| Amend 체크박스 | 직전 커밋 수정 모드 |
| 단축키 | `Ctrl+Enter` = Commit 실행 |
| 검증 | 컴파일 확인 |

### Step 8: 액션 바 + 브랜치

| 항목 | 내용 |
|------|------|
| Push 버튼 | ahead 숫자 표시 (↑N) |
| Pull 버튼 | behind 숫자 표시 (↓N) |
| Fetch 버튼 | 원격 상태 동기화 |
| 브랜치 ComboBox | 현재 브랜치 표시 + 전환 가능 |
| 새 브랜치 | [+] 버튼 → TextInputDialog |
| 검증 | 컴파일 확인 |

### Step 9: 커밋 히스토리

| 항목 | 내용 |
|------|------|
| 접이식 | TitledPane — "커밋 히스토리" |
| ListView | hash(7자리), author, message, 상대 시간 |
| 조회 | 레포 선택/작업 완료 시 최근 N개 로드 |
| 검증 | 컴파일 확인 |

### Step 10: Command Log 패널

| 항목 | 내용 |
|------|------|
| 위치 | 메인 콘텐츠 하단, 접이식 TitledPane |
| ListView | 시각 (HH:mm:ss), 명령어, 성공/실패 아이콘, 소요 시간 |
| 실시간 | `executor.addCommandListener()` → `Platform.runLater`로 UI 갱신 |
| 명령어 복사 | 더블클릭 또는 컨텍스트 메뉴로 클립보드 복사 |
| 검증 | 컴파일 확인 |

### Step 11: 상태바 + 비동기 피드백 + 토스트

| 항목 | 내용 |
|------|------|
| 상태바 | 왼쪽: 작업 상태 메시지, 오른쪽: 브랜치 + ↑N ↓N |
| ProgressIndicator | Git 작업 중 상태바에 스피너 표시 |
| 버튼 비활성화 | 작업 진행 중 관련 버튼 disable |
| 토스트 알림 | 성공/실패 피드백 — 상태바 메시지 변경 + 3초 후 자동 복원 |
| 검증 | 컴파일 확인 |

### Step 12: 컨텍스트 메뉴 + 키보드 단축키

| 항목 | 내용 |
|------|------|
| 파일 컨텍스트 메뉴 | Discard Changes, Stage/Unstage, Open in Explorer, Copy Path |
| Discard 확인 | 위험한 작업이므로 확인 다이얼로그 표시 |
| 키보드 단축키 | `Ctrl+Enter`(Commit), `Ctrl+Shift+P`(Push), `Ctrl+Shift+L`(Pull) |
| 검증 | 컴파일 확인 |

### Step 13: 드래그 & 드롭 + 자동 Fetch

| 항목 | 내용 |
|------|------|
| 드래그 & 드롭 | 사이드바에 폴더를 드롭하면 레포 추가 |
| 자동 Fetch | `ScheduledExecutorService`로 주기적 fetch |
| 주기 설정 | `AppConfig.autoFetchIntervalMinutes` 값 사용 |
| EventBus 연동 | fetch 결과 → `RepoStatusChangedEvent` → UI 자동 갱신 |
| 최종 검증 | 컴파일 + 기존 133개 테스트 전체 PASSED 확인 |

---

## 6. 전체 흐름 요약

```
Step 1~2:   서비스 계층 준비 (인프라 + RepositoryManager)
Step 3:     FXML 레이아웃 뼈대 재설계
Step 4~10:  UI 영역별 구현 (사이드바 → 파일 → diff → 커밋 → 액션 → 히스토리 → 커맨드로그)
Step 11~13: UX 마무리 (피드백, 단축키, 드래그드롭, 자동fetch)
```

### 검증 전략
- 매 Step마다 `gradlew build` (컴파일 확인) 수행
- Step 2에서 `RepositoryManagerTest` 단위 테스트 작성
- Step 13 완료 후 기존 133개 테스트 전체 PASSED 확인
- Phase 3 완료 후: AI 자체 코드 리뷰 리포트 + 사용자 체크포인트 체크리스트

### 사용자 체크포인트 (Phase 3 완료 후 확인 항목)

```
□ gradlew build 성공하는가?
□ gradlew test 성공하는가? (133개 이상 PASSED)
□ gradlew run 실행 시 창이 정상적으로 뜨는가?
□ 사이드바에 레포를 추가할 수 있는가? (폴더 선택)
□ 드래그 & 드롭으로 레포를 추가할 수 있는가?
□ 사이드바에서 레포를 선택하면 메인 영역이 갱신되는가?
□ Unstaged/Staged 파일 목록이 표시되는가?
□ Stage/Unstage 버튼이 동작하는가?
□ 파일 선택 시 Diff가 표시되는가? (추가=초록, 삭제=빨강)
□ 커밋 메시지 입력 후 Commit이 동작하는가?
□ Amend 체크박스가 동작하는가?
□ Push/Pull/Fetch 버튼이 동작하는가?
□ ahead/behind 숫자가 표시되는가?
□ 브랜치 전환이 가능한가?
□ 새 브랜치 생성이 가능한가?
□ 커밋 히스토리가 표시되는가?
□ Command Log에 실행된 명령어가 실시간으로 표시되는가?
□ 상태바에 브랜치/상태 정보가 표시되는가?
□ Git 작업 중 로딩 인디케이터가 보이는가?
□ Git 작업 중 버튼이 비활성화되는가?
□ Ctrl+Enter로 커밋이 되는가?
□ 파일 우클릭 시 컨텍스트 메뉴가 나오는가?
□ 자동 Fetch가 백그라운드에서 동작하는가?
□ 앱 종료 후 재시작하면 레포 목록이 유지되는가?
```

---

## 7. 핵심 아키텍처 결정 (Phase 3)

### Controller 구조
```
MainController (main.fxml)
├── 사이드바 영역 직접 관리
├── 상태바 직접 관리
└── 메인 콘텐츠는 선택된 레포에 따라 내용 갱신
```

> DESIGN.md에서는 `RepoDetailController`와 `RepoCardController`를 별도로 설계했으나,
> 실제 구현 시 복잡도와 FXML 구조에 따라 단일 `MainController`로 통합하거나
> 분리할 수 있다. Step 3~4에서 결정.

### 비동기 패턴 (모든 Git 작업)
```java
// Controller에서의 표준 패턴
private void refreshStatus() {
    setLoading(true);
    taskManager.run(
        () -> gitService.status(currentRepoPath),
        changes -> {
            updateFileList(changes);
            setLoading(false);
        },
        error -> {
            showError("상태 조회 실패: " + error.getMessage());
            setLoading(false);
        }
    );
}
```

### EventBus 이벤트 흐름
```
[Git 작업 완료]
  → EventBus.publish(GitOperationCompletedEvent)
  → MainController 구독: Command Log 갱신
  
[레포 상태 변경]
  → EventBus.publish(RepoStatusChangedEvent)
  → MainController 구독: 사이드바 레포 상태 갱신, 파일 목록 갱신
```

### Command Log 연동
```java
// GitMiniApp.start()에서 리스너 등록
executor.addCommandListener(record -> {
    Platform.runLater(() -> {
        EventBus.getInstance().publish(
            new GitOperationCompletedEvent(...)
        );
    });
});
```
