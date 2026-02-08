# GitMini — 미해결 이슈 (Phase 3 진행 중)

> **작성일**: 2026-02-08  
> **현재 상태**: Step 8 완료, Step 9~13 대기  
> **153개 테스트 PASSED**

---

## 긴급 수정 필요 (다음 세션에서 즉시)

### 1. 큰 레포 (600+ 파일) — 브랜치 전환/선택 시 UI 프리즈

**증상**: 651개 변경 파일이 있는 레포에서 브랜치 전환 시 UI가 멈추고 응답 불가.  
**원인**: `refreshRepoDetail` 성공 콜백에서 651개 `FileChange`를 ListView에 한번에 `setItems()`. ListView의 `FileChangeListCell`이 수백 개 생성되면서 FX 스레드 블로킹.  
**수정 방안**:
- 파일 목록에 최대 표시 개수 제한 (예: 200개). 초과 시 "N개 파일 더 있음..." 표시.
- 또는 ListView의 VirtualFlow가 원래 셀 재사용을 하므로, 이론적으로는 괜찮아야 함. 실제 원인이 ListView가 아닌 `git status --porcelain`의 출력 파싱 시간일 수도 있으므로 프로파일링 필요.
- `refreshRepoDetail`의 백그라운드 작업에서 status 결과가 N개 이상이면 잘라서 반환하는 방법도 가능.

### 2. 원격 없는 레포 — Push 에러 메시지가 비직관적

**증상**: 원격이 없는 레포에서 Push → 에러 다이얼로그에 git의 raw 영어 에러 메시지가 그대로 표시됨.  
**수정 방안**:
- `onPush/onPull/onFetch` 에러 콜백에서 `error.getMessage()`를 직접 표시하는 대신, 주요 에러를 한국어로 매핑.
- 예: "no upstream configured" → "원격 브랜치가 설정되지 않았습니다."
- 예: "Could not resolve host" → "네트워크 연결을 확인하세요."

### 3. 원격 없는 레포 — 사이드바에 원격 상태 표시

**요청**: 원격이 없는 레포는 사이드바에 "(원격 없음)" 같은 표시가 있으면 좋겠다.  
**수정 방안**:
- `Repository` 모델에 `hasRemote` 필드 추가, 또는 `trackingBranch`가 없으면 원격 없음으로 판단.
- `RepoListCell`에서 ahead/behind 대신 "(원격 없음)" 표시.

---

## Step 8 체크리스트 — 미통과 항목

```
□ 전환 실패 시 (uncommitted 변경 있을 때) 에러 다이얼로그가 뜨고 ComboBox가 이전 브랜치로 복원되는가?
  → 에러는 뜨지만 UI 프리즈로 확인 불가 (BUG: 큰 레포)
□ 원격이 없는 레포에서 Push → 에러 메시지가 사용자 친화적인가?
  → 에러는 뜨지만 영어 raw 메시지 (개선 필요)
```

---

## 남은 Step (Phase 3)

| Step | 내용 | 상태 |
|------|------|------|
| 9 | 커밋 히스토리 | 대기 |
| 10 | Command Log 패널 | 대기 |
| 11 | 상태바 + 비동기 피드백 + 토스트 | 대기 |
| 12 | 컨텍스트 메뉴 + 키보드 단축키 | 대기 |
| 13 | 드래그 & 드롭 + 자동 Fetch | 대기 |

---

## Phase 3 완료된 Step

| Step | 내용 | 커밋 |
|------|------|------|
| 1-2 bugfix | GitCommandRecord repoPath, RepositoryManager 캐시 | `54042c7` (노트북) |
| 3 | main.fxml 레이아웃 재설계 | `7d48421` |
| 4 | 사이드바 (RepoListCell, 추가/제거/선택) | `7d48421` |
| 5 | 파일 변경 영역 (FileChangeListCell, Stage/Unstage) | `2e0439e` |
| 6 | Diff 뷰어 (DiffRenderer, 비동기 로드) | `109df24` |
| 7 | 커밋 (Commit/Amend, Ctrl+Enter) | `a8e1bb4` |
| 8 | 액션 바 (Push/Pull/Fetch, 브랜치 전환/생성) + 긴급 버그 수정 | `a8e1bb4` |
