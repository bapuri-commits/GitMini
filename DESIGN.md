# GitMini — 설계 문서

> **상태**: 설계 재검토 완료 (학습용 → 상용 전환)  
> **최종 수정**: 2026-02-07

---

## 1. 프로젝트 개요

### 한 줄 설명
> **미니멀 Git 데스크톱 클라이언트** — 매일 실제로 사용하는 상용 수준 Git 도구

### 방향 전환
이 프로젝트는 원래 "Java 학습 + 포트폴리오"를 목적으로 설계되었으나, 방향이 전환되었다:

- ❌ 학습용 토이 프로젝트
- ❌ 포트폴리오 어필용
- ✅ 내가 매일 실제로 사용할 상용 수준의 Git 클라이언트

### 왜 만드는가? (Problem)

```
기존 도구의 문제:
├── GitHub Desktop: 기능이 많고 무거움
├── 터미널 Git: 단순 작업에도 명령어를 일일이 입력
├── VS Code Git: IDE에 종속됨
└── GitKraken/Fork: 유료이거나 무거움

내가 원하는 것:
├── 간단한 Git 작업을 빠르게 (stage, commit, push, pull, branch)
├── 여러 레포 상태를 한눈에
├── 가볍고 빠른 실행
├── 실행된 Git 명령어를 투명하게 확인
└── 브라우저 안 켜고 바로 사용
```

### 핵심 가치

| 가치 | 설명 |
|------|------|
| **미니멀리즘** | GitHub Desktop의 20% 기능, 하지만 일상 작업의 95%를 커버 |
| **다중 레포 대시보드** | 여러 레포 상태를 한 화면에서 확인 + 즉시 전환 |
| **Command Log (투명성)** | 모든 작업에서 실행되는 Git 명령어를 로그로 표시. 파워유저를 위한 투명성 도구 |
| **원클릭 워크플로우** | 자주 쓰는 패턴(Commit+Push 등)을 한 번에 |

### 개발 철학

1. **상용 품질 코드**: 프로덕션에 나가도 부끄럽지 않은 코드
   - 적절한 예외 처리, 일관된 컨벤션, 의미 있는 로깅, 방어적 프로그래밍

2. **견고한 설계**: 기능을 "동작하게"가 아니라 "제대로" 만든다
   - SOLID 원칙, 계층 분리, 인터페이스 기반 설계, 적절한 디자인 패턴

3. **사용자 경험 우선**: 개발자 도구라도 UX를 신경 쓴다
   - 응답성 (UI 스레드 블로킹 금지, 모든 Git/네트워크 작업 비동기)
   - 피드백 (로딩 인디케이터, 성공/실패, 명확한 에러 메시지)
   - 엣지 케이스 대응 (네트워크 끊김, 잘못된 경로, 권한 문제 등)

4. **점진적 완성**: 각 Phase가 끝나면 그 자체로 안정적으로 동작해야 한다

---

## 2. 기술 스택

| 구분 | 기술 | 선택 이유 |
|------|------|----------|
| 언어 | **Java 17** | LTS, 크로스 플랫폼 |
| GUI | **JavaFX + FXML** | Java GUI 표준, UI/로직 분리, Scene Builder 지원 |
| UI 테마 | **AtlantaFX (Primer)** | 현대적 CSS 테마, Light/Dark 모드 내장, 적용 한 줄 |
| UI 디자인 | **Scene Builder** | WYSIWYG FXML 편집기 |
| Git 연동 | **ProcessBuilder (git CLI)** | Command Log와 시너지, 사용자 git 설정 그대로 활용 |
| GitHub API | **java.net.http.HttpClient** | Java 표준 라이브러리, 추가 의존성 없음 |
| JSON | **Gson** | 가볍고 단순, 설정 파일 읽기/쓰기에 충분 |
| 로깅 | **SLF4J + Logback** | Java 로깅 사실상 표준 |
| 빌드 | **Gradle (Kotlin DSL)** | 2023년부터 Gradle 공식 기본값, 타입 안전, IDE 자동완성 |
| 패키징 | **jpackage** | Java 표준, 독립 실행 파일(.exe) 생성 |
| 테스트 | **JUnit 5** | Java 테스트 표준 |

### 기술 선택 상세

