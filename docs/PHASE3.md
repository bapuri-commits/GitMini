# Phase 3: UI 구현

> **상태**: 착수 대기  
> **이전 Phase**: Phase 1~2 완료, 133개 단위 테스트 PASSED

---

## 1. 계획

### 목표 (DESIGN.md §7)

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

### 결과물 정의

- 실제 사용 가능한 Git 클라이언트. 일상 Git 작업 모두 가능.

### Phase 2 코드 리뷰 — Phase 3 주의사항

| 항목 | 내용 |
|------|------|
| GitService 인스턴스 | `GitMiniApp`에서 생성 후 Controller 주입. `getGitService()` static getter 추가. |
| 비동기 | Controller에서 Git 작업은 반드시 `TaskManager.run()`으로 감싸기. |
| EventBus 구독 | 화면 해제 시 `unsubscribe` 필수 (메모리 누수 방지). |
| Command Log UI | `executor.addCommandListener` → `Platform.runLater`로 UI 갱신. |
| Git 설치 확인 | `GitMiniApp.start()`에서 `executor.isGitVersionSupported()`, 실패 시 Alert. |
| EventBus FX | 이미 활성화됨. 백그라운드 publish 시 자동 FX 스레드 디스패치. |

### Step 분해 (13단계) — 각 Step: 구현 → 빌드 검증 → 코드 리뷰

| Step | 내용 | 검증 |
|------|------|------|
| 1 | GitService 주입 + Git 설치 확인 (실패 시 Alert 후 앱 종료) | `gradlew build` → 코드 리뷰 |
| 2 | RepositoryManager 서비스 (레포 목록, AppConfig 동기화) | 단위 테스트 + build → 코드 리뷰 |
| 3 | main.fxml 재설계 (사이드바/메인/Command Log/상태바) | build → 코드 리뷰 |
| 4 | 사이드바 (ListCell, 레포 추가/제거/선택) | build → 코드 리뷰 |
| 5 | 파일 변경 영역 (Unstaged/Staged ListView, Stage/Unstage) | build → 코드 리뷰 |
| 6 | Diff 뷰어 (inline diff, staged/unstaged 연동) | build → 코드 리뷰 |
| 7 | 커밋 영역 (TextArea, Commit, Amend, Ctrl+Enter) | build → 코드 리뷰 |
| 8 | 액션 바 + 브랜치 (Push/Pull/Fetch, ComboBox, 새 브랜치) | build → 코드 리뷰 |
| 9 | 커밋 히스토리 (TitledPane, ListView) | build → 코드 리뷰 |
| 10 | Command Log 패널 (접이식, 실시간, 복사) | build → 코드 리뷰 |
| 11 | 상태바 + 비동기 피드백 + 토스트 | build → 코드 리뷰 |
| 12 | 컨텍스트 메뉴 + 키보드 단축키 | build → 코드 리뷰 |
| 13 | 드래그 & 드롭 + 자동 Fetch | build + 133개 테스트 PASSED → 코드 리뷰 |

### 흐름 요약

```
Step 1~2:   서비스 계층 준비 (인프라 + RepositoryManager)
Step 3:     FXML 레이아웃 뼈대 재설계
Step 4~10:  UI 영역별 구현 (사이드바 → 파일 → diff → 커밋 → 액션 → 히스토리 → 커맨드로그)
Step 11~13: UX 마무리 (피드백, 단축키, 드래그드롭, 자동fetch)
```

### 아키텍처 결정 (착수 전 Q&A 반영)

- **Controller**: **MainController + RepoDetailController** (메인 영역 별도 FXML/컨트롤러).
- **비동기 패턴**: 모든 Git 작업을 `TaskManager.run(작업, 성공콜백, 실패콜백)`으로 실행.
- **EventBus**: `GitOperationCompletedEvent` → Command Log 갱신, `RepoStatusChangedEvent` → 사이드바/파일 목록 갱신.
- **Command Log**: `GitMiniApp.start()`에서 `executor.addCommandListener` → `Platform.runLater` → EventBus publish.

### 사용자 체크포인트 (Phase 3 완료 후)

