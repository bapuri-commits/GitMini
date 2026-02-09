package com.gitmini.exception;

/**
 * GitHub REST API 호출 실패 시 발생하는 예외.
 * <p>
 * HTTP 상태 코드와 응답 본문을 포함하여, 상위 계층(Controller)이
 * 사용자에게 의미 있는 에러 메시지를 구성할 수 있도록 한다.
 * </p>
 */
public class GitHubApiException extends GitMiniException {

    private final int statusCode;
    private final String responseBody;

    /**
     * HTTP 응답 기반 예외.
     *
     * @param message      사람이 읽을 수 있는 에러 메시지
     * @param statusCode   HTTP 상태 코드 (예: 401, 403, 404)
     * @param responseBody 응답 본문 (디버깅용)
     */
    public GitHubApiException(String message, int statusCode, String responseBody) {
        super(message);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    /**
     * 네트워크/IO 오류 기반 예외.
     *
     * @param message 에러 메시지
     * @param cause   원인 예외
     */
    public GitHubApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
        this.responseBody = "";
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    /**
     * 인증 실패(401) 여부.
     */
    public boolean isUnauthorized() {
        return statusCode == 401;
    }

    /**
     * 권한 부족 또는 Rate Limit(403) 여부.
     */
    public boolean isForbidden() {
        return statusCode == 403;
    }

    /**
     * 네트워크 오류 (HTTP 응답을 받지 못한 경우) 여부.
     */
    public boolean isNetworkError() {
        return statusCode == -1;
    }
}
