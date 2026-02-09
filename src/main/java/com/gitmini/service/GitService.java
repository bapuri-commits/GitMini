package com.gitmini.service;

import com.gitmini.exception.GitExecutionException;
import com.gitmini.git.GitCommandBuilder;
import com.gitmini.git.GitExecutor;
import com.gitmini.git.GitResult;
import com.gitmini.git.parser.BranchParser;
import com.gitmini.git.parser.DiffParser;
import com.gitmini.git.parser.LogParser;
import com.gitmini.git.parser.StatusParser;
import com.gitmini.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

/**
 * Git 작업 서비스 — 비즈니스 계층.
 * <p>
 * GitExecutor + GitCommandBuilder + Parsers를 조합하여
 * 상위 계층(Controller)에 의미 있는 Git 작업 API를 제공한다.
 * </p>
 *
 * <h3>의존성 방향</h3>
 * <pre>
 * Controller → GitService → GitExecutor / GitCommandBuilder / Parsers
 *                 ↓
 *               model
 * </pre>
 *
 * <h3>에러 처리 전략</h3>
 * <ul>
 *   <li>git 명령 실패 시 {@link GitExecutionException}을 던진다</li>
 *   <li>Controller가 이를 잡아서 사용자에게 의미 있는 에러 메시지로 변환한다</li>
 * </ul>
 *
 * <h3>Command Log</h3>
 * <p>
 * 모든 git 명령은 GitExecutor를 통해 실행되며, GitExecutor가 자동으로
 * {@link com.gitmini.model.GitCommandRecord}를 기록한다.
 * </p>
 */
public class GitService {

    private static final Logger log = LoggerFactory.getLogger(GitService.class);

    private final GitExecutor executor;
    private final StatusParser statusParser;
    private final BranchParser branchParser;
    private final LogParser logParser;
    private final DiffParser diffParser;

    public GitService(GitExecutor executor) {
        this.executor = executor;
        this.statusParser = new StatusParser();
        this.branchParser = new BranchParser();
        this.logParser = new LogParser();
        this.diffParser = new DiffParser();
    }

    // ========== Status ==========

    /**
     * 레포의 변경 파일 목록을 조회한다 (staged + unstaged).
     *
     * @param repoPath 레포 절대 경로
     * @return 변경된 파일 목록
     * @throws GitExecutionException git status 실패 시
     */
    public List<FileChange> status(Path repoPath) {
        GitResult result = executor.execute(repoPath,
                GitCommandBuilder.git().status().porcelain().build());
        requireSuccess(result, "status");
        return statusParser.parse(result.stdout());
    }

    // ========== Staging ==========

