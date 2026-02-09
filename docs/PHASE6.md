# Phase 6: 확장

> **상태**: 완료  
> **이전 Phase**: Phase 5 완료, 225개 테스트 PASSED, jpackage .exe 빌드 완료  
> **기준 문서**: DESIGN.md §Phase 6

---

## 1. 계획

### 목표 (DESIGN.md §Phase 6 — 선택적 확장)

| 작업 | 상태 |
|------|------|
| 원클릭 워크플로우 (Commit + Push) | **보류** — 실사용 시 필요성 낮음 |
| 시스템 트레이 — 앱 최소화 → 트레이 상주, 알림 | 구현 대상 |
| 커밋 메시지 히스토리 — 최근 메시지 드롭다운 | 구현 대상 |
| 파일 필터 — 변경 파일 목록 검색 | **이미 구현됨** — 검증만 |
| Stash — save / pop / list | **보류** |
| 기본 Merge — fast-forward, 충돌 시 외부 에디터 | **보류** |

### Step 분해 (3단계)

```
Step 1: 커밋 메시지 히스토리
        — AppConfig에 최근 커밋 메시지 목록 저장 (최대 20개).
        — 커밋 성공 시 메시지 자동 저장. 중복 제거, 최신 우선.
        — UI: 커밋 영역에 히스토리 드롭다운 버튼. 선택 시 메시지 자동 입력.
        — 테스트 작성.

Step 2: 시스템 트레이
        — java.awt.SystemTray + TrayIcon. Platform.setImplicitExit(false).
        — X 버튼 → 트레이로 최소화 (종료 확인 대신). 트레이 메뉴: 열기/종료.
        — 자동 Fetch 후 변경 감지 시 트레이 알림.
        — 설정에 트레이 사용 여부 옵션 추가.

Step 3: 파일 필터 검증 + Phase 6 마무리
        — 이미 구현된 파일 필터(unstagedSearchField/stagedSearchField) 동작 검증.
        — 전체 코드 감사 (DESIGN.md 대조).
        — DESIGN.md Phase 6 체크리스트 갱신. Phase 6 문서 마무리.
```

### 워크플로 (Phase 3~5와 동일)

1. 각 Step: **구현 → 빌드 검증(`gradlew build`) → 코드 감사 → 커밋** (`feat(phase6): Step N <내용>`).
2. Step 종료 시 AI가 **체크리스트**를 제시 → 사용자가 앱 실행 후 확인·피드백.
3. DESIGN.md 기준으로 코드 감사.
4. 과정은 본 문서 §2에 기록, 종료 시 §3 갱신.

---

## 2. 과정

*(Step 진행 시마다 기록)*

### Step 1: 커밋 메시지 히스토리

- `AppConfig.java`: `commitMessageHistory` 필드 추가 (`Map<String, List<String>>`, 레포 경로 → 최근 메시지 목록).
  - 기본값 빈 맵. `validate()`에서 null 복구. setter null 방어.

- `main.fxml`: 커밋 영역 HBox에 `MenuButton commitMsgHistoryBtn` 추가 ("최근 메시지 ▾").
  - `<?import javafx.scene.control.MenuButton?>` 추가.

- `MainController.java`:
  - `MAX_COMMIT_MSG_HISTORY = 20` 상수.
  - `setupCommitMsgHistory()`: 메뉴 열릴 때마다 `updateCommitMsgHistoryMenu()` 호출 (showingProperty 리스너).
  - `updateCommitMsgHistoryMenu()`: 선택된 레포의 히스토리만 표시. 첫 줄 60자 축약. 히스토리가 있으면 하단에 구분선 + "히스토리 지우기" 메뉴 추가.
  - `saveCommitMessageToHistory(message)`: `onCommit` 성공 콜백에서 호출. `message.strip()` 후 저장 (중복 제거, 최신 우선, 최대 20개).
  - `removeCommitMessageFromHistory(message)`: `onUndoLastCommit` 성공 시 호출. 취소된 커밋 메시지를 히스토리에서 제거 + 입력란에 복원.
  - `clearCommitMessageHistory()`: 선택된 레포의 히스토리 전체 삭제.
  - `getRepoHistoryKey()`: 선택된 레포 경로 반환 (모든 히스토리 메서드의 키).