- gradlew build / test 성공, run 시 창·사이드바·레포 추가(폴더/드래그드롭)·선택 시 갱신·Unstaged/Staged·Stage/Unstage·Diff·Commit/Amend·Push/Pull/Fetch·ahead/behind·브랜치 전환·새 브랜치·커밋 히스토리·Command Log 실시간·상태바·로딩·버튼 비활성화·Ctrl+Enter·컨텍스트 메뉴·자동 Fetch·재시작 시 레포 목록 유지. (상세 목록은 METHODOLOGY §5 Phase 3 체크포인트 참고.)

---

## 2. 과정

### Step 1: GitService 주입 + Git 설치 확인 (실패 시 Alert 후 앱 종료)

**구현**
- `GitMiniApp`: `GitExecutor`, `GitService` 생성 및 `getGitService()` static getter 추가.
- `start()`: 설정·TaskManager·EventBus 이후 `GitExecutor` 생성 → `isGitVersionSupported()` 실패 시 Alert 표시 후 `Platform.exit()` 호출하여 앱 종료 (Q4 반영).
- `start()`: `GitService` 생성 후 Command Log용 `executor.addCommandListener` 등록 — 리스너에서 `Platform.runLater`로 `GitOperationCompletedEvent` 발행.
- `stop()`: `gitServiceInstance = null` 정리.

**검증**: `gradlew build` 성공 (로컬 Java 17 환경에서 실행).

**코드 리뷰 (Step 1)**
- **변경 파일**: `GitMiniApp.java` — GitExecutor/GitService 생성, `getGitService()` 추가, Git 버전 확인 실패 시 Alert 후 `Platform.exit()` (Q4 반영), Command Log 리스너 등록, `stop()`에서 `gitServiceInstance` 정리.
- **설계 준수**: Phase 2 주의사항 반영 — GitService는 앱에서 1회 생성·주입, Command Log는 `Platform.runLater`로 FX 스레드에서 이벤트 발행.
- **일관성**: TaskManager/ConfigManager와 동일한 static getter 패턴으로 `getGitService()` 제공.
- **정리**: `toOperationEvent(GitCommandRecord)`로 Command Log용 이벤트 변환, 메시지에 시각·소요시간 포함.

### Step 2: RepositoryManager 서비스 (레포 목록, AppConfig 동기화)

**구현**
- `service/RepositoryManager.java`: ConfigManager + GitService 주입. `getRepositories()`, `add(Path)`, `remove(Path)`, `refreshStatus(Repository)`, `getRepoPaths()`.
- 유효한 git 레포 (Q5): 디렉터리 존재, `.git` 존재, `git status` 한 번 실행 성공 시에만 등록.
- `getRepositories()`: 설정의 경로 목록을 읽어 각 경로에 대해 Repository 생성 후 `refreshStatus()`로 branch·변경 수·ahead/behind·최근 커밋 갱신. 무효 경로는 건너뜀.
- `add()`: 중복 경로는 저장하지 않음. `remove()`: 경로 정규화 후 목록에서 제거하고 설정 저장.

**검증**: `gradlew build` 성공. `RepositoryManagerTest` 단위 테스트 PASSED.

**코드 리뷰 (Step 2)**
- **변경 파일**: `RepositoryManager.java` (신규), `RepositoryManagerTest.java` (신규).
- **설계 준수**: Q5 반영 — add 시 `.git` + `git status` 검증. AppConfig.repoPaths와 동기화 후 ConfigManager.save() 호출.
- **테스트**: ConfigManager·GitService 모킹, @TempDir로 유효/무효 경로, add 중복·예외·remove·refreshStatus·getRepoPaths 검증.

### Step 1~2 코드 리뷰 후 수정 (데스크톱 복귀 후)

**BUG-1: GitCommandRecord에 repoPath 누락**
- `GitCommandRecord`에 `repoPath` 필드 추가 (어떤 레포에서 실행된 명령인지 식별).
- `GitExecutor.recordCommand()` → `recordCommand(result, workingDir)`로 서명 변경.
- `GitMiniApp.toOperationEvent()` → `record.repoPath()` 사용.
- `GitExecutorTest` → repoPath 관련 어서션 추가.

