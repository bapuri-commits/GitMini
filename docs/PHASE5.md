# Phase 5: 완성도

> **상태**: 완료  
> **이전 Phase**: Phase 4 완료, 191개 테스트 PASSED  
> **현재 테스트**: 225개 PASSED  
> **기준 문서**: DESIGN.md §Phase 5

---

## 1. 계획

### 목표 (DESIGN.md §Phase 5)

| 작업 | 상세 |
|------|------|
| 에러 핸들링 강화 | 네트워크 끊김, 권한 오류, 잘못된 경로 등 엣지 케이스 대응 |
| 설정 화면 완성 | 테마 선택, 자동 Fetch 주기, 기본 경로, 외부 터미널 설정 |
| 탐색기/터미널 열기 | 컨텍스트 메뉴에서 바로 열기 |
| Undo 기능 | 최근 커밋 취소 (soft reset), 스테이징 취소 |
| 창 상태 저장 | 크기/위치 저장, 다음 실행 시 복원 |
| jpackage 빌드 | .exe 설치 파일, 아이콘, 메타데이터 |
| **결과물** | **상용 수준 완성. 배포 가능** |

### 이미 구현된 것 (Phase 5 범위이지만 이전 Phase에서 구현됨)

| 항목 | 현재 상태 |
|------|-----------|
| 창 크기/위치 저장 | `GitMiniApp.stop()`에서 저장, `start()`에서 복원. **검증만 필요.** |
| 자동 Fetch 주기 | 설정 다이얼로그에서 변경 가능, 저장 후 타이머 재시작. 구현 완료. |
| 기본 Clone 경로 | 설정에서 설정/변경 가능. 구현 완료. |
| 탐색기에서 열기 | Unstaged/Staged 파일 우클릭 → "탐색기에서 열기" 구현됨. |
| 스테이징 취소 | Unstage(개별/전체) 이미 구현됨. Undo에서는 **최근 커밋 취소(soft reset)** 만 추가. |

### Step 분해 (5단계)

```
Step 1: 에러 핸들링 강화
        — Git/GitHub/네트워크 엣지 케이스: 타임아웃, 연결 실패, 권한/경로 오류.
        — 사용자 친화적 메시지, 재시도/복구 안내. 기존 Alert/토스트 정리.

Step 2: 설정 화면 완성
        — 테마 선택 (Primer Dark / Primer Light, AtlantaFX 내장).
        — 외부 터미널 설정 (설정값 저장/로드, AppConfig.externalTerminal 이미 존재).
        — 자동 Fetch 주기·기본 경로는 이미 있음 → UI/연동 점검만.

Step 3: 터미널에서 열기 + Undo 최근 커밋
        — 컨텍스트 메뉴에 "터미널에서 열기" 추가 (설정의 외부 터미널 사용).
        — GitService: soft reset (최근 커밋 취소) + MainController UI (버튼 또는 메뉴).

Step 4: 창 상태 저장 검증
        — start/stop 창 복원·저장 동작 확인. 체크리스트로 사용자 수동 검증.

Step 5: jpackage 빌드
        — Gradle jpackage 태스크, .exe 설치 파일, 아이콘, 앱 메타데이터.
```

### 워크플로 (Phase 3/4와 동일)

1. 각 Step: **구현 → 빌드 검증(`gradlew build`) → 코드 감사 → 커밋** (`feat(phase5): Step N <내용>`).
2. Step 종료 시 AI가 **체크리스트**를 제시 → 사용자가 앱 실행 후 확인·피드백.
3. DESIGN.md 기준으로 코드 감사.
4. 과정은 본 문서 §2에 기록, 종료 시 §3 갱신.

---

## 2. 과정

*(Step 진행 시마다 기록)*

### Step 1: 에러 핸들링 강화

