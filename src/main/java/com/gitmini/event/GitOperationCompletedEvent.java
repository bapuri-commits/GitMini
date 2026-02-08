package com.gitmini.event;

/**
 * Git 작업이 완료되었을 때 발행되는 이벤트.
 * Command Log 갱신 등에 사용된다.
 *
 * @param repoPath  작업 대상 레포의 절대 경로
 * @param operation 작업 종류 (예: "push", "commit", "pull")
 * @param success   성공 여부
 * @param message   결과 메시지 (성공 시 요약, 실패 시 에러 메시지)
 */
public record GitOperationCompletedEvent(
        String repoPath,
        String operation,
        boolean success,
        String message
) {
}
