# Phase 3: UI 구현

> **상태**: Step 9 완료, Step 10~13 대기  
> **이전 Phase**: Phase 1~2 완료, 133개 단위 테스트 PASSED  
> **현재 테스트**: 153개 PASSED

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

### Step 분해 (13단계)

```
Step 1~2:   서비스 계층 준비 (인프라 + RepositoryManager)
Step 3:     FXML 레이아웃 뼈대 재설계
Step 4~10:  UI 영역별 구현 (사이드바 → 파일 → diff → 커밋 → 액션 → 히스토리 → 커맨드로그)
Step 11~13: UX 마무리 (피드백, 단축키, 드래그드롭, 자동fetch)
```

---

## 2. 과정

### Step 1: GitService 주입 + Git 설치 확인

- `GitMiniApp`: GitExecutor/GitService 생성, `getGitService()` static getter.
- Git 버전 확인 실패 시 Alert 후 `Platform.exit()`.
- Command Log 리스너 등록.

### Step 2: RepositoryManager 서비스

- `RepositoryManager.java`: 캐시 기반, add/remove/refreshStatus/findByPath.
- 유효 git 레포: `.git` 존재 + `git status` 성공.
- `RepositoryManagerTest`: 22개 테스트.

### Step 1~2 버그 수정

- GitCommandRecord에 repoPath 필드 추가.
- RepositoryManager 캐시 도입, Windows 대소문자 호환.

### Step 3: main.fxml 레이아웃 전면 재설계

- 사이드바, StackPane 전환, 액션 바, 파일 변경 영역, Diff 뷰어, 커밋, 히스토리, Command Log, 상태바.
- 단일 MainController 결정.

### Step 4: 사이드바 구현

- `RepoListCell.java`: 레포 이름, 브랜치, 상태 아이콘, ahead/behind, 원격 없음 표시.
- 레포 추가(DirectoryChooser), 제거(컨텍스트 메뉴), 선택 시 상세 갱신.

### Step 5: 파일 변경 영역

- `FileChangeListCell.java`: 변경 유형 아이콘, 파일명, 툴팁.
- Stage/Unstage 개별/전체, `refreshRepoDetail()`로 전체 갱신.

### Step 6: Diff 뷰어

- `DiffRenderer.java`: DiffEntry → styled Labels.
- 비동기 diff 로드, untracked 파일 처리.

### Step 7: 커밋 영역

- Commit/Amend, Ctrl+Enter 단축키.

### Step 8: 액션 바 + 브랜치 + 긴급 수정 3건

- Push/Pull/Fetch, 브랜치 ComboBox 전환/생성.
- **긴급 수정 1**: 큰 레포 UI 프리즈 방지 — 파일 목록 최대 1000개 + Unstaged/Staged 검색 필드.
- **긴급 수정 2**: Push/Pull/Fetch 에러 한국어 매핑 (`mapRemoteErrorMessage`).
- **긴급 수정 3**: `Repository.hasRemote`, `GitService.hasRemote()`, 사이드바 브랜치에 "(원격 없음)" 표시.

### Step 8 추가 보완

- **에러 다이얼로그 위치**: `getMainWindow()` 도입 → 메인 창 중앙 표시. 모든 Alert/다이얼로그 통일.
- **에러 멘트 개선**: 원격 없을 때 "이 레포에는 원격 저장소가 없습니다. Push하려면 터미널에서 'git remote add origin <URL>' 로…"
- **사이드바 브랜치에 원격 없음**: `main (원격 없음)` 형태.
- **브랜치 전환 실패 시 ComboBox 무한루프 방지**: 실패 콜백에서 `updatingBranchComboBox` 플래그로 감싸서 `onBranchChanged` 재트리거 차단.
- **브랜치 전환 에러 메시지**: `mapBranchErrorMessage()` 추가 — uncommitted changes, pathspec 에러, 긴 메시지 300자 축약.
- **레포 추가**: 여러 폴더 연속 선택 가능 (취소하면 종료), 마지막 경로 기억(`lastBrowsedDir`).
- **config.json 깨짐 방어**: `ConfigManager.save()` 에서 원자적 저장 (tmp 파일 → ATOMIC_MOVE), `AppConfig.validate()` 에서 NaN 방어.