#### ProcessBuilder (git CLI) 선택
```
장점:
├── Command Log에 실제 실행 명령어를 그대로 표시
├── 사용자의 기존 git 설정 (credential helper, SSH, hooks) 활용
├── 구현이 단순하고 직관적
└── JGit의 SSH/GPG 호환성 문제 회피

방어 조치 (상용 수준):
├── LC_ALL=C 환경 변수 강제 → 출력 언어 통일 (파싱 안정성)
├── 타임아웃 설정 → 네트워크 작업 무한 대기 방지
├── git 설치/버전 확인 → 앱 시작 시 검증
├── stdout/stderr 분리 → 에러 메시지 정확한 포착
└── exit code 기반 성공/실패 판단

전제 조건:
└── 사용자 PC에 git 설치 필요
```

#### AtlantaFX 선택
```
선택 이유:
├── JavaFX 기본 테마(Modena)는 2013년 디자인 → 촌스러움
├── Primer/Nord/Cupertino/Dracula 테마 제공 (각각 Light/Dark)
├── 적용 한 줄: Application.setUserAgentStylesheet(...)
├── 다크 모드가 공짜로 따라옴 → 별도 구현 불필요
└── 추가 의존성 없음 (CSS만)
```

#### Gradle Kotlin DSL 선택
```
선택 이유:
├── 2023년부터 Gradle 공식 기본값
├── 정적 타입 → IDE 자동완성, 리팩터링 완전 지원
├── Groovy DSL 대비 가독성/유지보수성 우수
└── 새 프로젝트에서 Groovy DSL을 선택할 이유 없음
```

---

## 3. 기능 명세

### 3.1 MVP (Must Have) — 핵심 기능

#### 레포지토리 관리
- [ ] 로컬 레포 목록 관리 (등록/제거)
- [ ] 레포 상태 표시 (변경 파일 수, 브랜치, 마지막 커밋)
- [ ] 새 레포 Clone (URL → 로컬 경로 선택 → Clone)
- [ ] 폴더 드래그 & 드롭으로 레포 추가
- [ ] 탐색기에서 열기 / 터미널에서 열기

#### 기본 Git 작업
- [ ] Stage (add) — 파일 선택 후 스테이징
- [ ] Unstage — 스테이징 취소
- [ ] Commit — 메시지 입력 후 커밋
- [ ] Amend Commit — 직전 커밋 수정
- [ ] Push — 원격에 푸시
- [ ] Pull — 원격에서 가져오기
- [ ] Fetch — 원격 상태 확인
- [ ] Discard — 파일 변경 되돌리기 (작업 디렉토리 복원)

#### 브랜치
- [ ] 브랜치 목록 표시
- [ ] 브랜치 생성
- [ ] 브랜치 전환 (checkout/switch)
- [ ] Branch ahead/behind 표시 (원격 대비 ↑N ↓N)

#### Diff & 히스토리
- [ ] Diff 보기 — 파일 선택 시 변경 내용 표시 (inline diff)
- [ ] 커밋 히스토리 — 최근 커밋 목록 (hash, author, message, date)

#### Command Log ⭐ (핵심 차별점)
- [ ] 모든 Git 작업의 실행 명령어를 시간순 로그로 표시
- [ ] 명령어 복사 기능 (터미널에서 재현 가능)
- [ ] 성공/실패 상태 표시
- [ ] 하단 접이식(collapsible) 패널로 배치

#### 자동 Fetch
- [ ] 백그라운드 주기적 Fetch (간격 설정 가능)
- [ ] Fetch 결과로 ahead/behind 자동 갱신

#### 인증
- [ ] GitHub Personal Access Token 설정 (GitHub API용)
- [ ] Git push/pull 인증은 git credential helper에 위임

#### 설정
- [ ] 앱 설정 저장/로드 (`%APPDATA%/GitMini/`)
- [ ] 창 크기/위치 기억

### 3.2 Should Have (MVP 완료 후)

- [ ] **원클릭 워크플로우** — Commit + Push 한 번에
- [ ] **Undo 최근 작업** — 최근 커밋 취소 (soft reset), 스테이징 취소
- [ ] **Stash** — save / pop / list
- [ ] **기본 Merge** — fast-forward merge, 충돌 시 외부 에디터 안내
- [ ] **레포 생성** — GitHub API로 새 레포 생성
- [ ] **커밋 메시지 히스토리** — 최근 메시지 드롭다운
- [ ] **파일 필터/검색** — 변경 파일 목록 내 검색

### 3.3 Nice to Have (시간 남으면)

