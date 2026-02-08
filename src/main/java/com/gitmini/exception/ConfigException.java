package com.gitmini.exception;

/**
 * 설정 파일 읽기/쓰기 실패 시 발생하는 예외.
 */
public class ConfigException extends GitMiniException {

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
