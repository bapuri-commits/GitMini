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

**검증**: `gradlew build` 성공. `RepositoryManagerTest` 10개 단위 테스트 PASSED.

**코드 리뷰 (Step 2)**
- **변경 파일**: `RepositoryManager.java` (신규), `RepositoryManagerTest.java` (신규).
- **설계 준수**: Q5 반영 — add 시 `.git` + `git status` 검증. AppConfig.repoPaths와 동기화 후 ConfigManager.save() 호출.
- **테스트**: ConfigManager·GitService 모킹, @TempDir로 유효/무효 경로, add 중복·예외·remove·refreshStatus·getRepoPaths 검증.

---

## 3. 종료

미완료. Phase 3 검증 완료 후 AI가 다음을 기록한다.

- **검증**: `gradlew build` / `gradlew test` 결과, 사용자 체크포인트 결과.
- **완료 요약**: 구현된 UI·연동·결정 사항 요약.
- **다음 Phase 주의사항**: Phase 4(GitHub API) 시 참고할 점.