- 테스트 8개 추가 (ConfigManagerTest):
  - 기본값 빈 맵, 레포별 저장/로드, null validate, null setter 방어, 구버전 JSON 호환, 구버전 List→Map 하위 호환, 레포별 중복제거/순서보존, 레포 삭제 시 해당 레포만 제거.

- **버그 수정**:
  - TextArea 후행 공백/줄바꿈으로 중복 비교 실패 → `message.strip()` 적용.
  - 커밋 취소 시 히스토리에 유령 항목 잔류 → 취소 시 제거 + 입력란 복원.
  - 전역 히스토리 → 레포별 분리 (`List<String>` → `Map<String, List<String>>`).

- **검증**: `gradlew build` 233개 PASSED.

### Step 2: 시스템 트레이

- `AppConfig.java`: `minimizeToTray` 필드 추가 (boolean, 기본값 true).

- `GitMiniApp.java`:
  - `setupSystemTray()`: 설정 확인 + `SystemTray.isSupported()` 확인 후 트레이 아이콘 등록.
  - `Platform.setImplicitExit(false)`: 창 닫아도 JavaFX 종료하지 않음.
  - `createTrayImage()`: 16x16 프로그래밍 생성 아이콘 (파란색 배경 + 흰색 'G').
  - `onCloseRequest` 변경: 트레이 활성 시 hide, 비활성 시 기존 종료 확인 다이얼로그.
  - `showMainWindow()`: 트레이에서 창 복원 (show + toFront + deIconify).
  - `exitApplication()`: 트레이 제거 → Platform.exit() → stop() 정상 종료 흐름.
  - `saveWindowState()`: stop()에서 추출. 창이 숨겨진 상태에서는 저장 안 함.
  - `showTrayNotification(title, message)`: static 메서드. 창 숨김 상태에서만 알림 표시.
  - `removeTrayIcon()`: stop()에서 호출. 트레이 아이콘 정리.
  - 트레이 메뉴: "GitMini 열기", "종료". 더블클릭 → 열기.

- `settings.fxml` + `SettingsController.java`:
  - "닫기 시 트레이로 최소화 (백그라운드 Fetch 유지)" 체크박스 추가.
  - 로드/저장 연동. 변경 시 앱 재시작 필요.

- `MainController.java`:
  - `doAutoFetch()`: Fetch 성공 후 behind > 0이면 `GitMiniApp.showTrayNotification()` 호출.
  - 창이 보이는 상태에서는 알림 안 표시 (showTrayNotification 내부에서 필터).

- **검증**: `gradlew build` 233개 PASSED.

### Step 3: 파일 필터 검증 + Phase 6 마무리

- 이미 구현된 파일 필터 검증:
  - `unstagedSearchField` / `stagedSearchField`: TextArea 실시간 필터링 (대소문자 무시, 부분 일치).
  - `applyFilterUnstaged()` / `applyFilterStaged()`: null/blank 안전, 빈 검색어 시 전체 목록 복원.
  - 정상 동작 확인.

- 전체 코드 감사: DESIGN.md §Phase 6 요구사항 전부 대조 완료.

---

## 3. 종료

**Phase 6 완료.** 2026-02-09.

- **검증**: `gradlew build` 233개 PASSED.
- **완료 요약**: Step 1~2 + Step 3(검증/마무리).
  - Step 1: 커밋 메시지 히스토리 (레포별 분리, 드롭다운, 지우기, 커밋 취소 연동, strip 중복 수정)
  - Step 2: 시스템 트레이 (트레이 아이콘, X→최소화, 열기/종료 메뉴, 더블클릭 복원, 자동 Fetch 알림, 설정 토글)
  - Step 3: 파일 필터 검증 (이미 구현됨, 동작 확인)
- **총 테스트**: 233개 PASSED (Phase 5 대비 +8).
- **보류 항목**: 원클릭 워크플로우, Stash, 기본 Merge.