- [ ] 테마 변경 (Primer / Nord / Cupertino / Dracula, AtlantaFX 내장)
- [ ] 시스템 트레이 상주 (앱 닫아도 백그라운드, Fetch 알림)
- [ ] 드래그 & 드롭 스테이징 (Unstaged → Staged 드래그)
- [ ] 커밋 알림 (장시간 커밋 안 했을 때)
- [ ] 간단한 통계 (오늘 커밋 수 등)
- [ ] 윈도우 시작 프로그램 등록

### 3.4 Won't Have (하지 않음)

| 기능 | 이유 |
|------|------|
| 코드 에디터 | 범위 밖 |
| 리베이스 | 위험하고 복잡, 터미널이 적합 |
| 충돌 해결 UI | 복잡도 대비 가치 낮음. 충돌 감지 + 외부 에디터 연동으로 대체 |
| GitHub Actions 관리 | 웹으로 충분 |
| Issue/PR 관리 | 웹으로 충분 |
| 여러 GitHub 계정 | 복잡도 대비 필요성 낮음 |
| 커밋 그래프 시각화 | 구현 복잡도 높음, 미니멀 철학과 충돌 |

---

## 4. 화면 설계

### 레이아웃: 사이드바 + 메인 콘텐츠 (단일 화면)

기존 "대시보드 ↔ 상세" 화면 전환 방식 대신, **사이드바 + 메인 콘텐츠** 단일 화면을 채택한다.

**이유:**
- 레포 전환 시 화면 전환 없이 사이드바 클릭만으로 가능 → 훨씬 빠름
- 항상 레포 목록이 보이므로 "다중 레포 대시보드" 가치를 유지
- GitHub Desktop, SourceTree, Fork 등 모든 상용 도구가 이 패턴 사용
- 네비게이션 스택 관리 불필요 → 구현도 단순

### 메인 화면

```
┌──────────────────────────────────────────────────────────────────────────┐
│  GitMini                                                  [⚙] [─] [□] [×] │
├─────────────┬────────────────────────────────────────────────────────────┤
│             │                                                            │
│  레포 목록   │  ◆ Algorithm_Drill            main  ↑0 ↓0                  │
│             │  ──────────────────────────────────────────────────────────│
│  ▸ Algo..   │                                                            │
│    main ✓   │  [변경된 파일]                    [스테이징된 파일]          │
│             │  ┌───────────────────┐           ┌───────────────────┐    │
│  ▸ GitMini  │  │ M README.md       │    →→→    │ M README.md       │    │
│    main ⚠3  │  │ M src/Main.java   │   [▶▶]   │                   │    │
│             │  │ ? test.txt        │    ←←←    │                   │    │
│  ▸ BotTy..  │  └───────────────────┘   [◀◀]   └───────────────────┘    │
│    main ✓   │                                                            │
│             │  ──────────────────────────────────────────────────────────│
│             │  [Diff 영역]                                               │
│             │   - old line                                               │
│             │   + new line                                               │
│             │  ──────────────────────────────────────────────────────────│
│             │                                                            │
│             │  커밋 메시지: [                                        ]    │
│             │  [☐ Amend]         [Commit]  [Commit & Push]               │
│             │                                                            │
│             │  ──────────────────────────────────────────────────────────│
│             │  [Pull ↓]  [Push ↑]  [Fetch ⟳]   브랜치: [main ▼]  [+]   │
│             │                                                            │
│  [+ 추가]   │  ──────────────────────────────────────────────────────────│
│  [Clone]    │  ▾ Command Log                                             │
│             │  │ 09:14:23  git status                              ✓    │
│             │  │ 09:14:25  git add README.md                       ✓    │
│             │  │ 09:14:30  git commit -m "fix: typo"               ✓    │
│             │                                                            │
├─────────────┴────────────────────────────────────────────────────────────┤
│ ✓ Ready                                           main  ↑0 ↓0  │ 3 변경 │
└──────────────────────────────────────────────────────────────────────────┘
```

### 설정 화면 (모달 다이얼로그)

```
┌────────────────────────────────────────────┐
│  설정                                  [×] │
├────────────────────────────────────────────┤
│                                            │
│  일반                                      │
│  ├── 테마: [Primer Dark ▼]                 │
│  ├── 자동 Fetch 주기: [5분 ▼]              │
│  └── 시작 시 마지막 레포 열기: [☑]          │
│                                            │
│  GitHub                                    │
│  ├── Personal Access Token: [****...]      │
│  └── [토큰 테스트]                          │
│                                            │
│  경로                                      │
│  ├── 기본 Clone 경로: [G:\Projects  ...]   │
│  └── 외부 터미널: [Windows Terminal ▼]      │
│                                            │
│                          [취소]  [저장]     │
└────────────────────────────────────────────┘
```