### Step 9: 커밋 히스토리

- `COMMIT_HISTORY_MAX=50`, `RepoDetailData.commitHistory`.
- `commitHistoryListView` CellFactory: short hash(7자), 메시지, 날짜.
- 레포 선택 시 초기화, 상세 갱신 시 최근 50개 표시.

### Step 10: Command Log 패널

- **CellFactory**: 시각(HH:mm:ss), 성공/실패 아이콘(✓/✗), 명령어, 소요시간(ms) 한 줄 표시. 실패 시 빨간색.
- **실시간 갱신**: `GitMiniApp`에서 `GitCommandRecord`를 EventBus로 직접 발행 → MainController가 `GitCommandRecord.class` 구독 → `commandLogListView.getItems().add(0, record)` 로 최신이 위에.
- **최대 500건** 유지 (초과 시 오래된 것 제거).
- **우클릭 컨텍스트 메뉴**: "명령어 복사" / "출력 복사" — ClipboardContent 사용.
- **코드 감사 반영**: 클립보드 복사 `ClipboardContent`로 교체, 시각 표시 `DateTimeFormatter` 사용.
- **Push upstream 자동 설정**: upstream 미설정 시 `git push -u origin <브랜치>` 자동 실행, 에러 메시지 "원격 없음" vs "upstream 없음" 구분.

### Step 11: 상태바 + 비동기 피드백 + 토스트

- **로딩 인디케이터**: `setStatus("...중...")`처럼 "..."으로 끝나는 메시지 → `progressIndicator` 자동 표시. 완료 시 자동 숨김.
- **`progressIndicator` managed 바인딩**: 숨김 시 상태바에서 공간 차지하지 않음.
- **토스트 효과**: 완료/에러 메시지 표시 후 **5초 뒤 자동으로 "✓ Ready"** 복원. 중간에 새 메시지가 오면 이전 타이머 무효화 (`statusFadeGeneration`).
- **기존 `setStatus()` 호환**: 모든 기존 호출이 자동으로 로딩/완료를 구분. 코드 변경 최소화.

### Step 12: 컨텍스트 메뉴 + 키보드 단축키

- **Unstaged 우클릭**: "변경 취소 (Discard)" — 확인 다이얼로그 후 `gitService.discard()`, "탐색기에서 열기" — `Desktop.open(dir)`.
- **Staged 우클릭**: "Unstage" — `onUnstage()` 호출, "탐색기에서 열기".
- **키보드 단축키**: Ctrl+Enter(Commit), Ctrl+Shift+P(Push), Ctrl+Shift+L(Pull), Ctrl+Shift+F(Fetch).

### Step 13: 드래그 & 드롭 + 자동 Fetch

- **드래그 & 드롭**: 사이드바에 폴더를 드롭하면 레포 추가. 여러 폴더 동시 드롭 가능. `isDirectory` 필터.
- **자동 Fetch**: `AppConfig.autoFetchIntervalMinutes`(기본 5분) 간격으로 선택된 레포를 백그라운드 fetch. 원격 없는 레포는 스킵. 실패 시 무시(사용자 방해 없음).

---

## Phase 3 워크플로

- **Step이 끝날 때마다**: AI가 체크리스트를 응답에 보여준다.
- **사용자**: 강도 높은 감사 → 수정 요청.
- **반영 후**: 사용자가 직접 실행·체크하고 결과를 AI에게 알려준다.

---

## 남은 Step

| Step | 내용 | 상태 |
|------|------|------|
| 11 | 상태바 + 비동기 피드백 + 토스트 | 완료 |
| 12 | 컨텍스트 메뉴 + 키보드 단축키 | 완료 |
| 13 | 드래그 & 드롭 + 자동 Fetch | 완료 |

---

## 3. 종료

미완료. Phase 3 검증 완료 후 기록 예정.
