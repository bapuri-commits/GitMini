# Phase 4: GitHub API 연동

> **상태**: Step 1 진행 중  
> **이전 Phase**: Phase 1~3 완료, 153개 테스트 PASSED  
> **현재 UI**: JavaFX + AtlantaFX(Primer Dark). 사이드바, 파일 변경(검색), Diff, 커밋, 액션 바(Push/Pull/Fetch + upstream 자동), 브랜치, 커밋 히스토리, Command Log, 상태바(로딩+토스트), 컨텍스트 메뉴, 키보드 단축키, 드래그&드롭, 자동 Fetch.

---

## 1. 계획

### 목표 (DESIGN.md §Phase 4)

| 작업 | 상세 |
|------|------|
| PAT 설정 UI | 설정 화면에서 토큰 입력/저장/테스트 |
| GitHubService | HttpClient 기반, 인증 헤더 처리 |
| Clone 기능 | URL → 경로 선택 → Clone (진행률 표시) |
| (선택) 레포 생성 | GitHub API로 새 레포 생성 |
| **결과물** | **GitHub 연동 완료. Clone, Private 레포 접근 가능** |

### 기존 인프라

| 항목 | 상태 |
|------|------|
| `TokenManager` | PAT 파일 저장/로드/삭제 — 구현 완료 (Phase 2) |
| `GitHubService` | 미존재 |
| `SettingsController` / `settings.fxml` | 미존재 |
| `GitService.clone()` | 미존재 (`onCloneRepo()`는 스텁만 있음) |
| HTTP 클라이언트 | `java.net.http.HttpClient` 사용 예정 (Java 표준, 의존성 추가 불필요) |
| Gson | 이미 의존성에 포함 (GitHub API JSON 파싱에 활용) |

### Step 분해 (4단계)

```
Step 1: GitHubService (서비스 계층) — GitHub REST API 클라이언트
Step 2: 설정 다이얼로그 + PAT 설정 UI
Step 3: Clone 기능 (GitService.clone + Clone 다이얼로그)
Step 4: (선택) 레포 생성 + Phase 4 마무리
```

#### Step 1: GitHubService — GitHub REST API 클라이언트

- `service/GitHubService.java` 생성
- `java.net.http.HttpClient` 기반, `Authorization: Bearer <PAT>` 헤더 처리
- API: `validateToken()` (GET /user), `listRepositories()` (GET /user/repos)
- 응답 모델: `GitHubUser`, `GitHubRepo` (model 패키지)
- 에러 처리: 401, 403, 네트워크 오류, 타임아웃
- 단위 테스트

#### Step 2: 설정 다이얼로그 + PAT 설정 UI

- `settings.fxml` + `SettingsController.java` 생성
- GitHub 섹션: PAT 입력(마스킹), 토큰 테스트, 저장/삭제
- 일반 섹션: 자동 Fetch 주기, 기본 Clone 경로
- `GitMiniApp`에 GitHubService 인스턴스 생성 + static getter
- `MainController`에 설정 버튼(⚙) → 모달 다이얼로그 연결

#### Step 3: Clone 기능

- `GitService.clone(url, targetDir)` 메서드 추가
- PAT 기반 private 레포 clone 지원
- Clone 다이얼로그: URL 입력, 경로 선택, 진행률 표시
- Clone 완료 → 레포 목록에 자동 추가 + 선택

#### Step 4: (선택) 레포 생성 + Phase 4 마무리

- `GitHubService.createRepository()` — POST /user/repos
- 레포 생성 다이얼로그 UI
- 생성 완료 → 자동 Clone 제안
- docs/PHASE4.md 최종 기록, DESIGN.md 대조 감사

### Step별 의존 관계

```
Step 1: GitHubService (서비스 계층)
  ↓
Step 2: 설정 다이얼로그 (Step 1의 validateToken 사용)
  ↓
Step 3: Clone (Step 1 + Step 2의 PAT 설정 필요)
  ↓
Step 4: 레포 생성 + 마무리 (Step 1~3 기반 확장)
```

---

## 2. 과정

### Step 1: GitHubService — GitHub REST API 클라이언트

(진행 중)

