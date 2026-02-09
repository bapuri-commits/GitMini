# GitMini 코드 품질 감사 보고서

> **작성일**: 2026-02-09  
> **대상**: Phase 6 완료 시점의 전체 코드베이스  
> **테스트**: 233개 PASSED

---

## 1. 종합 평가

| 항목 | 점수 | 비고 |
|------|------|------|
| 아키텍처 | 8/10 | 계층 분리 우수. MainController만 과도하게 큼 |
| 코드 품질 | 7/10 | 기능별 동작은 견고. 중복 코드와 긴 메서드 존재 |
| 에러 처리 | 8/10 | ErrorMessages 유틸 + 일관된 패턴. 일부 조용한 무시 |
| 동시성 | 7/10 | JavaFX 단일 스레드 전제하에 문제 없음. RepositoryManager 캐시 보호 부재 |
| 테스트 | 6/10 | 서비스/파서/인프라 잘 테스트. UI 계층 테스트 부재 |
| 리소스 관리 | 9/10 | try-with-resources 일관 사용. Process/Stream 누수 없음 |
| **종합** | **7.5/10** | **상용 수준으로 동작. 기술 부채 관리 가능한 수준** |

---

## 2. 강점

### 아키텍처
- **계층 분리**: `controller → service → git/config → model` 의존성 방향이 정확히 지켜짐
- **순환 의존 없음**: controller가 git 패키지를 직접 참조하지 않음
- **model 순수성**: 모든 model 클래스가 순수 데이터 객체 (비즈니스 로직 없음)
- **이벤트 기반**: EventBus로 컴포넌트 간 느슨한 결합

### 비동기 처리
- 모든 Git/네트워크 작업이 `TaskManager.run()`으로 비동기 실행
- UI 스레드 복귀 보장 (`Platform.runLater` 캡슐화)
- 작업 중 버튼 비활성화로 중복 실행 방지

### 에러 처리
- `ErrorMessages` 유틸리티: 20+ git 에러 패턴 → 사용자 친화적 한국어 메시지
- 일관된 에러 콜백 패턴 (log.error + showErrorAlert + setStatus)
- ConfigManager: 설정 로드 실패 시 기본값 반환 (절대 크래시 안 함)

### 리소스 관리
- GitExecutor: Process/Stream을 try-with-resources로 관리
- ConfigManager: 원자적 파일 저장 (ATOMIC_MOVE)
- TaskManager: shutdown() 호출로 ExecutorService 정리

---

## 3. 주요 기술 부채

### 높은 우선순위

#### 3.1 MainController 비대화 (God Object)
- **현황**: ~2,260줄, 12+ 가지 책임
- **영향**: 가독성 저하, 수정 시 부작용 위험, 테스트 어려움
- **권장**: 기능별 분리
  - `CommitController`: 커밋/Amend/Undo 로직
  - `BranchController`: 브랜치 생성/전환
  - `DialogHelper`: Clone/CreateRepo 다이얼로그
  - `AutoFetchManager`: 자동 Fetch 타이머
- **완화 요인**: FXML 단일 컨트롤러 구조상 불가피한 측면 있음. 내부적으로 섹션 주석(`// =====`)으로 구분은 잘 되어 있음

#### 3.2 TaskManager.run() 보일러플레이트
- **현황**: 30+ 곳에서 동일 패턴 반복
  ```java
  TaskManager tm = GitMiniApp.getTaskManager();
  GitService gs = GitMiniApp.getGitService();
  if (tm == null || gs == null) return;
  Repository targetRepo = selectedRepo;
  Path repoPath = Path.of(targetRepo.getPath());
  setStatus("작업 중...");
  tm.run(() -> { ... }, result -> { ... }, error -> { ... });
  ```
- **권장**: 헬퍼 메서드 추출
  ```java
  private void runGitTask(String statusMsg, GitOperation op, String successMsg) { ... }
  ```

### 중간 우선순위

#### 3.3 설정 파일 경합 (Load-Modify-Save 패턴)
- **현황**: RepositoryManager, MainController(커밋 히스토리), GitMiniApp(창 상태)이 각각 `configManager.load() → 수정 → save()` 수행
- **영향**: 동시 호출 시 마지막 save가 덮어쓸 수 있음
- **완화 요인**: JavaFX 단일 스레드에서 동작하므로 실질적 경합 확률 낮음
- **권장**: `ConfigManager.update(Consumer<AppConfig> modifier)` 원자적 업데이트 메서드 도입

#### 3.4 하드코딩된 값
- `500` (Command Log 최대 개수), `5초` (상태 페이드 시간), 터미널 명령어 문자열 등
- **권장**: 상수로 추출

### 낮은 우선순위

#### 3.5 Static 전역 접근자
- `GitMiniApp.getTaskManager()` 등 — 테스트 시 모킹 어려움
- JavaFX Application 구조상 불가피한 측면 있음
- 향후 DI 프레임워크(Guice 등) 도입 시 개선 가능

#### 3.6 UI 계층 테스트 부재
- MainController, SettingsController 테스트 없음
- TestFX 도입 시 해결 가능하나 비용 대비 효과 검토 필요

---

## 4. 구현 완성도 요약

### §3.1 MVP — 전부 구현 ✅

| 카테고리 | 항목 수 | 구현 |
|----------|---------|------|
| 레포지토리 관리 | 5 | 5/5 |
| 기본 Git 작업 | 8 | 8/8 |
| 브랜치 | 4 | 4/4 |
| Diff & 히스토리 | 2 | 2/2 |
| Command Log | 4 | 4/4 |
| 자동 Fetch | 2 | 2/2 |
| 인증 | 2 | 2/2 |
| 설정 | 2 | 2/2 |
| **합계** | **29** | **29/29 (100%)** |

### §3.2 Should Have — 7개 중 4개 구현

| 항목 | 상태 |
|------|------|
| 원클릭 워크플로우 | 보류 |
| Undo 최근 작업 | ✅ 구현 |
| Stash | 보류 |
| 기본 Merge | 보류 |
| 레포 생성 | ✅ 구현 |
| 커밋 메시지 히스토리 | ✅ 구현 |
| 파일 필터/검색 | ✅ 구현 |

### §3.3 Nice to Have — 6개 중 2개 구현

| 항목 | 상태 |
|------|------|
| 테마 변경 | ✅ 구현 |
| 시스템 트레이 | ✅ 구현 |
| 드래그&드롭 스테이징 | 미구현 |
| 커밋 알림 | 미구현 |
| 간단한 통계 | 미구현 |
| 시작 프로그램 등록 | 미구현 |

---

## 5. 결론

GitMini는 **MVP 기능 100% 구현**, **Phase 1~6 전부 완료**, **233개 테스트 PASSED** 상태로, "매일 실제로 사용할 상용 수준의 Git 클라이언트"라는 프로젝트 목표를 달성했다.

기술 부채는 **MainController 비대화**가 가장 큰 항목이지만, 기능적으로 안정적이고 구조적 결함(순환 의존, 리소스 누수 등)은 없다. 향후 기능 추가나 리팩토링 시 MainController 분리를 우선 고려하면 된다.