- `ErrorMessages.java` 유틸리티 클래스 신규 생성 (`com.gitmini.util`)
  - `mapGitError(String)` — 일반 git CLI 에러 → 한국어 (index.lock, 권한, 디스크, 손상, 타임아웃, 빈 커밋, 브랜치 중복, 유효하지 않은 이름 등)
  - `mapRemoteError(String)` — 원격 작업 에러 → 한국어 (DNS, 연결 거부, 타임아웃, SSL, 인증, 레포 미발견, non-fast-forward, merge 충돌, unable to access 등). 기존 MainController의 `mapRemoteErrorMessage()` 이관 + 패턴 7개 추가 (SSL, connection reset, logon failed, repo not found, merge conflict, unable to access, getaddrinfo).
  - `mapBranchError(String)` — 브랜치 전환 에러 → 한국어. 기존 `mapBranchErrorMessage()` 이관.
  - `truncate(String)` — 300자 초과 메시지 축약.
  - 모든 메서드 null 안전.

- MainController: 기존 private `mapRemoteErrorMessage()`/`mapBranchErrorMessage()` 제거 → `ErrorMessages.*` 호출로 교체.
  - 모든 에러 콜백에서 사용자 노출 메시지에 `ErrorMessages` 적용 (총 18곳):
    - 레포 추가/드롭 실패, 레포 목록 로드 실패, 레포 상세 갱신 실패
    - Stage/Unstage 개별/전체 실패
    - 커밋/Amend 실패
    - 브랜치 생성/전환 실패
    - Fetch/Pull/Push 실패 (upstream 자동 재시도 포함)
    - Clone 실패 (다이얼로그/자동)
    - 변경 취소(Discard) 실패
    - 설정 다이얼로그 열기 실패

- SettingsController: Clone 경로 유효성 검증 추가. 비어 있지 않은 경로가 존재하지 않을 경우 경고 다이얼로그 (YES/NO) 후 저장.

- `ErrorMessagesTest.java` 31개 단위 테스트.
  - MapGitErrorTest: 13개 (null, index.lock, 권한, access denied, not a git repo, 디스크, 손상, 타임아웃, 빈 커밋, 브랜치 중복, 유효하지 않은 이름, 알 수 없는 메시지, 긴 메시지)
  - MapRemoteErrorTest: 16개 (null, 원격 없음, upstream 미설정, DNS, 연결 거부, 타임아웃, SSL, 인증, 레포 미발견, non-fast-forward, merge 충돌, unable to access, GitExecutor 타임아웃, index.lock 위임, connection reset, logon failed)
  - MapBranchErrorTest: 5개 (null, uncommitted changes, pathspec, index.lock 위임, 알 수 없는)
  - TruncateTest: 5개 (null, 짧은, 긴, 300자, 301자)

- `ErrorMessages.java`: 연산자 우선순위 명확화 (괄호 추가, 동작 변경 없음).

- 설정 다이얼로그: **테마 선택 ComboBox 추가** (Primer Dark / Primer Light).
  - `settings.fxml`에 테마 ComboBox 추가.
  - `SettingsController`: `themeComboBox` 바인딩, `THEME_OPTIONS` 정의, 로드/저장 로직.
  - `MainController.onSettings()`: 저장 후 `GitMiniApp.applyTheme()` 즉시 호출 → 다이얼로그 닫자마자 테마 적용.

- `DESIGN.md`: Phase 6의 Stash, 기본 Merge 항목에 **(보류)** 표시.

- **코드 수준 네트워크/upstream 검수 완료**: 9가지 네트워크 에러 시나리오 + 4가지 upstream 시나리오 추적 확인. 모든 경로에서 사용자 친화적 메시지 표시 및 버튼 비활성화 해제 보장 확인.

- **검증**: `gradlew build` PASSED. 기존 191 + 신규 31 = 222개 테스트 전부 PASSED.

### Step 2+3: 설정 완성 + 터미널에서 열기 + Undo 최근 커밋

(테마 토글은 Step 1에서 구현 완료. Step 2 잔여 항목과 Step 3을 합침.)

- 설정 다이얼로그: **외부 터미널 설정** ComboBox 추가.
  - `settings.fxml`: 터미널 ComboBox (CMD/PowerShell/Windows Terminal).
  - `SettingsController`: `terminalComboBox` 바인딩, `TERMINAL_OPTIONS` 정의, 로드/저장.
  - `AppConfig.externalTerminal` 이미 존재 → UI만 연결.