### UX 필수 요소

| 요소 | 설명 |
|------|------|
| **비동기 피드백** | 모든 Git 작업에 로딩 인디케이터. Push/Pull/Clone 시 진행률 표시. 작업 중 버튼 비활성화 (중복 실행 방지) |
| **성공/실패 피드백** | 상태바 또는 토스트 알림으로 결과 표시. 실패 시 원인 + 가능한 해결 방법 제시 |
| **키보드 단축키** | `Ctrl+Enter` = Commit, `Ctrl+Shift+P` = Push, `Ctrl+Shift+L` = Pull 등 |
| **컨텍스트 메뉴** | 파일 우클릭 → Discard / Stage / Unstage / Open in Explorer / Copy Path |
| **드래그 & 드롭** | 폴더를 사이드바에 드롭하여 레포 추가 |
| **상태바** | 하단에 현재 작업 상태, 브랜치 정보, ahead/behind 상시 표시 |

---

## 5. 아키텍처

### 패키지 구조

```
com.gitmini/
├── GitMiniApp.java                        // JavaFX Application 진입점
│
├── controller/                             // [UI 계층] JavaFX FXML 컨트롤러
│   ├── MainController.java                 //   메인 화면 (사이드바 + 콘텐츠 전체)
│   ├── RepoDetailController.java           //   메인 콘텐츠 영역 (파일 목록, 커밋, 액션)
│   ├── SettingsController.java             //   설정 다이얼로그
│   └── component/                          //   재사용 UI 컴포넌트 컨트롤러
│       └── RepoCardController.java         //   사이드바 레포 카드
│
├── model/                                  // [도메인 모델] 순수 데이터 객체
│   ├── Repository.java                     //   레포 정보 (경로, 이름, 상태)
│   ├── FileChange.java                     //   변경 파일 (상태, 경로)
│   ├── BranchInfo.java                     //   브랜치 정보 (이름, 추적 브랜치, ahead/behind)
│   ├── CommitInfo.java                     //   커밋 정보 (hash, author, message, date)
│   └── GitCommandRecord.java              //   실행된 git 명령어 기록
│
├── service/                                // [비즈니스 계층] 핵심 로직
│   ├── GitService.java                     //   Git 작업 (status, commit, push, pull...)
│   ├── GitHubService.java                  //   GitHub REST API 연동
│   ├── RepositoryManager.java              //   레포 목록 관리 (등록/제거/상태 갱신)
│   └── WorkflowService.java               //   복합 작업 (Commit+Push 등)
│
├── git/                                    // [인프라 계층] Git CLI 실행 엔진
│   ├── GitExecutor.java                    //   ProcessBuilder 래퍼 (실행, 타임아웃, 환경 변수)
│   ├── GitCommandBuilder.java              //   명령어 조립 (fluent API)
│   ├── GitResult.java                      //   실행 결과 (exit code, stdout, stderr)
│   └── parser/                             //   출력 파싱 (git output → 도메인 모델)
│       ├── StatusParser.java
│       ├── BranchParser.java
│       ├── LogParser.java
│       └── DiffParser.java
│
├── config/                                 // [설정 계층] 앱 설정 + 토큰 관리
│   ├── AppConfig.java                      //   설정 모델 (테마, 자동 Fetch 주기, 경로 등)
│   ├── ConfigManager.java                  //   JSON 파일 읽기/쓰기 (%APPDATA%/GitMini/)
│   └── TokenManager.java                   //   GitHub PAT 저장/로드
│
├── event/                                  // [이벤트 시스템] 컴포넌트 간 통신
│   ├── EventBus.java                       //   발행/구독 중앙 허브
│   ├── RepoStatusChangedEvent.java
│   └── GitOperationCompletedEvent.java
│
├── async/                                  // [비동기 계층] 백그라운드 작업 관리
│   └── TaskManager.java                    //   JavaFX Task 래핑, 진행률/에러 콜백
│
└── exception/                              // [예외 계층] 커스텀 예외 체계
    ├── GitMiniException.java               //   최상위 예외
    ├── GitExecutionException.java          //   git 프로세스 실행 실패
    ├── GitParseException.java              //   git 출력 파싱 실패
    └── ConfigException.java               //   설정 관련 오류
```

### 리소스 구조

