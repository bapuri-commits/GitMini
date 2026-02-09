package com.gitmini.service;

import com.gitmini.exception.GitExecutionException;
import com.gitmini.git.GitExecutor;
import com.gitmini.git.GitResult;
import com.gitmini.git.parser.LogParser;
import com.gitmini.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * GitService 단위 테스트.
 * GitExecutor를 모킹하여 서비스 로직을 검증한다.
 * 실제 git CLI는 실행하지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class GitServiceTest {

    @Mock
    private GitExecutor executor;

    private GitService service;

    private static final Path REPO = Path.of("/test/repo");

    @BeforeEach
    void setUp() {
        service = new GitService(executor);
    }

    // ========== Status ==========

    @Test
    void status_변경파일_반환() {
        when(executor.execute(eq(REPO), anyList()))
                .thenReturn(successResult("git status --porcelain",
                        " M README.md\n?? new.txt"));

        List<FileChange> changes = service.status(REPO);

        assertEquals(2, changes.size());
        assertEquals("README.md", changes.get(0).path());
        assertEquals(FileChange.ChangeType.MODIFIED, changes.get(0).type());
        assertFalse(changes.get(0).staged());
        assertEquals("new.txt", changes.get(1).path());
        assertEquals(FileChange.ChangeType.UNTRACKED, changes.get(1).type());
    }

    @Test
    void status_clean_레포() {
        when(executor.execute(eq(REPO), anyList()))
                .thenReturn(successResult("git status --porcelain", ""));

        List<FileChange> changes = service.status(REPO);
        assertTrue(changes.isEmpty());
    }

    @Test
    void status_실패시_예외() {
        when(executor.execute(eq(REPO), anyList()))
                .thenReturn(failureResult("git status", 128, "fatal: not a git repository"));

        assertThrows(GitExecutionException.class, () -> service.status(REPO));
    }

    // ========== Staging ==========

    @Test
    void add_파일_스테이징() {
        when(executor.execute(eq(REPO), anyList()))
                .thenReturn(successResult("git add -- file.txt", ""));

        assertDoesNotThrow(() -> service.add(REPO, List.of("file.txt")));
        verify(executor).execute(eq(REPO), argThat(cmd ->
                cmd.contains("add") && cmd.contains("--") && cmd.contains("file.txt")));
    }

    @Test
    void addAll() {
        when(executor.execute(eq(REPO), anyList()))
                .thenReturn(successResult("git add .", ""));

        assertDoesNotThrow(() -> service.addAll(REPO));
        verify(executor).execute(eq(REPO), argThat(cmd ->
                cmd.contains("add") && cmd.contains(".")));
    }

    @Test
    void unstage_파일() {
        when(executor.execute(eq(REPO), anyList()))
                .thenReturn(successResult("git reset HEAD -- file.txt", ""));

        assertDoesNotThrow(() -> service.unstage(REPO, List.of("file.txt")));
        verify(executor).execute(eq(REPO), argThat(cmd ->
                cmd.contains("reset") && cmd.contains("HEAD") && cmd.contains("file.txt")));
    }

    // ========== Commit ==========

    @Test
    void commit_성공() {
        when(executor.execute(eq(REPO), anyList()))
                .thenReturn(successResult("git commit -m \"fix: typo\"",
                        "[main abc1234] fix: typo\n 1 file changed"));

        assertDoesNotThrow(() -> service.commit(REPO, "fix: typo"));
        verify(executor).execute(eq(REPO), argThat(cmd ->
                cmd.contains("commit") && cmd.contains("-m") && cmd.contains("fix: typo")));
    }

    @Test
    void commit_실패시_예외() {
        when(executor.execute(eq(REPO), anyList()))
                .thenReturn(failureResult("git commit", 1, "nothing to commit"));

        assertThrows(GitExecutionException.class, () -> service.commit(REPO, "msg"));
    }

    @Test
    void amend_메시지와_함께() {
        when(executor.execute(eq(REPO), anyList()))
                .thenReturn(successResult("git commit --amend -m \"new msg\"", ""));

        assertDoesNotThrow(() -> service.amend(REPO, "new msg"));
        verify(executor).execute(eq(REPO), argThat(cmd ->
                cmd.contains("--amend") && cmd.contains("-m") && cmd.contains("new msg")));
    }

    @Test
    void amend_메시지_없이() {
        when(executor.execute(eq(REPO), anyList()))
                .thenReturn(successResult("git commit --amend --no-edit", ""));

        assertDoesNotThrow(() -> service.amend(REPO, null));
        verify(executor).execute(eq(REPO), argThat(cmd ->
                cmd.contains("--amend") && cmd.contains("--no-edit") && !cmd.contains("-m")));
    }

    // ========== Remote Operations ==========

    @Test
    void push_네트워크_타임아웃_사용() {
        when(executor.execute(eq(REPO), anyList(), eq(GitExecutor.NETWORK_TIMEOUT_SECONDS)))
                .thenReturn(successResult("git push", ""));

        assertDoesNotThrow(() -> service.push(REPO));
        verify(executor).execute(eq(REPO), anyList(), eq(GitExecutor.NETWORK_TIMEOUT_SECONDS));
    }

    @Test
    void pull_네트워크_타임아웃_사용() {
        when(executor.execute(eq(REPO), anyList(), eq(GitExecutor.NETWORK_TIMEOUT_SECONDS)))
                .thenReturn(successResult("git pull", "Already up to date."));

        assertDoesNotThrow(() -> service.pull(REPO));
        verify(executor).execute(eq(REPO), anyList(), eq(GitExecutor.NETWORK_TIMEOUT_SECONDS));
    }

    @Test
    void fetch_네트워크_타임아웃_사용() {
        when(executor.execute(eq(REPO), anyList(), eq(GitExecutor.NETWORK_TIMEOUT_SECONDS)))
                .thenReturn(successResult("git fetch", ""));

        assertDoesNotThrow(() -> service.fetch(REPO));
        verify(executor).execute(eq(REPO), anyList(), eq(GitExecutor.NETWORK_TIMEOUT_SECONDS));
    }

    // ========== Branch ==========

    @Test
    void branches_목록_반환() {
        when(executor.execute(eq(REPO), anyList()))
                .thenReturn(successResult("git branch -vv --no-color",
                        "* main     abc1234 [origin/main: ahead 1] latest commit\n" +
                        "  develop  def5678 [origin/develop] feature work"));

        List<BranchInfo> branches = service.branches(REPO);

        assertEquals(2, branches.size());
        assertTrue(branches.get(0).current());
        assertEquals("main", branches.get(0).name());
        assertEquals(1, branches.get(0).ahead());
    }

    @Test
    void currentBranch_반환() {
        when(executor.execute(eq(REPO), anyList()))
                .thenReturn(successResult("git rev-parse --abbrev-ref HEAD", "main\n"));

        String branch = service.currentBranch(REPO);
        assertEquals("main", branch);
    }

    @Test
    void checkout_브랜치_전환() {
        when(executor.execute(eq(REPO), anyList()))
                .thenReturn(successResult("git checkout develop",
                        "Switched to branch 'develop'"));

        assertDoesNotThrow(() -> service.checkout(REPO, "develop"));
    }

    @Test
    void createBranch() {
        when(executor.execute(eq(REPO), anyList()))
                .thenReturn(successResult("git branch feature-x", ""));

        assertDoesNotThrow(() -> service.createBranch(REPO, "feature-x"));
        verify(executor).execute(eq(REPO), argThat(cmd ->
                cmd.contains("branch") && cmd.contains("feature-x")));
    }

    @Test
    void aheadBehind_정상() {
        when(executor.execute(eq(REPO), anyList()))
                .thenReturn(successResult("git rev-list --count --left-right @{u}...HEAD",
                        "2\t5"));

        int[] ab = service.aheadBehind(REPO);
        assertEquals(5, ab[0]); // ahead
        assertEquals(2, ab[1]); // behind
    }

    @Test
    void aheadBehind_upstream_없으면_0_0() {
        when(executor.execute(eq(REPO), anyList()))
                .thenThrow(new GitExecutionException("no upstream", 128, "fatal: no upstream"));

        int[] ab = service.aheadBehind(REPO);
        assertEquals(0, ab[0]);
        assertEquals(0, ab[1]);
    }

    // ========== Log ==========

    @Test
    void log_커밋_목록_반환() {
        // \u0000 = null 문자 구분자 (Java에서 \0 뒤에 숫자가 오면 8진수로 해석됨)
        String logOutput = "abc123\u0000Alice\u0000feat: login\u00002024-03-10T09:00:00+09:00\n" +
                           "def456\u0000Bob\u0000fix: null check\u00002024-03-09T18:30:00+09:00";
        when(executor.execute(eq(REPO), anyList()))
                .thenReturn(successResult("git log", logOutput));

        List<CommitInfo> commits = service.log(REPO, 20);

        assertEquals(2, commits.size());
        assertEquals("Alice", commits.get(0).author());
        assertEquals("Bob", commits.get(1).author());
    }

    // ========== Diff ==========

    @Test
    void diffUnstaged_반환() {
        String diffOutput = String.join("\n",
                "diff --git a/file.txt b/file.txt",
                "index 1234..5678 100644",
                "--- a/file.txt",
                "+++ b/file.txt",
                "@@ -1,1 +1,2 @@",
                " line1",
                "+added"
        );
        when(executor.execute(eq(REPO), anyList()))
                .thenReturn(successResult("git diff", diffOutput));

        List<DiffEntry> diffs = service.diffUnstaged(REPO);

        assertEquals(1, diffs.size());
        assertEquals("file.txt", diffs.get(0).oldPath());
    }

    @Test
    void diffStaged_cached_옵션_사용() {
        when(executor.execute(eq(REPO), anyList()))
                .thenReturn(successResult("git diff --cached", ""));

        service.diffStaged(REPO);
        verify(executor).execute(eq(REPO), argThat(cmd ->
                cmd.contains("diff") && cmd.contains("--cached")));
    }

    // ========== Discard ==========

    @Test
    void discard_파일_되돌리기() {
        when(executor.execute(eq(REPO), anyList()))
                .thenReturn(successResult("git checkout -- file.txt", ""));

        assertDoesNotThrow(() -> service.discard(REPO, List.of("file.txt")));
        verify(executor).execute(eq(REPO), argThat(cmd ->
                cmd.contains("checkout") && cmd.contains("--") && cmd.contains("file.txt")));
    }

    // ========== Clone ==========

    @Test
    void cloneRepo_url_빈문자열_예외() {
        assertThrows(IllegalArgumentException.class,
                () -> service.cloneRepo("", Path.of("/test/new-repo")));
    }

    @Test
    void cloneRepo_url_null_예외() {
        assertThrows(IllegalArgumentException.class,
                () -> service.cloneRepo(null, Path.of("/test/new-repo")));
    }

    @Test
    void cloneRepo_targetDir_null_예외() {
        assertThrows(IllegalArgumentException.class,
                () -> service.cloneRepo("https://github.com/a/b.git", null));
    }

    // ========== injectTokenIntoUrl ==========

    @Test
    void injectTokenIntoUrl_https_정상삽입() {
        String result = GitService.injectTokenIntoUrl(
                "https://github.com/owner/repo.git", "ghp_test123");
        assertEquals("https://ghp_test123@github.com/owner/repo.git", result);
    }

    @Test
    void injectTokenIntoUrl_토큰없음_원본반환() {
        String url = "https://github.com/owner/repo.git";
        assertEquals(url, GitService.injectTokenIntoUrl(url, null));
        assertEquals(url, GitService.injectTokenIntoUrl(url, ""));
    }

    @Test
    void injectTokenIntoUrl_ssh_원본반환() {
        String url = "git@github.com:owner/repo.git";
        assertEquals(url, GitService.injectTokenIntoUrl(url, "ghp_test123"));
    }

    @Test
    void injectTokenIntoUrl_이미_인증정보_있음_원본반환() {
        String url = "https://user@github.com/owner/repo.git";
        assertEquals(url, GitService.injectTokenIntoUrl(url, "ghp_test123"));
    }

    // ========== Undo (resetSoft) ==========

    @Test
    void resetSoft_성공() {
        when(executor.execute(any(Path.class), anyList()))
                .thenReturn(successResult("git reset --soft HEAD~1", ""));

        assertDoesNotThrow(() -> service.resetSoft(REPO));
        verify(executor).execute(eq(REPO), argThat((List<String> cmd) ->
                cmd.contains("reset") && cmd.contains("--soft") && cmd.contains("HEAD~1")));
    }

    @Test
    void resetSoft_실패시_예외() {
        when(executor.execute(any(Path.class), anyList()))
                .thenReturn(failureResult("git reset --soft HEAD~1", 128,
                        "fatal: Failed to resolve 'HEAD~1' as a valid ref."));

        assertThrows(GitExecutionException.class, () -> service.resetSoft(REPO));
    }

    // ========== Executor 접근 ==========

    @Test
    void getExecutor_반환() {
        assertSame(executor, service.getExecutor());
    }

    // ========== 헬퍼 메서드 ==========

    private GitResult successResult(String command, String stdout) {
        return new GitResult(command, 0, stdout, "", 10);
    }

    private GitResult failureResult(String command, int exitCode, String stderr) {
        return new GitResult(command, exitCode, "", stderr, 10);
    }
}
