# Phase 4: GitHub API 연동

> **상태**: 완료  
> **이전 Phase**: Phase 1~3 완료, 153개 테스트 PASSED  
> **현재 테스트**: 191개 PASSED

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

### Step 분해 (4단계)

```
Step 1: GitHubService (서비스 계층) — GitHub REST API 클라이언트
Step 2: 설정 다이얼로그 + PAT 설정 UI
Step 3: Clone 기능 (GitService.clone + Clone 다이얼로그)
Step 4: (선택) 레포 생성 + Phase 4 마무리
```

---

## 2. 과정

### Step 1: GitHubService — GitHub REST API 클라이언트

- `GitHubService.java`: `java.net.http.HttpClient` 기반, `Authorization: Bearer <PAT>` 헤더.
- API: `validateToken()` (GET /user), `validateToken(String)` (저장 전 검증), `listRepositories()` (GET /user/repos).
- 응답 모델: `GitHubUser` (login, name, avatarUrl), `GitHubRepo` (fullName, cloneUrl, description, isPrivate, defaultBranch) — 모두 record.
- `GitHubApiException`: HTTP 상태코드별 분류 (401/403/네트워크), `isUnauthorized()`/`isForbidden()`/`isNetworkError()` 헬퍼.
- 에러 메시지 한국어 매핑: 401("토큰 유효한지 확인"), 403 Rate Limit("호출 한도 초과"), 네트워크("연결할 수 없습니다").
- **감사 수정 5건**: Javadoc 불일치, Rate Limit 대소문자 취약, executePost token null 무방비, parseUser login null 방어, parseRepoList 비-객체 요소 예외 누수.
- 27개 단위 테스트 (HttpClient Mockito 모킹).

### Step 2: 설정 다이얼로그 + PAT 설정 UI

- `settings.fxml` + `SettingsController.java`: 모달 다이얼로그.
- **일반 섹션**: 자동 Fetch 주기 (ComboBox 1~60분), 기본 Clone 경로 (DirectoryChooser).
- **GitHub 섹션**: PAT PasswordField, 토큰 테스트(비동기 → "✓ 사용자명" / "✗ 에러"), 토큰 삭제.
- 토큰 생성 안내 텍스트: GitHub Settings 경로 + 필요 권한(repo).
- `GitMiniApp.java`에 `GitHubService` 인스턴스 생성 + `getGitHubService()` static getter.
- `MainController`에 `onSettings()`: 모달 열기, 메인 창 중앙 배치(`onShown` 좌표 계산), 저장 후 자동 Fetch 타이머 재시작.
- `main.fxml` 사이드바 헤더에 ⚙ 설정 버튼 추가.
- **감사 수정 2건**: CSS 중복 패딩 제거, 다이얼로그 중앙 배치.

### Step 3: Clone 기능

- `GitCommandBuilder.cloneRepo()`, `progress()` 메서드 추가.
- `GitService.cloneRepo(url, targetDir)`: git clone --progress, NETWORK_TIMEOUT(120s), 방어 검증(URL 빈값, targetDir 존재, 부모 디렉토리 존재).
- `GitService.injectTokenIntoUrl(url, token)`: HTTPS URL에 PAT 삽입 (`https://token@github.com/...`). SSH/이미 인증 포함 URL은 원본 반환.
- `GitService.maskTokenInUrl()`: 로그 파일에 토큰 노출 방지 (`https://***@...`).
- Clone 다이얼로그 (MainController): URL 입력, 경로 선택(기본 Clone 경로 자동 로드), 로딩 인디케이터, 에러 시 다이얼로그 유지, 성공 시 자동 레포 목록 추가 + 다이얼로그 닫힘.
- `extractRepoName(url)`: URL에서 레포명 추출 (HTTPS/SSH 모두 지원).
- 단축키 툴팁 추가: Commit(Ctrl+Enter), Push(Ctrl+Shift+P), Pull(Ctrl+Shift+L), Fetch(Ctrl+Shift+F).
- **버그 수정**: Clone 버튼 `disableProperty().bind()` 후 `setDisable()` 호출 시 "bound value cannot be set" 에러 → 리스너 방식으로 교체.
- 7개 단위 테스트 (cloneRepo 파라미터 검증 + injectTokenIntoUrl 4케이스).

### Step 4: 레포 생성 + Phase 4 마무리

- `GitHubService.createRepository(name, description, isPrivate, autoInit)`: POST /user/repos.
- `parseRepo()`: 단일 레포 JSON 파싱 (createRepository 응답용).
- 레포 생성 다이얼로그 (MainController): 이름, 설명, Private/Public, README 초기화 옵션, 생성 진행률.
- 생성 완료 → Clone 제안 다이얼로그 → 확인 시 자동 Clone (`autoCloneNewRepo`).
- 토큰 미설정 시 에러 다이얼로그 (⚙ 경로 안내 포함).
- `main.fxml` 사이드바에 "새 레포" 버튼 추가.
- 4개 단위 테스트 (createRepository 성공/이름빈값/토큰없음/422 이름중복).

### 후속 버그 수정 (사용자 검증 중 발견)

- **parseRepo IllegalStateException 미포착**: `parseRepoList`와 동일 패턴 누락 → catch에 `IllegalStateException` 추가.
- **토큰 삭제 즉시 반영 버그**: "삭제" 클릭 시 디스크에서 바로 삭제 → "취소"해도 복원 불가. `tokenDeletePending` 플래그 도입, "저장" 시에만 실제 삭제.
- **토큰 미설정 에러 메시지 잘림**: header/content 분리 + 줄바꿈 + ⚙ 경로 안내 추가.

---

## 3. 종료

**Phase 4 완료.** 2026-02-09.

- **검증**: `gradlew build` / `gradlew test` 191개 PASSED. 사용자 수동 검증 전수 통과.
- **완료 요약**: 4개 Step + 후속 수정 3건.
  - GitHubService (HttpClient 기반 REST API 클라이언트 — validateToken, listRepositories, createRepository)
  - 설정 다이얼로그 (PAT 입력/저장/테스트/삭제(지연), 자동 Fetch 주기, 기본 Clone 경로)
  - Clone (URL + 경로 → git clone, PAT 자동 삽입, 진행률, 에러 피드백, 자동 레포 추가)
  - 레포 생성 (GitHub API → 자동 Clone 제안)
  - 단축키 툴팁 (Commit, Push, Pull, Fetch)
- **추가 구현**: 토큰 URL 마스킹(로그 보안), 에러 메시지 80자 축약, 다이얼로그 중앙 배치, Clone 버튼 바인딩 버그 수정, 토큰 삭제 지연(취소 안전).
- **다음 Phase**: Phase 5 (완성도) — 에러 핸들링 강화, 설정 화면 완성, Undo, 창 상태 저장, jpackage 빌드.
