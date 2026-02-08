# GitMini — 개발 방법론

> **"계층별 점진적 검증"**  
> AI가 검증할 수 있는 범위를 최대화하고, 사용자가 검증해야 하는 범위를 최소화 + 명확화한다.

---

## 1. 역할 분담

### AI가 검증 가능한 것
- 코드 작성
- 컴파일 체크 (`gradlew build`)
- 단위 테스트 실행 (`gradlew test`)
- 린트 에러 확인

### 사용자가 검증해야 하는 것
- GUI 시각적 확인 (레이아웃, 테마, 반응성)
- 실제 git 레포에 대한 통합 동작
- 사용감, 응답 속도 체감

---

## 2. 구현 순서: Bottom-Up

```
UI (Controller + FXML)         ← 마지막에 구현, 사용자가 검증
        ↓ 의존
Service (비즈니스 로직)         ← 단위 테스트로 AI가 검증
        ↓ 의존
Infrastructure (git/, config/) ← 단위 테스트로 AI가 검증
        ↓ 의존
Model (순수 데이터)             ← 컴파일만으로 검증 가능
```

아래쪽 계층일수록 테스트가 쉽고 자동 검증이 가능하다.
위쪽(UI)은 사용자의 눈으로 확인해야 한다.

---

## 3. Step 단위 진행

Phase를 한 번에 통째로 만들지 않는다. 작은 Step으로 쪼개고, 매 Step마다:

```
[Step 작업] → [컴파일 확인] → [테스트 실행] → [다음 Step]
```

한 Step에서 문제가 생기면 거기서 잡는다. 다음 Step으로 넘어가지 않는다.

---

## 4. 테스트 전략

| 계층 | 테스트 방법 | 담당 |
|------|------------|------|
| `model/` | 컴파일 확인 | AI (자동) |
| `git/parser/` | 단위 테스트 — 실제 git 출력 문자열을 파싱 검증 | AI (자동) |
| `git/GitExecutor` | 단위 테스트 — 타임아웃, 에러 처리 | AI (자동) |
| `config/` | 단위 테스트 — JSON 직렬화/역직렬화, 파일 I/O | AI (자동) |
| `service/` | 단위 테스트 — GitExecutor 모킹, 서비스 로직 | AI (자동) |
| `event/`, `async/` | 단위 테스트 — 발행/구독, 콜백 동작 | AI (자동) |
| `controller/` + FXML | 수동 테스트 — 체크포인트 체크리스트 | 사용자 |

### 테스트 실행 명령어

```bash
# 전체 빌드 + 테스트
gradlew build

# 테스트만 실행
gradlew test

# 특정 테스트 클래스 실행
gradlew test --tests "com.gitmini.config.ConfigManagerTest"

# 앱 실행 (UI 확인)
gradlew run
```

---

## 5. 사용자 체크포인트

각 Phase가 끝나면 체크포인트 체크리스트를 제공한다.
사용자가 모든 항목을 확인하고, 문제 없으면 다음 Phase로 넘어간다.

### Phase 1 체크포인트

```
□ gradlew build 성공하는가?
□ gradlew test 성공하는가?
□ gradlew run 실행 시 창이 뜨는가?
□ 사이드바 + 메인 영역이 보이는가?
□ 하단 상태바가 보이는가?
□ AtlantaFX 테마가 적용되어 보이는가? (다크 테마)
□ %APPDATA%/GitMini/ 폴더가 생성되는가?
□ %APPDATA%/GitMini/config.json 파일이 존재하는가?
□ 창 닫고 다시 열면 정상 동작하는가?
```

---

## 6. Phase별 진행 흐름

```
① 설계 제시
   "Phase N에서 이런 Step들로 구현하겠습니다"
   → 사용자 확인/수정
          ↓
② Step별 구현
   각 Step마다:
   - 코드 작성
   - 컴파일 확인 (gradlew build)
   - 단위 테스트 작성 + 실행 (해당되는 경우)
   - 린트 확인
          ↓
③ 자동 검증
   - gradlew clean build → 컴파일 성공
   - gradlew test → 모든 테스트 PASSED
          ↓
④ 코드 리뷰 리포트 (AI 자체 검수)
   - 전체 코드를 재읽기하여 품질 검증
   - 발견된 문제는 즉시 수정
   - 리포트 항목:
     a. 핵심 설계 결정 목록 + 근거
     b. DESIGN.md 대비 정합성 점검
     c. 발견된 버그/누락/코드 위생 문제 + 수정 내역
     d. 다음 Phase에서 주의할 점
          ↓
⑤ 체크포인트 체크리스트 제공
   "이 항목들을 확인해주세요"
   → 사용자 확인
          ↓
⑥ 문제 있으면 수정, 없으면 다음 Phase
```

---

## 7. 문제 발생 시 대응

1. 관련 테스트 확인 → 테스트 통과하면 UI 바인딩 문제
2. Controller 코드에서 이벤트 연결 확인
3. 로그 확인 요청 (`%APPDATA%/GitMini/logs/gitmini.log`)
4. 수정 → 컴파일 → 테스트 → 재확인 요청

---

## 8. 핵심 원칙 요약

| 원칙 | 설명 |
|------|------|
| **Bottom-Up** | Model → Infrastructure → Service → UI 순서 |
| **Step 단위** | 매 Step마다 컴파일 + 테스트 |
| **자동 검증 최대화** | UI 아래 모든 계층에 단위 테스트 |
| **체크포인트** | Phase 끝날 때 사용자 검증용 체크리스트 |
| **로그 기반 디버깅** | 문제 시 로그 파일로 원인 추적 |
| **Fix-before-proceed** | 현재 Phase 안정될 때까지 다음으로 안 넘어감 |
