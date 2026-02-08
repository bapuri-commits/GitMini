package com.gitmini.model;

/**
 * 브랜치 정보.
 *
 * @param name            브랜치 이름
 * @param trackingBranch  추적 중인 원격 브랜치 (예: "origin/main"), 없으면 null
 * @param ahead           원격 대비 앞선 커밋 수
 * @param behind          원격 대비 뒤처진 커밋 수
 * @param current         현재 체크아웃된 브랜치인지 여부
 */
public record BranchInfo(
        String name,
        String trackingBranch,
        int ahead,
        int behind,
        boolean current
) {
}
