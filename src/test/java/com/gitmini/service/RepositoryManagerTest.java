package com.gitmini.service;

import com.gitmini.config.AppConfig;
import com.gitmini.config.ConfigManager;
import com.gitmini.exception.GitExecutionException;
import com.gitmini.model.CommitInfo;
import com.gitmini.model.FileChange;
import com.gitmini.model.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RepositoryManager 단위 테스트.
 * ConfigManager와 GitService를 모킹하여 레포 목록 관리·동기화 로직을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class RepositoryManagerTest {

    @Mock
    private ConfigManager configManager;

    @Mock
    private GitService gitService;

    private RepositoryManager manager;

    @BeforeEach
    void setUp() {
        manager = new RepositoryManager(configManager, gitService);
    }

    @Test
    void getRepositories_빈_설정이면_빈_목록() {
        AppConfig config = new AppConfig();
        when(configManager.load()).thenReturn(config);

        List<Repository> list = manager.getRepositories();

        assertTrue(list.isEmpty());
    }

    @Test
    void getRepositories_유효한_경로_있으면_상태_갱신_후_반환(@TempDir Path tempDir) throws Exception {
        Path repoPath = tempDir.resolve("repo");
        Files.createDirectories(repoPath);
        Files.createDirectories(repoPath.resolve(".git"));

        AppConfig config = new AppConfig();
        config.getRepoPaths().add(repoPath.toString());
        when(configManager.load()).thenReturn(config);

        when(gitService.status(eq(repoPath))).thenReturn(List.of(
                new FileChange("a.txt", FileChange.ChangeType.MODIFIED, false)));
        when(gitService.currentBranch(eq(repoPath))).thenReturn("main");
        when(gitService.aheadBehind(eq(repoPath))).thenReturn(new int[]{1, 0});
        when(gitService.log(eq(repoPath), eq(1))).thenReturn(List.of(
                new CommitInfo("abc123", "author", "last commit msg", LocalDateTime.of(2025, 1, 15, 12, 0))));

        List<Repository> list = manager.getRepositories();

        assertEquals(1, list.size());
        Repository repo = list.get(0);
        assertEquals("repo", repo.getName());
        assertEquals(repoPath.toString(), repo.getPath());
        assertEquals("main", repo.getCurrentBranch());
        assertEquals(1, repo.getChangedFileCount());
        assertEquals(1, repo.getAhead());
        assertEquals(0, repo.getBehind());
        assertEquals("last commit msg", repo.getLastCommitMessage());
        assertNotNull(repo.getLastCommitDate());
    }

    @Test
    void getRepositories_경로_무효하면_해당_항목_건너뜀() {
        AppConfig config = new AppConfig();
        config.getRepoPaths().add("C:/nonexistent/path");
        when(configManager.load()).thenReturn(config);

        List<Repository> list = manager.getRepositories();

        assertTrue(list.isEmpty());
    }

    @Test
    void add_유효한_레포_등록_후_설정_저장(@TempDir Path tempDir) throws Exception {
        Path repoPath = tempDir.resolve("myrepo");
        Files.createDirectories(repoPath);
        Files.createDirectories(repoPath.resolve(".git"));

        AppConfig config = new AppConfig();
        when(configManager.load()).thenReturn(config);
        when(gitService.status(any(Path.class))).thenReturn(List.of());

        manager.add(repoPath);

        verify(configManager).save(argThat(c -> c.getRepoPaths().size() == 1 && c.getRepoPaths().get(0).equals(repoPath.toString())));
    }

    @Test
    void add_이미_등록된_경로는_중복_추가_안함(@TempDir Path tempDir) throws Exception {
        Path repoPath = tempDir.resolve("dup");
        Files.createDirectories(repoPath);
        Files.createDirectories(repoPath.resolve(".git"));

        AppConfig config = new AppConfig();
        config.getRepoPaths().add(repoPath.toString());
        when(configManager.load()).thenReturn(config);
        when(gitService.status(any(Path.class))).thenReturn(List.of());

        manager.add(repoPath);

        verify(configManager, never()).save(any());
    }

    @Test
    void add_디렉터리_아니면_예외() {
        assertThrows(IllegalArgumentException.class, () -> manager.add(Path.of("C:/nonexistent")));
        verify(configManager, never()).save(any());
    }

    @Test
    void add_dot_git_없으면_예외(@TempDir Path tempDir) throws Exception {
        Path noGit = tempDir.resolve("no-git");
        Files.createDirectories(noGit);

        assertThrows(IllegalArgumentException.class, () -> manager.add(noGit));
        verify(configManager, never()).save(any());
    }

    @Test
    void add_git_status_실패시_예외(@TempDir Path tempDir) throws Exception {
        Path repoPath = tempDir.resolve("broken");
        Files.createDirectories(repoPath);
        Files.createDirectories(repoPath.resolve(".git"));

        when(gitService.status(any(Path.class)))
                .thenThrow(new GitExecutionException("not a git repo", 128, "fatal"));

        assertThrows(GitExecutionException.class, () -> manager.add(repoPath));
        verify(configManager, never()).save(any());
    }

    @Test
    void remove_등록된_경로_제거() {
        Path path = Path.of("C:/repos/removed");
        AppConfig config = new AppConfig();
        config.getRepoPaths().add("C:\\repos\\removed");
        when(configManager.load()).thenReturn(config);

        manager.remove(path);

        verify(configManager).save(argThat(c -> c.getRepoPaths().isEmpty()));
    }

    @Test
    void refreshStatus_GitService_호출로_레포_상태_갱신() {
        Repository repo = new Repository("C:/test/repo");
        Path path = Path.of("C:/test/repo");

        when(gitService.currentBranch(eq(path))).thenReturn("develop");
        when(gitService.status(eq(path))).thenReturn(List.of(
                new FileChange("x.txt", FileChange.ChangeType.UNTRACKED, false)));
        when(gitService.aheadBehind(eq(path))).thenReturn(new int[]{2, 1});
        when(gitService.log(eq(path), eq(1))).thenReturn(List.of(
                new CommitInfo("h1", "u", "fix bug", LocalDateTime.of(2025, 2, 1, 10, 0))));

        manager.refreshStatus(repo);

        assertEquals("develop", repo.getCurrentBranch());
        assertEquals(1, repo.getChangedFileCount());
        assertEquals(2, repo.getAhead());
        assertEquals(1, repo.getBehind());
        assertEquals("fix bug", repo.getLastCommitMessage());
        assertNotNull(repo.getLastCommitDate());
    }

    @Test
    void getRepoPaths_설정의_경로_목록_반환() {
        AppConfig config = new AppConfig();
        config.getRepoPaths().add("C:/a");
        config.getRepoPaths().add("C:/b");
        when(configManager.load()).thenReturn(config);

        List<String> paths = manager.getRepoPaths();

        assertEquals(2, paths.size());
        assertTrue(paths.contains("C:/a"));
        assertTrue(paths.contains("C:/b"));
    }
}
