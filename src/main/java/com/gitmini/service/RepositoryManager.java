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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 레포지토리 목록 관리 서비스.
 * <p>
 * 내부에 {@code List<Repository>} 캐시를 유지하며,
 * {@link #add(Path)}/{@link #remove(Path)} 시 캐시와 AppConfig를 함께 갱신한다.
 * {@link #getRepositories()}는 캐시를 반환하여 불필요한 파일 I/O를 방지한다.
 * </p>
 * <p>
 * 유효한 git 레포 기준: {@code .git} 디렉토리 존재 + {@code git status} 한 번 실행 성공.
 * </p>
 */
public class RepositoryManager {

    private static final Logger log = LoggerFactory.getLogger(RepositoryManager.class);

    private final ConfigManager configManager;
    private final GitService gitService;

    /** 레포 목록 캐시. loadRepositories()로 초기화, add/remove로 갱신. */
    private final List<Repository> cache = new ArrayList<>();
    private boolean cacheLoaded = false;

    public RepositoryManager(ConfigManager configManager, GitService gitService) {
        this.configManager = configManager;
        this.gitService = gitService;
    }

    /**
     * 등록된 레포 목록을 반환한다 (읽기 전용).
     * <p>
     * 최초 호출 시 설정에서 로드하여 캐시를 초기화한다.
     * 이후에는 캐시를 반환한다. 전체 갱신이 필요하면 {@link #refreshAll()}을 호출한다.
     * </p>
     */
    public List<Repository> getRepositories() {
        if (!cacheLoaded) {
            loadRepositories();
        }
        return Collections.unmodifiableList(cache);
    }

    /**
     * 설정에서 레포 경로를 읽어 캐시를 초기화한다.
     * 각 레포의 상태(branch, 변경 수, ahead/behind, 최근 커밋)를 갱신한다.
     * 경로가 유효하지 않은 경우 해당 항목은 건너뛴다.
     */
    public void loadRepositories() {
        cache.clear();
        AppConfig config = configManager.load();
        List<String> paths = config.getRepoPaths();
        if (paths == null || paths.isEmpty()) {
            cacheLoaded = true;
            return;
        }

        for (String pathStr : paths) {
            Path path = Path.of(pathStr).toAbsolutePath().normalize();
            if (!Files.isDirectory(path) || !Files.exists(path.resolve(".git"))) {
                log.debug("레포 경로 무시 (디렉터리 또는 .git 없음): {}", path);
                continue;
            }
            Repository repo = new Repository(path.toString());
            try {
                refreshStatus(repo);
            } catch (Exception e) {
                // 커밋 없는 레포 등 — 상태 갱신 실패해도 목록에는 추가 (degraded 표시)
                log.warn("레포 상태 갱신 실패 (목록에는 포함): {} - {}", path, e.getMessage());
                repo.setCurrentBranch("(error)");
            }
            cache.add(repo);
        }
        cacheLoaded = true;
        log.info("레포 목록 로드 완료: {}개", cache.size());
    }

    /**
     * 캐시를 비우고 설정에서 다시 로드한다.
     */
    public void refreshAll() {
        cacheLoaded = false;
        loadRepositories();
    }

    /**
     * 레포를 등록한다.
     * <p>
     * 유효성: 디렉터리 존재, {@code .git} 존재, {@code git status} 실행 성공.
     * 등록 후 캐시와 설정을 함께 갱신한다.
     * </p>
     *
     * @param path 레포 루트 디렉터리 경로
     * @throws IllegalArgumentException 경로가 유효하지 않을 때
     * @throws GitExecutionException    git status 실패 시
     */
    public void add(Path path) {
        Path normalized = path.toAbsolutePath().normalize();

        if (!Files.isDirectory(normalized)) {
            throw new IllegalArgumentException("디렉터리가 아닙니다: " + normalized);
        }
        if (!Files.exists(normalized.resolve(".git"))) {
            throw new IllegalArgumentException(".git 디렉터리가 없습니다: " + normalized);
        }

        // git status 한 번 실행해서 동작 확인
        gitService.status(normalized);

        String pathStr = normalized.toString();

        // 설정에 등록 (중복 체크 + 추가를 원자적으로 수행)
        boolean[] added = {false};
        configManager.update(config -> {
            boolean alreadyExists = config.getRepoPaths().stream()
                    .anyMatch(p -> Path.of(p).toAbsolutePath().normalize()
                            .toString().equalsIgnoreCase(pathStr));
            if (!alreadyExists) {
                config.getRepoPaths().add(pathStr);
                added[0] = true;
            }
        });
        if (!added[0]) {
            log.debug("이미 등록된 레포: {}", pathStr);
            return;
        }

        // 캐시 갱신
        Repository repo = new Repository(pathStr);
        try {
            refreshStatus(repo);
        } catch (GitExecutionException e) {
            log.warn("새 레포 상태 갱신 실패 (목록에는 추가됨): {}", e.getMessage());
        }
        cache.add(repo);

        log.info("레포 등록: {}", pathStr);
    }

    /**
     * 레포를 목록에서 제거한다. 캐시와 설정을 함께 갱신한다.
     */
    public void remove(Path path) {
        String pathStr = path.toAbsolutePath().normalize().toString();

        // 설정에서 제거 (대소문자 무시, 원자적)
        boolean[] removed = {false};
        configManager.update(config -> {
            removed[0] = config.getRepoPaths().removeIf(p ->
                    Path.of(p).toAbsolutePath().normalize()
                            .toString().equalsIgnoreCase(pathStr));
        });
        if (removed[0]) {
            // 캐시에서도 제거
            cache.removeIf(repo ->
                    Path.of(repo.getPath()).toAbsolutePath().normalize()
                            .toString().equalsIgnoreCase(pathStr));

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
        repo.setHasRemote(gitService.hasRemote(path));

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
     * 현재 설정의 레포 경로 목록을 반환한다 (캐시 기반, 파일 I/O 없음).
     */
    public List<String> getRepoPaths() {
        if (!cacheLoaded) {
            loadRepositories();
        }
        List<String> paths = new ArrayList<>();
        for (Repository repo : cache) {
            paths.add(repo.getPath());
        }
        return paths;
    }

    /**
     * 경로로 캐시된 Repository를 찾는다.
     *
     * @return 찾은 Repository, 없으면 Optional.empty()
     */
    public Optional<Repository> findByPath(String repoPath) {
        String normalized = Path.of(repoPath).toAbsolutePath().normalize().toString();
        return cache.stream()
                .filter(r -> Path.of(r.getPath()).toAbsolutePath().normalize()
                        .toString().equalsIgnoreCase(normalized))
                .findFirst();
    }
}
