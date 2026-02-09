package com.gitmini.model;

/**
 * GitHub 레포지토리 정보.
 * <p>
 * {@code GET /user/repos} API 응답에서 필요한 필드만 매핑한다.
 * Clone UI에서 레포 목록 표시, 레포 생성 결과 등에 사용한다.
 * </p>
 *
 * @param fullName    "owner/repo" 형태의 전체 이름
 * @param cloneUrl    HTTPS clone URL (예: "https://github.com/owner/repo.git")
 * @param description 레포 설명 (nullable)
 * @param isPrivate   Private 레포 여부
 * @param defaultBranch 기본 브랜치 이름 (예: "main")
 */
public record GitHubRepo(
        String fullName,
        String cloneUrl,
        String description,
        boolean isPrivate,
        String defaultBranch
) {
    /**
     * 표시용 문자열을 반환한다.
     * Private 레포는 앞에 🔒 표시를 붙인다.
     */
    public String displayName() {
        return (isPrivate ? "\uD83D\uDD12 " : "") + fullName;
    }
}
