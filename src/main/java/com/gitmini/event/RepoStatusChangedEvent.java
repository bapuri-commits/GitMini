package com.gitmini.event;

/**
 * 레포지토리 상태가 변경되었을 때 발행되는 이벤트.
 * 구독자는 해당 레포의 UI를 갱신해야 한다.
 *
 * @param repoPath 변경된 레포의 절대 경로
 */
public record RepoStatusChangedEvent(String repoPath) {
}