- 컨텍스트 메뉴: **"터미널에서 열기"** 추가.
  - Unstaged/Staged 파일 우클릭 메뉴에 "터미널에서 열기" 항목 추가.
  - 사이드바 레포 우클릭 메뉴에 "탐색기에서 열기" + "터미널에서 열기" 항목 추가.
  - `RepoListCell` 생성자 확장: `onOpenExplorer`, `onOpenTerminal` 콜백 추가.
  - `MainController.openTerminalAt(String)`: 설정의 externalTerminal에 따라 cmd/powershell/wt 실행.

- **Undo 최근 커밋** (git reset --soft HEAD~1).
  - `GitCommandBuilder.soft()`: `--soft` 옵션 추가.
  - `GitService.resetSoft(Path)`: `git reset --soft HEAD~1` 실행.
  - 커밋 히스토리 ListView 우클릭 → "최근 커밋 취소 (soft reset)" 메뉴.
    - 첫 번째(최신) 커밋만 활성화, 나머지는 비활성.
    - 확인 다이얼로그: 커밋 hash+메시지 표시 + "변경 사항은 스테이징 상태로 유지" 안내.
    - 실행 후 refreshRepoDetail 자동 갱신.

- 3개 신규 테스트: `git_reset_soft_HEAD` (빌더), `resetSoft_성공`, `resetSoft_실패시_예외`.

- **검증**: `gradlew build` PASSED. 225개 테스트 전부 PASSED.

### Step 4: 창 상태 저장 검증

- 코드 확인: `GitMiniApp.stop()` → 디스크 최신 config 로드 후 창 크기/위치만 갱신 저장 (다른 모듈 데이터 보존).
- 코드 확인: `GitMiniApp.start()` → `Scene` 생성 시 `windowWidth/Height` 사용, `windowX/Y >= 0`이면 위치 복원.
- 사용자 검증: 창 줄이기/이동 후 재시작 → 복원 확인. 최대화 종료 → 전체 크기로 재시작 확인.

### Step 5: jpackage 빌드

- `build.gradle.kts`: `jpackageImage` 태스크 추가.
  - `installDist` 의존 → `build/install/GitMini/lib/` 전체 JAR을 `--input`으로 전달.
  - `--type app-image` → 포터블 디렉토리 (설치 프로그램 없이 바로 실행 가능).
  - `--main-jar`, `--main-class`, `--name`, `--app-version`, `--vendor`, `--description`.
  - `--java-options --add-opens` (JavaFX non-modular 호환).
  - 아이콘 파일(`src/main/resources/icons/gitmini.ico`)이 있으면 자동 적용.
  - 버전 문자열에서 SNAPSHOT 등 비숫자 제거 (jpackage 요구사항).
- **검증**: `gradlew jpackageImage` → `build/jpackage/GitMini/GitMini.exe` 생성 확인.
  - 출력: `GitMini.exe` (437KB) + `app/` (JAR들) + `runtime/` (JRE 내장).

### 후속 수정

- **터미널 열기 버그**: ProcessBuilder → Runtime.exec 교체 (사이드바 터미널 열기 안 되는 문제).
- **Windows Terminal PATH**: `wt` 직접 호출 → `cmd /c start wt` 경유 (Store 앱 PATH 이슈).

---

## 3. 종료

**Phase 5 완료.** 2026-02-09.

- **검증**: `gradlew build` 225개 PASSED. `gradlew jpackageImage` → `GitMini.exe` 생성 성공.
- **완료 요약**: Step 1~5 + 후속 수정 2건.
  - Step 1: 에러 핸들링 강화 (ErrorMessages 유틸리티 31개 테스트) + 테마 토글 + Clone 경로 검증
  - Step 2+3: 외부 터미널 설정 + 터미널에서 열기 (파일/레포) + Undo 최근 커밋 (soft reset)
  - Step 4: 창 상태 저장/복원 검증
  - Step 5: jpackage 포터블 .exe 빌드
- **총 테스트**: 225개 PASSED.
- **결과물**: 상용 수준 완성. 배포 가능.
