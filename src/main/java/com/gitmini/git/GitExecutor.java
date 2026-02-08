package com.gitmini.git;

import com.gitmini.exception.GitExecutionException;
import com.gitmini.model.GitCommandRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Git CLI 실행 엔진 — ProcessBuilder 래퍼.
 * <p>
 * DESIGN.md의 방어 조치를 모두 적용한다:
 * <ul>
 *   <li>LC_ALL=C 환경 변수 강제 → 출력 언어 통일 (파싱 안정성)</li>
 *   <li>GIT_TERMINAL_PROMPT=0 → 대화형 프롬프트 비활성화</li>
 *   <li>타임아웃 설정 → 무한 대기 방지 (기본 30초, 네트워크 120초)</li>
 *   <li>stdout/stderr 분리 → 에러 메시지 정확한 포착</li>
 *   <li>exit code 기반 성공/실패 판단</li>
 *   <li>git 설치/버전 확인</li>
 * </ul>
 * </p>
 *
 * <h3>Command Log</h3>
 * <p>
 * 모든 명령 실행을 {@link GitCommandRecord}로 기록하며,
 * 리스너를 등록하면 실시간으로 알림을 받을 수 있다.
 * </p>
 */
public class GitExecutor {

    private static final Logger log = LoggerFactory.getLogger(GitExecutor.class);

    /** 로컬 작업 기본 타임아웃 (초). */
    public static final long DEFAULT_TIMEOUT_SECONDS = 30;

    /** 네트워크 작업 타임아웃 (초) — push, pull, fetch, clone. */
    public static final long NETWORK_TIMEOUT_SECONDS = 120;

    /** git 최소 지원 버전 (git switch 등 최신 명령어 호환). */
    private static final String MIN_GIT_VERSION = "2.23.0";

    /** 스트림 읽기 대기 타임아웃 (초). */
    private static final long STREAM_READ_TIMEOUT_SECONDS = 10;

    private final List<GitCommandRecord> commandHistory = new CopyOnWriteArrayList<>();
    private final List<Consumer<GitCommandRecord>> commandListeners = new CopyOnWriteArrayList<>();

    // ========== 명령 실행 ==========

