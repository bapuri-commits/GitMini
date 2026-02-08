package com.gitmini.exception;

/**
 * GitMini 애플리케이션의 최상위 예외.
 * 모든 커스텀 예외는 이 클래스를 상속한다.
 */
public class GitMiniException extends RuntimeException {

    public GitMiniException(String message) {
        super(message);
    }

    public GitMiniException(String message, Throwable cause) {
        super(message, cause);
    }
}