    /**
     * 지정된 파일들을 스테이징한다.
     *
     * @param repoPath 레포 경로
     * @param files    스테이징할 파일 경로 목록 (레포 내 상대 경로)
     */
    public void add(Path repoPath, List<String> files) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("스테이징할 파일 목록이 비어 있습니다");
        }
        GitResult result = executor.execute(repoPath,
                GitCommandBuilder.git().add().files(files).build());
        requireSuccess(result, "add");
    }

    /**
     * 모든 변경 파일을 스테이징한다 ({@code git add .}).
     */
    public void addAll(Path repoPath) {
        GitResult result = executor.execute(repoPath,
                GitCommandBuilder.git().add().arg(".").build());
        requireSuccess(result, "add all");
    }

    /**
     * 지정된 파일들의 스테이징을 취소한다 ({@code git reset HEAD -- files}).
     */
    public void unstage(Path repoPath, List<String> files) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("언스테이징할 파일 목록이 비어 있습니다");
        }
        GitResult result = executor.execute(repoPath,
                GitCommandBuilder.git().reset().arg("HEAD").files(files).build());
        requireSuccess(result, "unstage");
    }

    // ========== Commit ==========

    /**
     * 스테이징된 변경을 커밋한다.
     *
     * @param repoPath 레포 경로
     * @param message  커밋 메시지
     */
    public void commit(Path repoPath, String message) {
        GitResult result = executor.execute(repoPath,
                GitCommandBuilder.git().commit().message(message).build());
        requireSuccess(result, "commit");
    }

    /**
     * 직전 커밋을 수정(amend)한다.
     *
     * @param repoPath 레포 경로
     * @param message  새 커밋 메시지 (null/blank이면 기존 메시지 유지)
     */
    public void amend(Path repoPath, String message) {
        GitCommandBuilder builder = GitCommandBuilder.git().commit().amend();
        if (message != null && !message.isBlank()) {
            builder.message(message);
        } else {
            builder.noEdit();
        }
        GitResult result = executor.execute(repoPath, builder.build());
        requireSuccess(result, "amend");
    }

    // ========== Undo ==========

    /**
     * 최근 커밋을 취소한다 ({@code git reset --soft HEAD~1}).
     * <p>
     * 커밋만 취소되고, 변경 사항은 스테이징 상태로 유지된다.
     * 커밋 히스토리가 없는 레포에서 호출하면 에러가 발생한다.
     * </p>
     *
     * @param repoPath 레포 경로
     * @throws GitExecutionException git reset 실패 시
     */
    public void resetSoft(Path repoPath) {
        GitResult result = executor.execute(repoPath,
                GitCommandBuilder.git().reset().soft().arg("HEAD~1").build());
        requireSuccess(result, "reset --soft");
    }

    // ========== Remote Operations (네트워크 타임아웃 적용) ==========

    /**
     * 현재 브랜치를 원격에 푸시한다.
     */
    public void push(Path repoPath) {
        GitResult result = executor.execute(repoPath,
                GitCommandBuilder.git().push().build(),
                GitExecutor.NETWORK_TIMEOUT_SECONDS);
        requireSuccess(result, "push");
    }

    /**
     * upstream이 설정되지 않은 브랜치를 원격에 push하면서 upstream을 자동 설정한다.
     * {@code git push -u origin <branch>}
     */
    public void pushSetUpstream(Path repoPath, String branchName) {
        GitResult result = executor.execute(repoPath,
                GitCommandBuilder.git().push().arg("-u").arg("origin").arg(branchName).build(),
                GitExecutor.NETWORK_TIMEOUT_SECONDS);
        requireSuccess(result, "push -u");
    }

    /**
     * 원격에서 변경사항을 가져오고 병합한다.
     */
    public void pull(Path repoPath) {
        GitResult result = executor.execute(repoPath,
                GitCommandBuilder.git().pull().build(),
                GitExecutor.NETWORK_TIMEOUT_SECONDS);
        requireSuccess(result, "pull");
    }

    /**
     * 원격 상태를 가져온다 (병합 없이).
     */
    public void fetch(Path repoPath) {
        GitResult result = executor.execute(repoPath,
                GitCommandBuilder.git().fetch().build(),
                GitExecutor.NETWORK_TIMEOUT_SECONDS);
        requireSuccess(result, "fetch");
    }

    // ========== Branch ==========

    /**
     * 로컬 브랜치 목록을 조회한다 (트래킹 정보 포함).
     *
     * @return 브랜치 정보 목록
     */
    public List<BranchInfo> branches(Path repoPath) {
        GitResult result = executor.execute(repoPath,
                GitCommandBuilder.git().branch().verbose().noColor().build());
        requireSuccess(result, "branch list");
        return branchParser.parse(result.stdout());
    }

    /**
     * 현재 브랜치 이름을 반환한다.
     *
     * @return 현재 브랜치 이름 (예: "main")
     */
    public String currentBranch(Path repoPath) {
        GitResult result = executor.execute(repoPath,
                GitCommandBuilder.git().revParse().arg("--abbrev-ref").arg("HEAD").build());
        requireSuccess(result, "current branch");
        return result.stdout().trim();
    }

    /**
     * 지정된 브랜치로 전환한다.
     */
    public void checkout(Path repoPath, String branch) {
        GitResult result = executor.execute(repoPath,
                GitCommandBuilder.git().checkout().arg(branch).build());
        requireSuccess(result, "checkout");
    }

    /**
     * 새 브랜치를 생성한다 (전환하지 않음).
     */
    public void createBranch(Path repoPath, String name) {
        GitResult result = executor.execute(repoPath,
                GitCommandBuilder.git().branch().arg(name).build());
        requireSuccess(result, "create branch");
    }

    /**
     * 현재 브랜치의 ahead/behind 정보를 조회한다.
     * <p>
     * upstream이 설정되지 않은 경우 {0, 0}을 반환한다 (에러가 아님).
     * </p>
     *
     * @return int[]{ahead, behind}
     */
    public int[] aheadBehind(Path repoPath) {
        try {
            GitResult result = executor.execute(repoPath,
                    GitCommandBuilder.git().revList().count().leftRight()
                            .arg("@{u}...HEAD").build());
            if (result.isSuccess()) {
                return branchParser.parseAheadBehind(result.stdout());
            }
        } catch (GitExecutionException e) {
            // "no upstream" / exit code 128 = upstream 미설정 → 정상적인 상황
            if (e.getExitCode() == 128 || e.getStderr().contains("no upstream")) {
                log.debug("ahead/behind: upstream 미설정 ({})", e.getMessage());
            } else {
                log.warn("ahead/behind 조회 실패: {}", e.getMessage());
            }
        }
        return new int[]{0, 0};
    }

    /**
     * 레포에 원격(remote)이 하나라도 설정되어 있는지 조회한다.
     *
     * @param repoPath 레포 경로
     * @return 원격이 있으면 true, 없거나 조회 실패 시 false
     */
    public boolean hasRemote(Path repoPath) {
        try {
            GitResult result = executor.execute(repoPath,
                    GitCommandBuilder.git().remote().build());
            return result.isSuccess() && result.stdout() != null
                    && !result.stdout().trim().isEmpty();
        } catch (Exception e) {
            log.debug("원격 목록 조회 실패: {}", e.getMessage());
            return false;
        }
    }

    // ========== Log ==========

    /**
     * 커밋 히스토리를 조회한다.
     *
     * @param repoPath 레포 경로
     * @param maxCount 최대 조회 커밋 수
     * @return 커밋 정보 목록 (최신이 첫 번째)
     */
    public List<CommitInfo> log(Path repoPath, int maxCount) {
        GitResult result = executor.execute(repoPath,
                GitCommandBuilder.git().log()
                        .format(LogParser.LOG_FORMAT)
                        .maxCount(maxCount)
                        .build());
        requireSuccess(result, "log");
        return logParser.parse(result.stdout());
    }

    // ========== Diff ==========

    /**
     * Unstaged 변경의 diff를 조회한다.
     */
    public List<DiffEntry> diffUnstaged(Path repoPath) {
        GitResult result = executor.execute(repoPath,
                GitCommandBuilder.git().diff().noColor().build());
        requireSuccess(result, "diff");
        return diffParser.parse(result.stdout());
    }

    /**
     * Staged 변경의 diff를 조회한다 ({@code git diff --cached}).
     */
    public List<DiffEntry> diffStaged(Path repoPath) {
        GitResult result = executor.execute(repoPath,
                GitCommandBuilder.git().diff().cached().noColor().build());
        requireSuccess(result, "diff staged");
        return diffParser.parse(result.stdout());
    }

    /**
     * 특정 파일의 diff를 조회한다.
     *
     * @param filePath 파일 경로 (레포 내 상대 경로)
     */
    public List<DiffEntry> diffFile(Path repoPath, String filePath) {
        GitResult result = executor.execute(repoPath,
                GitCommandBuilder.git().diff().noColor().files(List.of(filePath)).build());
        requireSuccess(result, "diff file");
        return diffParser.parse(result.stdout());
    }

    // ========== Discard ==========

    /**
     * 지정된 파일의 working tree 변경을 되돌린다 (위험한 작업).
     * <p>
     * {@code git checkout -- files}를 실행한다.
     * 스테이징되지 않은 변경이 영구적으로 삭제된다.
     * </p>
     */
    public void discard(Path repoPath, List<String> files) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("되돌릴 파일 목록이 비어 있습니다");
        }
        GitResult result = executor.execute(repoPath,
                GitCommandBuilder.git().checkout().files(files).build());
        requireSuccess(result, "discard");
    }

    // ========== Clone ==========

    /**
     * 원격 레포지토리를 로컬에 클론한다.
     * <p>
     * {@code git clone <url> <targetDir>} 을 실행한다.
     * 네트워크 타임아웃(120초)이 적용된다.
     * </p>
     *
     * @param url       클론할 URL (HTTPS 또는 SSH)
     * @param targetDir 클론 대상 디렉토리 (존재하지 않아야 함)
     * @throws GitExecutionException 클론 실패 시
     * @throws IllegalArgumentException URL이 비었거나 targetDir이 이미 존재할 때
     */
    public void cloneRepo(String url, java.nio.file.Path targetDir) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("클론 URL이 비어 있습니다");
        }
        if (targetDir == null) {
            throw new IllegalArgumentException("클론 대상 경로가 지정되지 않았습니다");
        }
        if (java.nio.file.Files.exists(targetDir)) {
            throw new IllegalArgumentException("대상 경로가 이미 존재합니다: " + targetDir);
        }

        // 작업 디렉토리 = 대상의 부모 (존재해야 함)
        java.nio.file.Path parentDir = targetDir.getParent();
        if (parentDir == null || !java.nio.file.Files.isDirectory(parentDir)) {
            throw new IllegalArgumentException("부모 디렉토리가 존재하지 않습니다: " + parentDir);
        }

        log.info("Clone 시작: {} → {}", maskTokenInUrl(url), targetDir);

        GitResult result = executor.execute(parentDir,
                GitCommandBuilder.git().cloneRepo().progress()
                        .arg(url).arg(targetDir.toString()).build(),
                GitExecutor.NETWORK_TIMEOUT_SECONDS);

        // git clone은 성공 시에도 stderr에 진행 정보를 출력한다.
        // exit code로만 성공 판단.
        if (!result.isSuccess()) {
            throw new GitExecutionException(
                    "Clone 실패: " + result.stderr(),
                    result.exitCode(),
                    result.stderr()
            );
        }

        log.info("Clone 완료: {} ({}ms)", targetDir, result.durationMs());
    }

    /**
     * URL에 포함된 토큰을 마스킹한다 (로깅용).
     * "https://ghp_abc123@github.com/..." → "https://***@github.com/..."
     */
    private static String maskTokenInUrl(String url) {
        if (url == null) return null;
        return url.replaceAll("(https?://)([^@]+)@", "$1***@");
    }

    /**
     * PAT를 HTTPS URL에 삽입하여 인증된 클론 URL을 만든다.
     * <p>
     * 입력: https://github.com/owner/repo.git + token
     * 출력: https://token@github.com/owner/repo.git
     * </p>
     * HTTPS가 아니거나 토큰이 없으면 원본 URL을 그대로 반환한다.
     *
     * @param url   원본 클론 URL
     * @param token PAT (null이면 원본 반환)
     * @return 인증 정보가 삽입된 URL
     */
    public static String injectTokenIntoUrl(String url, String token) {
        if (token == null || token.isBlank()) return url;
        if (url == null || !url.startsWith("https://")) return url;

        // 이미 인증 정보가 포함된 경우 교체하지 않음
        if (url.contains("@")) return url;

        return url.replaceFirst("https://", "https://" + token + "@");
    }

    // ========== 유틸리티 ==========

    /**
     * GitResult가 실패인 경우 GitExecutionException을 던진다.
     */
    private void requireSuccess(GitResult result, String operation) {
        if (!result.isSuccess()) {
            throw new GitExecutionException(
                    "Git " + operation + " 실패: " + result.stderr(),
                    result.exitCode(),
                    result.stderr()
            );
        }
    }

    /**
     * GitExecutor 인스턴스를 반환한다 (Command Log 접근 등에 사용).
     */
    public GitExecutor getExecutor() {
        return executor;
    }
}
