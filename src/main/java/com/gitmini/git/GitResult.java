package com.gitmini.git;

/**
 * Git CLI 실행 결과.
 * <p>
 * GitExecutor가 ProcessBuilder로 git 명령을 실행한 뒤 반환하는 불변 결과 객체.
 * exit code, stdout, stderr를 분리하여 보관한다.
 * </p>
 *
 * @param command    실행된 명령어 문자열 (예: "git status --porcelain")
 * @param exitCode   프로세스 종료 코드 (0 = 성공, -1 = 프로세스 실행 자체 실패)
 * @param stdout     표준 출력
 * @param stderr     표준 에러 출력
 * @param durationMs 실행 소요 시간 (밀리초)
 */
public record GitResult(
        String command,
        int exitCode,
        String stdout,
        String stderr,
        long durationMs
) {
    /**
     * 명령이 성공했는지 여부 (exit code == 0).
     */
    public boolean isSuccess() {
        return exitCode == 0;
    }

    @Override
    public String toString() {
        return String.format("GitResult{cmd='%s', exit=%d, duration=%dms, stdout=%d chars, stderr=%d chars}",
                command, exitCode, durationMs,
                stdout != null ? stdout.length() : 0,
                stderr != null ? stderr.length() : 0);
    }
}
