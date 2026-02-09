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
import java.util.Optional;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RepositoryManager 단위 테스트.
 * ConfigManager와 GitService를 모킹하여 레포 목록 관리·동기화·캐시 로직을 검증한다.
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

    // ========== getRepositories ==========

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

        stubGitServiceForRepo(repoPath, "main", 1, new int[]{1, 0}, "last commit msg");

        List<Repository> list = manager.getRepositories();

        assertEquals(1, list.size());
        Repository repo = list.get(0);
        assertEquals("repo", repo.getName());
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
    void getRepositories_유효_무효_혼합시_유효한_것만_반환(@TempDir Path tempDir) throws Exception {
        Path validRepo = tempDir.resolve("valid");
        Files.createDirectories(validRepo);
        Files.createDirectories(validRepo.resolve(".git"));

        AppConfig config = new AppConfig();
        config.getRepoPaths().add("C:/nonexistent/invalid");
        config.getRepoPaths().add(validRepo.toString());
        when(configManager.load()).thenReturn(config);

        stubGitServiceForRepo(validRepo, "main", 0, new int[]{0, 0}, "init");

        List<Repository> list = manager.getRepositories();

        assertEquals(1, list.size());
        assertEquals("valid", list.get(0).getName());
    }

    @Test
    void getRepositories_git_status_실패한_레포도_degraded로_포함(@TempDir Path tempDir) throws Exception {
        Path brokenRepo = tempDir.resolve("broken");
        Files.createDirectories(brokenRepo);
        Files.createDirectories(brokenRepo.resolve(".git"));

        AppConfig config = new AppConfig();
        config.getRepoPaths().add(brokenRepo.toString());
        when(configManager.load()).thenReturn(config);

        when(gitService.currentBranch(any(Path.class)))
                .thenThrow(new GitExecutionException("fatal", 128, "not a git repo"));

        List<Repository> list = manager.getRepositories();

        // 실패한 레포도 목록에 포함 (degraded 상태)
        assertEquals(1, list.size());
        assertEquals("(error)", list.get(0).getCurrentBranch());
    }

    @Test
    void getRepositories_캐시된_결과_반환_두번째_호출시_파일IO_없음() {
        AppConfig config = new AppConfig();
        when(configManager.load()).thenReturn(config);

        manager.getRepositories(); // 최초 로드
        manager.getRepositories(); // 캐시 반환

        // configManager.load()는 최초 1회만 호출되어야 함
        verify(configManager, times(1)).load();
    }

    @Test
    void getRepositories_반환값은_읽기전용() {
        AppConfig config = new AppConfig();
        when(configManager.load()).thenReturn(config);

        List<Repository> list = manager.getRepositories();

        assertThrows(UnsupportedOperationException.class, () ->
                list.add(new Repository("C:/test")));
    }

    // ========== add ==========

    @Test
    void add_유효한_레포_등록_후_설정_저장_및_캐시_갱신(@TempDir Path tempDir) throws Exception {
        Path repoPath = tempDir.resolve("myrepo");
        Files.createDirectories(repoPath);
        Files.createDirectories(repoPath.resolve(".git"));

        AppConfig config = new AppConfig();
        when(configManager.load()).thenReturn(config);
        // update() 호출 시 consumer를 실제로 실행하도록 설정
        doAnswer(inv -> {
            Consumer<AppConfig> modifier = inv.getArgument(0);
            modifier.accept(config);
            return null;
        }).when(configManager).update(any());
        when(gitService.status(any(Path.class))).thenReturn(List.of());
        when(gitService.currentBranch(any(Path.class))).thenReturn("main");
        when(gitService.aheadBehind(any(Path.class))).thenReturn(new int[]{0, 0});
        when(gitService.log(any(Path.class), eq(1))).thenReturn(List.of());

        manager.add(repoPath);

        // update() 호출 확인
        verify(configManager).update(any());

        // 캐시에도 추가되었는지 확인
        List<Repository> repos = manager.getRepositories();
        assertEquals(1, repos.size());
        assertEquals("myrepo", repos.get(0).getName());
    }

    @Test
    void add_이미_등록된_경로는_중복_추가_안함(@TempDir Path tempDir) throws Exception {
        Path repoPath = tempDir.resolve("dup");
        Files.createDirectories(repoPath);
        Files.createDirectories(repoPath.resolve(".git"));

        AppConfig config = new AppConfig();
        config.getRepoPaths().add(repoPath.toAbsolutePath().normalize().toString());
        doAnswer(inv -> {
            Consumer<AppConfig> modifier = inv.getArgument(0);
            modifier.accept(config);
            return null;
        }).when(configManager).update(any());
        when(gitService.status(any(Path.class))).thenReturn(List.of());

        manager.add(repoPath);

        // update()는 호출되지만, 중복이므로 repoPaths 크기는 그대로 1
        assertEquals(1, config.getRepoPaths().size());
    }

    @Test
    void add_디렉터리_아니면_예외() {
        assertThrows(IllegalArgumentException.class, () -> manager.add(Path.of("C:/nonexistent")));
        verify(configManager, never()).update(any());
    }

    @Test
    void add_dot_git_없으면_예외(@TempDir Path tempDir) throws Exception {
        Path noGit = tempDir.resolve("no-git");
        Files.createDirectories(noGit);

        assertThrows(IllegalArgumentException.class, () -> manager.add(noGit));
        verify(configManager, never()).update(any());
    }

    @Test
    void add_git_status_실패시_예외(@TempDir Path tempDir) throws Exception {
        Path repoPath = tempDir.resolve("broken");
        Files.createDirectories(repoPath);
        Files.createDirectories(repoPath.resolve(".git"));

        when(gitService.status(any(Path.class)))
                .thenThrow(new GitExecutionException("not a git repo", 128, "fatal"));

        assertThrows(GitExecutionException.class, () -> manager.add(repoPath));
        verify(configManager, never()).update(any());
    }

    // ========== remove ==========

    @Test
    void remove_등록된_경로_제거() {
        Path path = Path.of("C:/repos/removed");
        AppConfig config = new AppConfig();
        config.getRepoPaths().add("C:\\repos\\removed");
        doAnswer(inv -> {
            Consumer<AppConfig> modifier = inv.getArgument(0);
            modifier.accept(config);
            return null;
        }).when(configManager).update(any());

        manager.remove(path);

        verify(configManager).update(any());
        assertTrue(config.getRepoPaths().isEmpty());
    }

    @Test
    void remove_존재하지_않는_경로는_무시() {
        AppConfig config = new AppConfig();
        config.getRepoPaths().add("C:\\repos\\existing");
        doAnswer(inv -> {
            Consumer<AppConfig> modifier = inv.getArgument(0);
            modifier.accept(config);
            return null;
        }).when(configManager).update(any());

        manager.remove(Path.of("C:/repos/nonexistent"));

        // update는 호출되지만, 실제 제거는 안 됨
        assertEquals(1, config.getRepoPaths().size());
    }

    // ========== refreshStatus ==========

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
    void refreshStatus_커밋_없는_새_레포() {
        Repository repo = new Repository("C:/test/new-repo");
        Path path = Path.of("C:/test/new-repo");

        when(gitService.currentBranch(eq(path))).thenReturn("main");
        when(gitService.status(eq(path))).thenReturn(List.of());
        when(gitService.aheadBehind(eq(path))).thenReturn(new int[]{0, 0});
        when(gitService.log(eq(path), eq(1))).thenReturn(List.of());

        manager.refreshStatus(repo);

        assertEquals("main", repo.getCurrentBranch());
        assertEquals(0, repo.getChangedFileCount());
        assertEquals("", repo.getLastCommitMessage());
        assertNull(repo.getLastCommitDate());
    }

    // ========== getRepoPaths ==========

    @Test
    void getRepoPaths_캐시_기반으로_경로_반환(@TempDir Path tempDir) throws Exception {
        Path repoA = tempDir.resolve("a");
        Path repoB = tempDir.resolve("b");
        Files.createDirectories(repoA.resolve(".git"));
        Files.createDirectories(repoB.resolve(".git"));

        AppConfig config = new AppConfig();
        config.getRepoPaths().add(repoA.toString());
        config.getRepoPaths().add(repoB.toString());
        when(configManager.load()).thenReturn(config);

        stubGitServiceForRepo(repoA, "main", 0, new int[]{0, 0}, "");
        stubGitServiceForRepo(repoB, "dev", 0, new int[]{0, 0}, "");

        List<String> paths = manager.getRepoPaths();

        assertEquals(2, paths.size());
    }

    // ========== refreshAll ==========

    @Test
    void refreshAll_캐시를_다시_로드(@TempDir Path tempDir) throws Exception {
        Path repoPath = tempDir.resolve("repo");
        Files.createDirectories(repoPath.resolve(".git"));

        AppConfig config = new AppConfig();
        config.getRepoPaths().add(repoPath.toString());
        when(configManager.load()).thenReturn(config);

        stubGitServiceForRepo(repoPath, "main", 0, new int[]{0, 0}, "init");

        manager.getRepositories(); // 최초 로드
        manager.refreshAll();      // 강제 재로드

        // configManager.load()가 2회 호출되어야 함 (최초 + refreshAll)
        verify(configManager, times(2)).load();
    }

    // ========== findByPath ==========

    @Test
    void findByPath_존재하는_레포_찾기(@TempDir Path tempDir) throws Exception {
        Path repoPath = tempDir.resolve("target");
        Files.createDirectories(repoPath.resolve(".git"));

        AppConfig config = new AppConfig();
        config.getRepoPaths().add(repoPath.toString());
        when(configManager.load()).thenReturn(config);

        stubGitServiceForRepo(repoPath, "main", 0, new int[]{0, 0}, "init");

        manager.loadRepositories();

        Optional<Repository> found = manager.findByPath(repoPath.toString());
        assertTrue(found.isPresent());
        assertEquals("target", found.get().getName());
    }

    @Test
    void findByPath_존재하지_않는_경로() {
        AppConfig config = new AppConfig();
        when(configManager.load()).thenReturn(config);

        manager.loadRepositories();

        Optional<Repository> found = manager.findByPath("C:/nonexistent");
        assertFalse(found.isPresent());
    }

    // ========== 유틸리티 ==========

    private void stubGitServiceForRepo(Path repoPath, String branch, int changeCount,
                                       int[] aheadBehind, String lastCommitMsg) {
        when(gitService.currentBranch(eq(repoPath))).thenReturn(branch);

        List<FileChange> changes = new java.util.ArrayList<>();
        for (int i = 0; i < changeCount; i++) {
            changes.add(new FileChange("file" + i + ".txt", FileChange.ChangeType.MODIFIED, false));
        }
        when(gitService.status(eq(repoPath))).thenReturn(changes);
        when(gitService.aheadBehind(eq(repoPath))).thenReturn(aheadBehind);

        if (lastCommitMsg != null && !lastCommitMsg.isEmpty()) {
            when(gitService.log(eq(repoPath), eq(1))).thenReturn(List.of(
                    new CommitInfo("abc123", "author", lastCommitMsg,
                            LocalDateTime.of(2025, 1, 15, 12, 0))));
        } else {
            when(gitService.log(eq(repoPath), eq(1))).thenReturn(List.of());
        }
    }
}