**BUG-2: RepositoryManager 매 호출마다 파일 I/O**
- `List<Repository>` 캐시 도입. `getRepositories()`는 캐시 반환.
- `loadRepositories()` / `refreshAll()`로 명시적 로드/갱신 분리.
- `add()` / `remove()` 시 캐시와 설정을 함께 갱신.
- 경로 비교 시 `equalsIgnoreCase()` 적용 (Windows 대소문자 호환).
- `findByPath()` 메서드 추가.
- `getRepositories()` 반환값을 `Collections.unmodifiableList()`로 읽기 전용화.

**DESIGN-1: GitMiniApp에 RepositoryManager 통합**
- `GitMiniApp`에 `RepositoryManager` 생성 + `getRepositoryManager()` static getter 추가.
- `stop()`에서 `repositoryManagerInstance = null` 정리.

**테스트 보완 (RepositoryManagerTest)**
- 기존 11개 → 22개로 확대.
- 추가 시나리오: 캐시 동작 검증, 읽기 전용 반환, 유효/무효 혼합, git status 실패한 레포 건너뜀, remove 존재하지 않는 경로, 커밋 없는 새 레포, refreshAll, findByPath.

**검증**: `gradlew clean test` → 전체 PASSED.

### Step 3: main.fxml 레이아웃 전면 재설계

**구현**
- `main.fxml`: DESIGN.md 와이어프레임에 맞게 전면 재설계.
  - **사이드바**: `ListView<Repository>` + 추가/Clone 버튼 (기존 유지).
  - **메인 콘텐츠**: `StackPane`으로 welcomePane / repoDetailPane 전환.
  - **액션 바**: 레포 이름, `ComboBox<String>` 브랜치 선택, 새 브랜치 +, Fetch/Pull/Push, ahead/behind.
  - **파일 변경 영역**: Unstaged `ListView<FileChange>` | Stage/Unstage 버튼 | Staged `ListView<FileChange>`. 수직 `SplitPane`으로 Diff 뷰어와 리사이즈 가능.
  - **Diff 뷰어**: `ScrollPane` > `VBox diffContent`. CSS 클래스로 추가/삭제/컨텍스트 줄 스타일 준비.
  - **커밋 영역**: `TextArea` (promptText), `CheckBox` Amend, `Button` Commit (accent 스타일).
  - **커밋 히스토리**: `TitledPane` 접이식, `ListView<CommitInfo>`.
  - **Command Log**: `TitledPane` 접이식, `ListView<GitCommandRecord>` (monospace).
  - **상태바**: `ProgressIndicator` (숨김), 상태 메시지, 브랜치, 변경 파일 수.

- `MainController.java`: 전면 재작성.
  - 모든 FXML 바인딩 (28개 필드) — 타입 지정 (`ListView<Repository>`, `ListView<FileChange>`, `ComboBox<String>` 등).
  - `showWelcome()` / `showRepoDetail()` 화면 전환 메서드.
  - 핸들러 스텁 13개 — 로그 + 상태바 메시지 (실제 로직은 Step 4~12에서 구현).

- `app.css`: 12개 스타일 섹션 추가.
  - 액션 바, 파일 변경 영역, Diff 줄 스타일 (added/removed/context/hunk), 커밋 영역, 하단 패널, 상태바.

**설계 결정**
- **단일 MainController**: DESIGN.md에서는 `RepoDetailController` 분리를 계획했으나, FXML include 없이 단일 컨트롤러로 통합. 코드가 비대해지면 Step 4~10 진행 중 분리 가능.
- **StackPane 전환**: welcomePane과 repoDetailPane을 `visible` 속성으로 전환. StackPane이 두 자식을 겹치므로, visible=false인 쪽은 클릭 이벤트도 받지 않아 자연스러움.

**검증**: `gradlew clean test` → 153개 테스트 전체 PASSED. 컴파일 성공.

### Step 4: 사이드바 구현