    /**
     * 기본 타임아웃(30초)으로 git 명령을 실행한다.
     *
     * @param workingDir 작업 디렉토리 (레포 경로)
     * @param command    실행할 명령어 리스트 (예: ["git", "status", "--porcelain"])
     * @return 실행 결과
     * @throws GitExecutionException 프로세스 실행 실패 또는 타임아웃
     */
    public GitResult execute(Path workingDir, List<String> command) {
        return execute(workingDir, command, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * 지정된 타임아웃으로 git 명령을 실행한다.
     *
     * @param workingDir     작업 디렉토리
     * @param command        실행할 명령어 리스트
     * @param timeoutSeconds 타임아웃 (초)
     * @return 실행 결과
     * @throws GitExecutionException 프로세스 실행 실패 또는 타임아웃
     */
    public GitResult execute(Path workingDir, List<String> command, long timeoutSeconds) {
        String commandString = String.join(" ", command);
        log.debug("Git 명령 실행: {} (dir: {}, timeout: {}s)", commandString, workingDir, timeoutSeconds);

        long startTime = System.currentTimeMillis();

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workingDir.toFile());

            // 방어 조치: 환경 변수 설정
            Map<String, String> env = pb.environment();
            env.put("LC_ALL", "C");                    // 출력 언어 영어 고정 → 파싱 안정성
            env.put("GIT_TERMINAL_PROMPT", "0");       // 대화형 프롬프트 비활성화

            Process process = pb.start();

            // stdout/stderr를 별도 스레드로 읽기 (데드락 방지)
            CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(
                    () -> readStream(process.getInputStream()));
            CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(
                    () -> readStream(process.getErrorStream()));

            // 타임아웃 대기
            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                long durationMs = System.currentTimeMillis() - startTime;
                String timeoutMsg = String.format("타임아웃: %d초 초과", timeoutSeconds);
                GitResult result = new GitResult(commandString, -1, "", timeoutMsg, durationMs);
                recordCommand(result, workingDir);
                throw new GitExecutionException(
                        String.format("Git 명령 타임아웃 (%d초): %s", timeoutSeconds, commandString),
                        -1, timeoutMsg);
            }

            // 스트림 읽기 (타임아웃 포함, 읽기 실패 시에도 부분 결과 보존)
            String stdout;
            String stderr;
            try {
                stdout = stdoutFuture.get(STREAM_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                log.warn("stdout 읽기 타임아웃: {}", commandString);
                stdout = stdoutFuture.getNow("");
            }
            try {
                stderr = stderrFuture.get(STREAM_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                log.warn("stderr 읽기 타임아웃: {}", commandString);
                stderr = stderrFuture.getNow("");
            }

            int exitCode = process.exitValue();
            long durationMs = System.currentTimeMillis() - startTime;

            GitResult result = new GitResult(commandString, exitCode, stdout, stderr, durationMs);
            recordCommand(result, workingDir);

            if (exitCode != 0) {
                log.debug("Git 명령 실패: {} (exit={}, stderr={})", commandString, exitCode, stderr);
            } else {
                log.debug("Git 명령 완료: {} ({}ms)", commandString, durationMs);
            }

            return result;

        } catch (GitExecutionException e) {
            throw e; // 이미 처리된 예외 (타임아웃 등)
        } catch (IOException e) {
            long durationMs = System.currentTimeMillis() - startTime;
            GitResult result = new GitResult(commandString, -1, "", e.getMessage(), durationMs);
            recordCommand(result, workingDir);
            throw new GitExecutionException(
                    "Git 프로세스 실행 실패: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitExecutionException(
                    "Git 명령 실행 중 인터럽트: " + commandString, e);
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            GitResult result = new GitResult(commandString, -1, "", e.getMessage(), durationMs);
            recordCommand(result, workingDir);
            throw new GitExecutionException(
                    "Git 명령 실행 중 오류: " + e.getMessage(), e);
        }
    }

    // ========== Git 설치/버전 확인 ==========

    /**
     * git 버전 문자열을 반환한다 (예: "2.39.0").
     * git이 설치되지 않았거나 확인 실패 시 null을 반환한다.
     */
    public String getGitVersion() {
        try {
            GitResult result = execute(
                    Path.of("."),
                    List.of("git", "--version"),
                    DEFAULT_TIMEOUT_SECONDS);
            if (result.isSuccess()) {
                // "git version 2.39.0.windows.1" → "2.39.0.windows.1"
                String output = result.stdout().trim();
                if (output.startsWith("git version ")) {
                    return output.substring("git version ".length()).trim();
                }
                return output;
            }
            return null;
        } catch (Exception e) {
            log.warn("Git 버전 확인 실패", e);
            return null;
        }
    }

    /**
     * git이 설치되어 있는지 확인한다.
     */
    public boolean isGitInstalled() {
        return getGitVersion() != null;
    }

    /**
     * git 버전이 최소 요구 버전(2.23.0) 이상인지 확인한다.
     */
    public boolean isGitVersionSupported() {
        String version = getGitVersion();
        if (version == null) return false;
        return compareVersions(version, MIN_GIT_VERSION) >= 0;
    }

    /**
     * 두 버전 문자열을 비교한다.
     * <p>
     * "2.39.0.windows.1" vs "2.23.0" 같은 형식을 처리한다.
     * 숫자가 아닌 부분(windows 등)은 0으로 취급한다.
     * </p>
     *
     * @return 양수(v1 > v2), 0(같음), 음수(v1 < v2)
     */
    static int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("[.\\-]");
        String[] parts2 = v2.split("[.\\-]");
        int len = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < len; i++) {
            int n1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
            int n2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;
            if (n1 != n2) return Integer.compare(n1, n2);
        }
        return 0;
    }

    private static int parseVersionPart(String part) {
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            return 0; // "windows" 등 비숫자 부분은 0으로 취급
        }
    }

    // ========== 스트림 읽기 ==========

    private String readStream(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            log.warn("스트림 읽기 실패", e);
            return "";
        }
    }

    // ========== Command Log ==========

    private void recordCommand(GitResult result, Path workingDir) {
        String repoPath = workingDir != null
                ? workingDir.toAbsolutePath().normalize().toString()
                : "";
        GitCommandRecord record = new GitCommandRecord(
                result.command(),
                repoPath,
                LocalDateTime.now(),
                result.isSuccess(),
                result.durationMs(),
                result.isSuccess() ? result.stdout() : result.stderr()
        );
        commandHistory.add(record);

        for (Consumer<GitCommandRecord> listener : commandListeners) {
            try {
                listener.accept(record);
            } catch (Exception e) {
                log.error("Command listener 오류", e);
            }
        }
    }

    /**
     * 명령 실행 리스너를 등록한다. 모든 git 명령 실행 시 호출된다.
     * Command Log UI에서 사용한다.
     */
    public void addCommandListener(Consumer<GitCommandRecord> listener) {
        commandListeners.add(Objects.requireNonNull(listener, "listener는 null일 수 없습니다"));
    }

    /**
     * 명령 실행 리스너를 해제한다.
     */
    public void removeCommandListener(Consumer<GitCommandRecord> listener) {
        commandListeners.remove(listener);
    }

    /**
     * 지금까지 실행된 모든 명령 기록을 반환한다 (읽기 전용).
     */
    public List<GitCommandRecord> getCommandHistory() {
        return Collections.unmodifiableList(commandHistory);
    }

    /**
     * 명령 기록을 초기화한다.
     */
    public void clearCommandHistory() {
        commandHistory.clear();
    }
}
