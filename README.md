# GitMini

> **미니멀 Git 데스크톱 클라이언트** — 가볍고 빠른 상용 수준 Git 도구

[![Release](https://img.shields.io/github/v/release/bapuri-commits/GitMini)](https://github.com/bapuri-commits/GitMini/releases)

## 다운로드

[**GitMini v1.0.0 (Windows)**](https://github.com/bapuri-commits/GitMini/releases/latest) — 포터블 실행 파일 (JRE 내장, 설치 불필요)

압축 해제 후 `GitMini.exe` 실행. **전제 조건**: [Git 2.23.0+](https://git-scm.com/) 설치 필요.

## 왜 만들었는가

| 기존 도구 | 문제점 |
|-----------|--------|
| GitHub Desktop | 기능이 많고 무거움 |
| 터미널 Git | 단순 작업에도 명령어를 일일이 입력 |
| VS Code Git | IDE에 종속됨 |
| GitKraken/Fork | 유료이거나 무거움 |

**GitMini는** GitHub Desktop의 20% 기능으로 일상 작업의 95%를 커버합니다.

## 주요 기능

- **다중 레포 대시보드** — 사이드바에서 여러 레포 상태를 한눈에, 즉시 전환
- **기본 Git 작업** — Stage / Unstage / Commit / Amend / Push / Pull / Fetch
- **브랜치** — 생성 / 전환 / ahead-behind 표시
- **Inline Diff** — 파일 클릭 시 변경 내용 즉시 표시
- **커밋 히스토리** — 최근 커밋 목록
- **Command Log** — 실행된 Git 명령어를 투명하게 표시 (복사 가능)
- **GitHub 연동** — PAT 설정, Clone, 레포 생성
- **다크/라이트 테마** — Primer Dark / Primer Light (AtlantaFX)
- **외부 터미널에서 열기** — CMD / PowerShell / Windows Terminal
- **Undo** — 최근 커밋 취소 (soft reset)
- **자동 Fetch** — 설정 가능 주기로 백그라운드 실행
- **드래그 & 드롭** — 폴더 드롭으로 레포 추가
- **키보드 단축키** — Ctrl+Enter(Commit), Ctrl+Shift+P(Push), Ctrl+Shift+L(Pull), Ctrl+Shift+F(Fetch)

## 기술 스택

| 구분 | 기술 |
|------|------|
| 언어 | Java 17 |
| GUI | JavaFX 21 + FXML |
| 테마 | AtlantaFX (Primer) |
| Git 연동 | ProcessBuilder (git CLI) |
| GitHub API | java.net.http.HttpClient |
| JSON | Gson |
| 로깅 | SLF4J + Logback |
| 빌드 | Gradle Kotlin DSL |
| 패키징 | jpackage |
| 테스트 | JUnit 5 + Mockito (225개) |

## 빌드

```bash
# 테스트
./gradlew test

# 실행
./gradlew run

# 포터블 .exe 빌드 (JDK 17 필요)
./gradlew jpackageImage
# 결과: build/jpackage/GitMini/GitMini.exe
```

## 프로젝트 구조

```
src/main/java/com/gitmini/
├── GitMiniApp.java          # 앱 진입점, 서비스 생성·주입
├── Launcher.java            # 런처 (모듈 시스템 우회)
├── async/TaskManager.java   # 비동기 작업 실행
├── config/                  # 설정 (AppConfig, ConfigManager, TokenManager)
├── controller/              # UI 컨트롤러 (MainController, SettingsController)
├── event/EventBus.java      # 이벤트 버스
├── exception/               # 커스텀 예외 계층
├── git/                     # Git CLI 실행 (GitExecutor, GitCommandBuilder, Parsers)
├── model/                   # 데이터 모델 (Repository, FileChange, CommitInfo, ...)
├── service/                 # 비즈니스 서비스 (GitService, GitHubService, RepositoryManager)
└── util/ErrorMessages.java  # 에러 메시지 매핑
```

## 라이선스

MIT
