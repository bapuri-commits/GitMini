package com.gitmini.exception;

/**
 * Git CLI 출력 파싱 실패 시 발생하는 예외.
 * 파싱에 실패한 원본 출력을 포함한다.
 */
public class GitParseException extends GitMiniException {

    private final String rawOutput;

    public GitParseException(String message, String rawOutput) {
        super(message);
        this.rawOutput = rawOutput;
    }

    public GitParseException(String message, String rawOutput, Throwable cause) {
        super(message, cause);
        this.rawOutput = rawOutput;
    }

    public String getRawOutput() {
        return rawOutput;
    }
}