```
src/main/resources/
├── fxml/
│   ├── main.fxml                           // 메인 화면 (사이드바 + 콘텐츠)
│   ├── repo_detail.fxml                    // 레포 상세 영역
│   ├── settings.fxml                       // 설정 다이얼로그
│   └── component/
│       └── repo_card.fxml                  // 사이드바 레포 카드
├── css/
│   └── app.css                             // 앱 커스텀 스타일 (AtlantaFX 위에 덧씌움)
├── images/
│   └── icon.png                            // 앱 아이콘
└── logback.xml                             // 로깅 설정
```

### 의존성 방향

```
controller → service → git/config
     ↓          ↓         ↓
   model      model     model
     ↑          ↑
   event      async
```

- Controller가 git 패키지를 직접 참조하지 않음
- 모든 계층이 model을 참조 (순수 데이터 객체)
- event/async는 계층 간 통신 인프라

### 핵심 아키텍처 결정

#### 비동기 처리 패턴
모든 Git/네트워크 작업은 `TaskManager`를 통해 실행한다.
```
Controller → TaskManager.run(
    task:      () -> gitService.push(repo),     // 백그라운드 스레드
    onSuccess: (result) -> updateUI(result),     // UI 스레드
    onFailure: (error) -> showError(error)       // UI 스레드
)
```
- UI 스레드 블로킹 금지
- UI 스레드 복귀는 TaskManager가 보장 (`Platform.runLater` 캡슐화)
- 작업 중 버튼 비활성화, 로딩 인디케이터 표시

#### 이벤트 기반 갱신
```
Push 완료
  → EventBus.publish(RepoStatusChangedEvent)
  → MainController가 구독 → 사이드바 레포 상태 자동 갱신
```
- Controller 간 직접 참조 없음
- 느슨한 결합, 새 구독자 추가 용이

#### 에러 처리 전략

| 계층 | 전략 |
|------|------|
| `git/` | `GitExecutionException` (프로세스 실패), `GitParseException` (파싱 실패) 발생 |
| `service/` | 예외를 잡아서 의미 있는 메시지로 변환하거나 상위로 전파 |
| `controller/` | 사용자에게 보여줄 에러 메시지로 변환 (상태바 / 토스트 / 다이얼로그) |
| 글로벌 | `Thread.setDefaultUncaughtExceptionHandler` — 예상치 못한 예외 로깅 + "알 수 없는 오류" 표시 |

#### 로깅 전략

| 항목 | 설정 |
|------|------|
| 라이브러리 | SLF4J + Logback |
| 로그 위치 | `%APPDATA%/GitMini/logs/` |
| 로테이션 | 10MB 또는 7일 기준 |
| 레벨 | git 명령 실행 = DEBUG, 사용자 액션 = INFO, 에러 = ERROR |

---

## 6. 기술적 결정

### 토큰 관리

| 용도 | 방식 |
|------|------|
| Git push/pull 인증 | **git credential helper에 위임** — 별도 구현 불필요. 사용자 OS의 credential manager 사용 |
| GitHub REST API (PAT) | **앱에서 관리** — `%APPDATA%/GitMini/config.json`에 저장. OS 파일 시스템 권한에 의존 |

### 설정 저장

| 항목 | 설정 |
|------|------|
| 포맷 | JSON (Gson) |
| 위치 | `%APPDATA%/GitMini/` |
| 내용 | 레포 목록, 창 크기/위치, 테마, 자동 Fetch 주기, PAT, 기본 경로 등 |
| 로딩 실패 시 | 기본값 적용 + 경고 표시. 절대 크래시하지 않음 |
| 저장 시점 | 앱 종료 시 + 주요 설정 변경 시 즉시 |

### ProcessBuilder 방어 조치

| 조치 | 이유 |
|------|------|
| `LC_ALL=C` 환경 변수 | git 출력 언어 통일 (파싱 안정성) |
| 타임아웃 (30초 기본, 네트워크 작업 120초) | 무한 대기 방지 |
| git 설치 확인 (`git --version`) | 앱 시작 시 검증, 없으면 안내 |
| git 최소 버전 확인 | `git switch` 등 최신 명령어 호환성 (2.23+) |
| stdout/stderr 분리 | 에러 메시지 정확한 포착 |
| exit code 기반 판단 | git은 정상 상황에서도 stderr에 출력할 때 있음 |

---

## 7. 구현 계획

### Phase 1: 환경 설정 + 프로젝트 뼈대

