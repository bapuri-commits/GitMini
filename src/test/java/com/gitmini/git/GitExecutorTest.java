package com.gitmini.git;

import com.gitmini.exception.GitExecutionException;
import com.gitmini.model.GitCommandRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GitExecutor 단위 테스트.
 * 실제 git CLI를 실행하여 검증한다 (git이 설치된 환경에서만 동작).
 */
class GitExecutorTest {

    private GitExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new GitExecutor();
    }

    // ========== 기본 실행 ==========

    @Test
    void git_version_실행_성공() {
        GitResult result = executor.execute(Path.of("."), List.of("git", "--version"));

        assertTrue(result.isSuccess());
        assertTrue(result.stdout().startsWith("git version"));
        assertEquals(0, result.exitCode());
        assertTrue(result.durationMs() >= 0);
    }

    @Test
    void 존재하지_않는_서브명령_실행시_실패_결과() {
        GitResult result = executor.execute(Path.of("."),
                List.of("git", "nonexistent-command-xyz"));

        assertFalse(result.isSuccess());
        assertTrue(result.exitCode() != 0);
        assertFalse(result.stderr().isEmpty());
    }

    @Test
    void 실패한_명령의_exitCode_확인() {
        // git status on non-git directory
        GitResult result = executor.execute(
                Path.of(System.getProperty("java.io.tmpdir")),
                List.of("git", "status"));

        assertFalse(result.isSuccess());
        assertTrue(result.exitCode() != 0);
    }

    // ========== 타임아웃 ==========

    @Test
    void 타임아웃_발생_시_예외() {
        // 1초 타임아웃으로 긴 작업 실행 시도
        // Windows에서는 ping, Unix에서는 sleep 사용
        String os = System.getProperty("os.name").toLowerCase();
        List<String> command;
        if (os.contains("win")) {
            command = List.of("ping", "-n", "10", "127.0.0.1");
        } else {
            command = List.of("sleep", "10");
        }

        GitExecutionException ex = assertThrows(GitExecutionException.class, () ->
                executor.execute(Path.of("."), command, 1));

        assertTrue(ex.getMessage().contains("타임아웃"));
    }

    // ========== Command Log ==========

    @Test
    void 명령_실행_시_히스토리에_기록() {
        executor.execute(Path.of("."), List.of("git", "--version"));

        List<GitCommandRecord> history = executor.getCommandHistory();
        assertEquals(1, history.size());

        GitCommandRecord record = history.get(0);
        assertEquals("git --version", record.command());
        assertTrue(record.success());
        assertTrue(record.durationMs() >= 0);
        assertNotNull(record.timestamp());
    }

    @Test
    void 여러_명령_실행_시_모두_기록() {
        executor.execute(Path.of("."), List.of("git", "--version"));
        executor.execute(Path.of("."), List.of("git", "--version"));
        executor.execute(Path.of("."), List.of("git", "--version"));

        assertEquals(3, executor.getCommandHistory().size());
    }

    @Test
    void 히스토리_초기화() {
        executor.execute(Path.of("."), List.of("git", "--version"));
        assertEquals(1, executor.getCommandHistory().size());

        executor.clearCommandHistory();
        assertEquals(0, executor.getCommandHistory().size());
    }

    @Test
    void 명령_리스너_호출_확인() {
        AtomicReference<GitCommandRecord> received = new AtomicReference<>();
        executor.addCommandListener(received::set);

        executor.execute(Path.of("."), List.of("git", "--version"));

        assertNotNull(received.get());
        assertEquals("git --version", received.get().command());
        assertTrue(received.get().success());
    }

    @Test
    void 리스너_해제() {
        List<GitCommandRecord> records = new ArrayList<>();
        var listener = (java.util.function.Consumer<GitCommandRecord>) records::add;

        executor.addCommandListener(listener);
        executor.execute(Path.of("."), List.of("git", "--version"));
        assertEquals(1, records.size());

        executor.removeCommandListener(listener);
        executor.execute(Path.of("."), List.of("git", "--version"));
        assertEquals(1, records.size()); // 증가하지 않아야 함
    }

    @Test
    void 히스토리는_읽기전용() {
        executor.execute(Path.of("."), List.of("git", "--version"));

        List<GitCommandRecord> history = executor.getCommandHistory();
        assertThrows(UnsupportedOperationException.class, () ->
                history.add(new GitCommandRecord("x", null, true, 0, "")));
    }

    // ========== Git 설치 확인 ==========

    @Test
    void git_설치_확인() {
        assertTrue(executor.isGitInstalled());
    }

    @Test
    void git_버전_문자열_반환() {
        String version = executor.getGitVersion();
        assertNotNull(version);
        // "2.39.0" 또는 "2.39.0.windows.1" 형식
        assertTrue(version.matches("\\d+\\.\\d+\\.\\d+.*"),
                "버전 형식이 올바르지 않음: " + version);
    }

    @Test
    void git_버전_지원_확인() {
        // 현대 시스템이면 2.23.0 이상일 것
        assertTrue(executor.isGitVersionSupported());
    }

    // ========== 버전 비교 ==========

    @Test
    void 버전_비교_같은_버전() {
        assertEquals(0, GitExecutor.compareVersions("2.23.0", "2.23.0"));
    }

    @Test
    void 버전_비교_높은_버전() {
        assertTrue(GitExecutor.compareVersions("2.39.0", "2.23.0") > 0);
    }

    @Test
    void 버전_비교_낮은_버전() {
        assertTrue(GitExecutor.compareVersions("2.20.0", "2.23.0") < 0);
    }

    @Test
    void 버전_비교_windows_suffix() {
        // "2.39.0.windows.1" vs "2.23.0"
        assertTrue(GitExecutor.compareVersions("2.39.0.windows.1", "2.23.0") > 0);
    }

    @Test
    void 버전_비교_메이저_버전_차이() {
        assertTrue(GitExecutor.compareVersions("3.0.0", "2.99.99") > 0);
    }

    @Test
    void 버전_비교_패치_버전_차이() {
        assertTrue(GitExecutor.compareVersions("2.23.1", "2.23.0") > 0);
    }

    // ========== 실패 명령의 Command Log ==========

    @Test
    void 실패한_명령도_히스토리에_기록() {
        try {
            executor.execute(
                    Path.of(System.getProperty("java.io.tmpdir")),
                    List.of("git", "status"));
        } catch (Exception ignored) {
            // 실패해도 괜찮음
        }

        // 성공이든 실패든 히스토리에 기록되어야 함
        assertFalse(executor.getCommandHistory().isEmpty());
    }
}
