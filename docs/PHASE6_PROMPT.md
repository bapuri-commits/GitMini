# Phase 6 시작 프롬프트

아래 내용을 새 대화에 붙여넣으면 Phase 6를 바로 시작할 수 있다.

---

```
GitMini Phase 6: 확장 진행.

## 프로젝트 상태
- **프로젝트 경로**: G:\CS_Study\GitMini
- **설계 문서**: DESIGN.md (DESIGN.md를 기준 진실로 사용)
- **Phase 5 완료**: docs/PHASE5.md 참고. 5개 Step + 후속 수정 3건 전부 구현·검증 완료. 225개 테스트 PASSED. v1.0.0 릴리스 완료.
- **현재 UI**: JavaFX + AtlantaFX(Primer Dark/Light 토글). 사이드바(⚙설정 버튼), 파일 변경(검색), Diff, 커밋, 액션 바(Push/Pull/Fetch + upstream 자동), 브랜치, 커밋 히스토리, Command Log, 상태바(로딩+토스트), 컨텍스트 메뉴(탐색기/터미널에서 열기, 경로 복사, Discard, Stage/Unstage, 최근 커밋 취소), 키보드 단축키(툴팁), 드래그&드롭, 자동 Fetch.
- **Phase 5에서 추가된 것**: ErrorMessages(에러 핸들링 강화, 20+패턴), 설정 완성(테마 Dark/Light, 외부 터미널 CMD/PowerShell/WT, Clone 경로 검증), 터미널에서 열기(파일+레포), Undo 최근 커밋(soft reset, 확인 다이얼로그), 창 크기 화면 범위 클램핑, jpackage 포터블 .exe 빌드.
- **기존 인프라**: GitExecutor, GitService(clone, resetSoft 포함), GitHubService, RepositoryManager, TaskManager(비동기), EventBus, ConfigManager(원자적 저장), TokenManager(PAT 저장/로드), SettingsController, ErrorMessages.

## Phase 6 범위 (DESIGN.md §Phase 6 — 선택적 확장)
1. **원클릭 워크플로우** — Commit + Push 한 번에
2. **시스템 트레이** — 앱 최소화 → 트레이 상주, 알림
3. **커밋 메시지 히스토리** — 최근 메시지 드롭다운
4. **파일 필터** — 변경 파일 목록 검색 (이미 부분 구현: unstagedSearchField/stagedSearchField 존재)
5. **(보류) Stash** — save / pop / list
6. **(보류) 기본 Merge** — fast-forward merge, 충돌 시 외부 에디터 안내

## 작업 방식 (Phase 3에서 확립된 워크플로 — 반드시 따를 것)
1. **Step 분해**: Phase 6를 3~5개 Step으로 나눈다. 각 Step: 구현 → 빌드 검증 → 코드 감사 → 커밋.
2. **Step이 끝날 때마다**: AI가 강도 높은 코드 감사를 진행하고, 체크리스트를 **사용자가 직접 확인할 수 있는 항목**으로 만든다.
3. **사용자**: AI가 만든 내용에 대해 추가 감사를 요청하거나 수정 요청을 한다.
4. **반영 후**: 사용자가 직접 앱을 실행해 체크리스트 항목(및 그 밖에 발견한 점)을 확인하고, 결과를 AI에게 알려주며 다음 Step으로 진행한다.
5. **각 Step마다 커밋한다.** 커밋 메시지는 feat(phase6): Step N <내용> 형태.
6. **DESIGN.md를 기준 진실로 사용한다.** 코드 감사 시 DESIGN.md의 요구사항과 대조한다.
7. **문서**: docs/PHASE6.md에 과정을 기록한다 (PHASE3.md~PHASE5.md와 동일 구조: 1.계획, 2.과정, 3.종료).

## 참고할 기존 코드
- `controller/MainController.java` — 현재 ~2000줄
- `controller/SettingsController.java` — 설정 다이얼로그 (테마, 자동 Fetch 주기, Clone 경로, 외부 터미널, PAT)
- `service/GitService.java` — git 명령 래퍼 (status, add, commit, amend, push, pull, fetch, branch, log, diff, discard, clone, resetSoft)
- `service/GitHubService.java` — GitHub REST API (validateToken, listRepositories, createRepository)
- `config/AppConfig.java` — 설정 모델 (theme, autoFetchIntervalMinutes, windowWidth/Height/X/Y, defaultClonePath, externalTerminal)
- `config/ConfigManager.java` — JSON 설정 읽기/쓰기 (원자적 저장)
- `GitMiniApp.java` — 앱 진입점, 서비스 생성·주입, 테마 적용, 창 상태 저장/복원 (화면 범위 클램핑 포함)
- `util/ErrorMessages.java` — 에러 메시지 매핑 (mapGitError, mapRemoteError, mapBranchError)
- `git/GitCommandBuilder.java` — git 명령 빌더 (soft 옵션 포함)

## 이미 구현된 것 (Phase 6 범위이지만 이전 Phase에서 부분 구현됨)
- **파일 필터/검색**: Unstaged/Staged 각각 TextField로 실시간 필터링 이미 구현. → 검증만 필요하거나 추가 개선(예: 정규식, 파일 유형 필터 등).

일단 Step 분해부터 시작해줘.
```