| 작업 | 상세 |
|------|------|
| Gradle Kotlin DSL 전환 | `build.gradle` → `build.gradle.kts`, 모든 의존성 추가 |
| 패키지 구조 생성 | 합의된 구조대로 패키지 + 클래스 틀 |
| JavaFX 진입점 | `GitMiniApp.java` — Stage 설정, AtlantaFX 테마 적용 |
| 기본 레이아웃 FXML | 사이드바 + 메인 콘텐츠 + 상태바 + Command Log 패널 껍데기 |
| 설정 인프라 | `ConfigManager` — `%APPDATA%/GitMini/`, JSON 읽기/쓰기 |
| 로깅 인프라 | Logback 설정 (logback.xml), 로그 파일/로테이션 |
| 예외 체계 | `GitMiniException` 계층 구조 |
| **결과물** | **앱이 실행되고, 빈 사이드바 + 메인 화면이 보임. 설정 파일 생성/로드 동작** |

### Phase 2: Git 연동 핵심

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
| **결과물** | **UI 없이 서비스 계층이 완전 동작. 단위 테스트로 검증** |

### Phase 3: UI 구현

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
| **결과물** | **실제 사용 가능한 Git 클라이언트. 일상 Git 작업 모두 가능** |

### Phase 4: GitHub API 연동

| 작업 | 상세 |
|------|------|
| PAT 설정 UI | 설정 화면에서 토큰 입력/저장/테스트 |
| GitHubService | HttpClient 기반, 인증 헤더 처리 |
| Clone 기능 | URL → 경로 선택 → Clone (진행률 표시) |
| (선택) 레포 생성 | GitHub API로 새 레포 생성 |
| **결과물** | **GitHub 연동 완료. Clone, Private 레포 접근 가능** |

### Phase 5: 완성도

| 작업 | 상세 |
|------|------|
| 에러 핸들링 강화 | 네트워크 끊김, 권한 오류, 잘못된 경로 등 엣지 케이스 대응 |
| 설정 화면 완성 | 테마 선택, 자동 Fetch 주기, 기본 경로, 외부 터미널 설정 |
| 탐색기/터미널 열기 | 컨텍스트 메뉴에서 바로 열기 |
| Undo 기능 | 최근 커밋 취소, 스테이징 취소 |
| 창 상태 저장 | 크기/위치 저장, 다음 실행 시 복원 |
| jpackage 빌드 | .exe 설치 파일, 아이콘, 메타데이터 |
| **결과물** | **상용 수준 완성. 배포 가능** |

### Phase 6: 확장 (선택)

| 작업 | 상세 |
|------|------|
| Stash | save / pop / list |
| 기본 Merge | fast-forward merge, 충돌 시 외부 에디터 안내 |
| 원클릭 워크플로우 | Commit + Push 한 번에 |
| 시스템 트레이 | 앱 최소화 → 트레이 상주, 알림 |
| 커밋 메시지 히스토리 | 최근 메시지 드롭다운 |
| 파일 필터 | 변경 파일 목록 검색 |

---

## 8. 결정 사항 요약

| 항목 | 결정 | 이유 |
|------|------|------|
| 프로젝트 이름 | **GitMini** | 미니멀 강조 |
| 프로젝트 성격 | **상용 수준 데스크톱 앱** | 매일 실사용 |
| Git 연동 | **ProcessBuilder** (git CLI) | Command Log 시너지, 기존 git 설정 활용 |
| UI 구현 | **FXML + Scene Builder** | UI/로직 분리, 시각적 편집 |
| UI 테마 | **AtlantaFX (Primer)** | 현대적 CSS, Light/Dark 내장 |
| 화면 구조 | **사이드바 + 메인 콘텐츠** | 단일 화면, 빠른 레포 전환 |
| 빌드 스크립트 | **Gradle Kotlin DSL** | 공식 기본값, 타입 안전, IDE 지원 |
| 로깅 | **SLF4J + Logback** | Java 로깅 표준 |
| 설정 저장 | **JSON (Gson), %APPDATA%/GitMini/** | 가볍고 단순 |
| Git 인증 | **git credential helper 위임** | 별도 구현 불필요 |
| GitHub API 인증 | **앱에서 PAT 관리** | REST API 호출에 필요 |
| Command Log | **투명성/파워유저 도구** | 학습 도구가 아닌 실행 기록 |

---

## 문서 이력

| 날짜 | 내용 |
|------|------|
| 2026-02-03 | 초안 작성 (학습용 설계) |
| 2026-02-03 | 설계 확정 — 이름, 기술 스택, 차별점, 기능 범위 |
| 2026-02-07 | **설계 재검토** — 학습용 → 상용 전환. 아키텍처/기능/UI/기술 스택 전면 재설계 |
