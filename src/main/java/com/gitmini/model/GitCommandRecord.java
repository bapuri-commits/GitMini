package com.gitmini.model;

import java.time.LocalDateTime;

/**
 * 실행된 Git 명령어의 기록 (Command Log용).
 *
 * @param command    실행된 전체 명령어 문자열 (예: "git status --porcelain")
 * @param repoPath   작업 디렉토리 절대 경로 (어떤 레포에서 실행했는지 식별용)
 * @param timestamp  실행 시각
 * @param success    성공 여부
 * @param durationMs 실행 소요 시간 (밀리초)
 * @param output     실행 결과 (stdout, 실패 시 stderr)
 */
public record GitCommandRecord(
        String command,
        String repoPath,
        LocalDateTime timestamp,
        boolean success,
        long durationMs,
        String output
) {
}
