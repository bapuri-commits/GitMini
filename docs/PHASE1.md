# Phase 1: 환경 설정 + 프로젝트 뼈대

> **상태**: 완료  
> **결과물**: 앱 실행, 빈 사이드바 + 메인 화면, 설정 파일 생성/로드 동작

---

## 1. 계획

### 목표 (DESIGN.md §7)

| 작업 | 상세 |
|------|------|
| Gradle Kotlin DSL 전환 | `build.gradle` → `build.gradle.kts`, 모든 의존성 추가 |
| 패키지 구조 생성 | 합의된 구조대로 패키지 + 클래스 틀 |
| JavaFX 진입점 | `GitMiniApp.java` — Stage 설정, AtlantaFX 테마 적용 |
| 기본 레이아웃 FXML | 사이드바 + 메인 콘텐츠 + 상태바 + Command Log 패널 껍데기 |
| 설정 인프라 | `ConfigManager` — `%APPDATA%/GitMini/`, JSON 읽기/쓰기 |
| 로깅 인프라 | Logback 설정 (logback.xml), 로그 파일/로테이션 |
| 예외 체계 | `GitMiniException` 계층 구조 |

### 결과물 정의

- 앱이 실행되고, 빈 사이드바 + 메인 화면이 보인다.
- 설정 파일 생성/로드가 동작한다.

---

## 2. 과정

- Java 17 toolchain 설정 후 Gradle·의존성(JavaFX, AtlantaFX, Gson, Logback) 추가.
- 패키지 구조 생성: `controller`, `model`, `config`, `event`, `async`, `exception`, 리소스 `fxml/`, `css/`.
- `GitMiniApp`, `Launcher` 진입점 및 AtlantaFX Primer Dark 테마 적용.
- `ConfigManager`, `AppConfig`, `TokenManager` — `%APPDATA%/GitMini/` 기반 설정 저장/로드.
- `GitMiniException` → `GitExecutionException`, `GitParseException`, `ConfigException` 예외 계층.
- `EventBus` (싱글톤, 발행/구독), `TaskManager` (JavaFX Task 래핑, 비동기 실행).
- 모델 8종: `Repository`, `FileChange`, `BranchInfo`, `CommitInfo`, `GitCommandRecord`, `DiffEntry`, `DiffHunk`, `DiffLine`.
- `main.fxml` 뼈대: 사이드바 + 메인 콘텐츠 + 상태바, `app.css`, `logback.xml`.

---

## 3. 종료

### 검증

- `gradlew build` 성공.
- `gradlew test` 성공 (Phase 1 관련: ConfigManagerTest 5, TokenManagerTest 5, EventBusTest 7).
- `gradlew run` 실행 시 창 표시, 사이드바·메인 영역·상태바·AtlantaFX 테마 확인.
- `%APPDATA%/GitMini/`, `config.json` 생성 확인.

### 완료 요약

- 환경 설정 및 프로젝트 뼈대 구축 완료.
- 설정·로깅·예외·이벤트·비동기 인프라와 기본 UI 껍데기가 준비됨.

### 다음 Phase 시 주의사항

- Phase 2에서 `GitExecutor` → `GitService`를 추가할 때, EventBus/TaskManager는 이미 존재하므로 연동만 하면 됨.
