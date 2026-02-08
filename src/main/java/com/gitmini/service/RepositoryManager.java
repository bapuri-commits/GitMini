package com.gitmini.service;

import com.gitmini.config.AppConfig;
import com.gitmini.config.ConfigManager;
import com.gitmini.exception.GitExecutionException;
import com.gitmini.model.CommitInfo;
import com.gitmini.model.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 레포지토리 목록 관리 서비스.
 * <p>
 * AppConfig.repoPaths와 동기화하며, GitService로 각 레포의 상태(branch, 변경 파일 수, ahead/behind, 최근 커밋)를 갱신한다.
 * </p>
 * <p>
 * 유효한 git 레포 기준 (Q5): {@code .git} 디렉토리 존재 + {@code git status} 한 번 실행 성공.
 * </p>
 */
public class RepositoryManager {

    private static final Logger log = LoggerFactory.getLogger(RepositoryManager.class);

    private final ConfigManager configManager;
    private final GitService gitService;

    public RepositoryManager(ConfigManager configManager, GitService gitService) {
        this.configManager = configManager;
        this.gitService = gitService;
    }

    /**
     * 등록된 레포 목록을 반환한다. 각 레포의 상태(branch, 변경 수, ahead/behind, 최근 커밋)를 갱신한 뒤 반환한다.
     * <p>
     * 경로가 더 이상 유효하지 않은 경우(디렉터리 삭제 등) 해당 항목은 건너뛴다.
     * </p>
     */
    public List<Repository> getRepositories() {
        AppConfig config = configManager.load();
        List<String> paths = config.getRepoPaths();
        if (paths == null || paths.isEmpty()) {
            return new ArrayList<>();
        }

        List<Repository> list = new ArrayList<>();
        for (String pathStr : paths) {
            Path path = Path.of(pathStr).toAbsolutePath().normalize();
            if (!Files.isDirectory(path) || !Files.exists(path.resolve(".git"))) {
                log.debug("레포 경로 무시 (디렉터리 또는 .git 없음): {}", path);
                continue;
            }
            try {
                Repository repo = new Repository(path.toString());
                refreshStatus(repo);
                list.add(repo);
            } catch (GitExecutionException e) {
                log.warn("레포 상태 갱신 실패, 목록에서 제외: {} - {}", path, e.getMessage());
            }
        }
        return list;
    }

    /**
     * 레포를 등록한다.
     * <p>
     * 유효성: 디렉터리 존재, {@code .git} 존재, {@code git status} 실행 성공 (Q5).
     * </p>
     *
     * @param path 레포 루트 디렉터리 경로
     * @throws IllegalArgumentException 경로가 유효하지 않을 때
     * @throws GitExecutionException   git status 실패 시
     */
    public void add(Path path) {
        Path normalized = path.toAbsolutePath().normalize();

        if (!Files.isDirectory(normalized)) {
            throw new IllegalArgumentException("디렉터리가 아닙니다: " + normalized);
        }
        if (!Files.exists(normalized.resolve(".git"))) {
            throw new IllegalArgumentException(".git 디렉터리가 없습니다: " + normalized);
        }

        // Q5: git status 한 번 실행해서 동작 확인
        gitService.status(normalized);

        String pathStr = normalized.toString();
        AppConfig config = configManager.load();
        if (config.getRepoPaths().contains(pathStr)) {
            log.debug("이미 등록된 레포: {}", pathStr);
            return;
        }
        config.getRepoPaths().add(pathStr);
        configManager.save(config);
        log.info("레포 등록: {}", pathStr);
    }

    /**
     * 레포를 목록에서 제거한다. 설정을 저장한다.
     */
    public void remove(Path path) {
        String pathStr = path.toAbsolutePath().normalize().toString();
        AppConfig config = configManager.load();
        boolean removed = config.getRepoPaths().removeIf(p -> Path.of(p).toAbsolutePath().normalize().toString().equals(pathStr));
        if (removed) {
            configManager.save(config);
            log.info("레포 제거: {}", pathStr);
        }
    }

    /**
     * 레포의 상태를 갱신한다 (currentBranch, changedFileCount, ahead, behind, lastCommitMessage, lastCommitDate).
     */
    public void refreshStatus(Repository repo) {
        Path path = Path.of(repo.getPath());

        repo.setCurrentBranch(gitService.currentBranch(path));
        repo.setChangedFileCount(gitService.status(path).size());

        int[] ab = gitService.aheadBehind(path);
        repo.setAhead(ab[0]);
        repo.setBehind(ab[1]);

        List<CommitInfo> logList = gitService.log(path, 1);
        if (!logList.isEmpty()) {
            CommitInfo last = logList.get(0);
            repo.setLastCommitMessage(last.message());
            repo.setLastCommitDate(last.date());
        } else {
            repo.setLastCommitMessage("");
            repo.setLastCommitDate(null);
        }
    }

    /**
     * 현재 설정의 레포 경로 목록을 반환한다 (상태 갱신 없이).
     */
    public List<String> getRepoPaths() {
        AppConfig config = configManager.load();
        List<String> paths = config.getRepoPaths();
        return paths == null ? new ArrayList<>() : new ArrayList<>(paths);
    }
}