**구현**
- `controller/component/RepoListCell.java` (신규): Custom ListCell.
  - 레포 이름 (bold), 브랜치명, 상태 아이콘 (✓ clean / ⚠N dirty), ahead/behind (↑N ↓N).
  - 우클릭 컨텍스트 메뉴: "레포 제거" → 확인 다이얼로그 후 `RepositoryManager.remove()`.
- `MainController.java` 전면 업데이트:
  - `initialize()`: Custom ListCell 설정, 선택 리스너 등록, 레포 목록 비동기 로드.
  - `loadRepoList()`: `TaskManager.run()` → `RepositoryManager.getRepositories()` → ListView 갱신.
  - `onRepoSelected()`: 선택 시 `showRepoDetail()` + 레포 상태 비동기 갱신.
  - `refreshRepoDetail()`: 백그라운드에서 status/branches/aheadBehind 조회 → UI 스레드에서 파일 목록, 브랜치 ComboBox, ahead/behind, 상태바 갱신.
  - `onAddRepo()`: `DirectoryChooser` → `RepositoryManager.add()` (비동기) → 목록 재로드.
  - `removeRepo()`: 확인 Alert → `RepositoryManager.remove()` → 목록 재로드.
  - `RepoDetailData` 내부 record: 백그라운드 결과 묶음.
  - `setStatus()`, `updateStatusBar()`, `updateAheadBehind()`, `showErrorAlert()` 유틸리티.
- `app.css`: 사이드바 레포 셀 스타일 7개 추가 (`.repo-cell-*`, `.status-clean`, `.status-dirty`).

**설계 결정**
- **비동기 패턴 일관성**: 모든 Git 작업은 `TaskManager.run()`으로 감쌈. UI 스레드 블로킹 없음.
- **레포 목록 갱신**: add/remove 후 `loadRepoList()`로 전체 재로드. 캐시 기반이므로 성능 무관.
- **선택 복원**: `loadRepoList()` 후 이전 선택된 레포를 path로 찾아 복원.

**검증**: `gradlew clean test` → 153개 테스트 전체 PASSED.

### Step 5: 파일 변경 영역 (Unstaged/Staged + Stage/Unstage)

**구현**
- `controller/component/FileChangeListCell.java` (신규): Custom ListCell.
  - 변경 유형 아이콘: M(주황), A(초록), D(빨강), R(파랑), ?(흐림).
  - 파일명 표시 (경로의 마지막 구성요소). 전체 경로는 툴팁.
  - CSS 클래스 누적 방지 (`removeAll` 후 `add`).
- `MainController.java` 추가:
  - `initialize()`: FileChangeListCell 설정, 파일 선택 이벤트 리스너 등록.
  - `onStageAll()`: `gitService.addAll()` 비동기 → `refreshRepoDetail()`.
  - `onStage()`: 선택된 파일 1개 `gitService.add()` 비동기.
  - `onUnstage()`: 선택된 파일 1개 `gitService.unstage()` 비동기.
  - `onUnstageAll()`: 모든 staged 파일 `gitService.unstage()` 비동기.
  - `onFileSelected(file, staged)`: Diff 라벨 갱신, 반대쪽 리스트 선택 해제. Step 6에서 Diff 로드 구현.
- `app.css`: 파일 변경 셀 스타일 (`.file-change-cell`, `.file-type-icon`, `.file-path`, `.type-*`).

**설계 결정**
- Stage/Unstage 후 `refreshRepoDetail()`로 전체 상태 갱신. 파일 목록, 브랜치, ahead/behind 모두 최신화.
- 파일 선택 시 unstaged/staged 중 한 쪽만 선택 활성화 (반대쪽 자동 해제).

**검증**: `gradlew clean test` → 153개 테스트 전체 PASSED.

---

## 3. 종료

미완료. Phase 3 검증 완료 후 AI가 다음을 기록한다.

- **검증**: `gradlew build` / `gradlew test` 결과, 사용자 체크포인트 결과.
- **완료 요약**: 구현된 UI·연동·결정 사항 요약.
- **다음 Phase 주의사항**: Phase 4(GitHub API) 시 참고할 점.
