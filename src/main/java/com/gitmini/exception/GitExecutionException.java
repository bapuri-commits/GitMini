package com.gitmini.exception;

/**
 * Git CLI 프로세스 실행 실패 시 발생하는 예외.
 * exit code, stderr 정보를 포함한다.
 */
public class GitExecutionException extends GitMiniException {

    private final int exitCode;
    private final String stderr;

    public GitExecutionException(String message, int exitCode, String stderr) {
        super(message);
        this.exitCode = exitCode;
        this.stderr = stderr;
    }

    public GitExecutionException(String message, Throwable cause) {
        super(message, cause);
        this.exitCode = -1;
        this.stderr = "";
    }

    public int getExitCode() {
        return exitCode;
    }

    public String getStderr() {
        return stderr;
    }
}
