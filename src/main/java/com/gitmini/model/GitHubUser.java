package com.gitmini.model;

/**
 * GitHub 사용자 정보.
 * <p>
 * {@code GET /user} API 응답에서 필요한 필드만 매핑한다.
 * 토큰 유효성 검증 결과로 사용되며, 설정 UI에서 "인증된 사용자" 표시에 활용한다.
 * </p>
 *
 * @param login     사용자 로그인 이름 (예: "octocat")
 * @param name      표시 이름 (nullable, 예: "The Octocat")
 * @param avatarUrl 프로필 이미지 URL (nullable)
 */
public record GitHubUser(
        String login,
        String name,
        String avatarUrl
) {
    /**
     * 표시용 이름을 반환한다.
     * name이 있으면 "name (login)", 없으면 login만 반환한다.
     */
    public String displayName() {
        if (name != null && !name.isBlank()) {
            return name + " (" + login + ")";
        }
        return login;
    }
}
