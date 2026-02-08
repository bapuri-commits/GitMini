package com.gitmini.controller;

import com.gitmini.GitMiniApp;
import com.gitmini.async.TaskManager;
import com.gitmini.controller.component.DiffRenderer;
import com.gitmini.controller.component.FileChangeListCell;
import com.gitmini.controller.component.RepoListCell;
import com.gitmini.model.DiffEntry;
import com.gitmini.model.CommitInfo;
import com.gitmini.model.FileChange;
import com.gitmini.model.GitCommandRecord;
import com.gitmini.model.Repository;
import com.gitmini.service.GitService;
import com.gitmini.service.RepositoryManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * 메인 화면 컨트롤러.
 * <p>
 * 사이드바(레포 목록) + 메인 콘텐츠(파일 변경, Diff, 커밋, 액션, 히스토리, Command Log) + 상태바를 관리한다.
 * 레포 미선택 시 welcomePane, 선택 시 repoDetailPane을 표시한다.
 * </p>
 * <p>
 * 모든 Git 작업은 {@code TaskManager.run()}으로 감싸서 비동기 실행한다.
 * EventBus 구독은 {@link #initialize()}에서 등록한다.
 * 구독 해제는 앱 종료 시 또는 필요 시 명시적으로 수행해야 한다.
 * </p>
 */
public class MainController {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    // ========== FXML 바인딩: 사이드바 ==========

    @FXML private SplitPane mainSplitPane;
    @FXML private VBox sidebar;
    @FXML private ListView<Repository> repoListView;

    // ========== FXML 바인딩: 메인 콘텐츠 (전환) ==========

    @FXML private StackPane mainContent;
    @FXML private VBox welcomePane;
    @FXML private Label welcomeLabel;
    @FXML private VBox repoDetailPane;

    // ========== FXML 바인딩: 액션 바 ==========

    @FXML private HBox actionBar;
    @FXML private Label repoNameLabel;
    @FXML private ComboBox<String> branchComboBox;
    @FXML private Button newBranchBtn;
    @FXML private Label aheadBehindLabel;
    @FXML private Button fetchBtn;
    @FXML private Button pullBtn;
    @FXML private Button pushBtn;

    // ========== FXML 바인딩: 파일 변경 영역 ==========

    @FXML private SplitPane contentSplitPane;
    @FXML private ListView<FileChange> unstagedListView;
    @FXML private ListView<FileChange> stagedListView;
    @FXML private Button stageAllBtn;
    @FXML private Button stageBtn;
    @FXML private Button unstageBtn;
    @FXML private Button unstageAllBtn;

    // ========== FXML 바인딩: Diff 뷰어 ==========

    @FXML private Label diffFileLabel;
    @FXML private ScrollPane diffScrollPane;
    @FXML private VBox diffContent;

    // ========== FXML 바인딩: 커밋 영역 ==========

    @FXML private VBox commitArea;
    @FXML private TextArea commitMessageArea;
    @FXML private CheckBox amendCheckBox;
    @FXML private Button commitBtn;

    // ========== FXML 바인딩: 커밋 히스토리 ==========

    @FXML private TitledPane commitHistoryPane;
    @FXML private ListView<CommitInfo> commitHistoryListView;

    // ========== FXML 바인딩: Command Log ==========

    @FXML private TitledPane commandLogPane;
    @FXML private ListView<GitCommandRecord> commandLogListView;

    // ========== FXML 바인딩: 상태바 ==========

    @FXML private HBox statusBar;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private Label statusLabel;
    @FXML private Label branchStatusLabel;
    @FXML private Label changesCountLabel;

    // ========== 내부 상태 ==========

    /** 현재 선택된 레포. null이면 미선택. */
    private Repository selectedRepo;

    /** 파일 선택 리스너 재진입 방지 플래그. */
    private boolean updatingFileSelection = false;

    // ========== 초기화 ==========

    @FXML
    public void initialize() {
        log.info("MainController 초기화");

        // StackPane 화면 전환: managed를 visible에 바인딩
        welcomePane.managedProperty().bind(welcomePane.visibleProperty());
        repoDetailPane.managedProperty().bind(repoDetailPane.visibleProperty());

        // 사이드바: Custom ListCell 설정
        repoListView.setCellFactory(listView -> new RepoListCell(this::removeRepo));

        // 사이드바: 선택 이벤트
        repoListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldRepo, newRepo) -> onRepoSelected(newRepo));

        // 파일 변경 목록: Custom ListCell 설정
        unstagedListView.setCellFactory(lv -> new FileChangeListCell());
        stagedListView.setCellFactory(lv -> new FileChangeListCell());

        // 파일 변경 목록: 선택 이벤트 → Diff 연동
        unstagedListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldFile, newFile) -> onFileSelected(newFile, false));
        stagedListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldFile, newFile) -> onFileSelected(newFile, true));

        // 초기 상태
        showWelcome();

        // 키보드 단축키 등록 (Scene 생성 후 지연 실행)
        Platform.runLater(this::setupKeyboardShortcuts);

        // 등록된 레포 목록 로드 (비동기)
        loadRepoList();
    }

    // ========== 사이드바: 레포 목록 로드 ==========

    /**
     * 등록된 레포 목록을 비동기로 로드하여 사이드바에 표시한다.
     */
    private void loadRepoList() {
        loadRepoListWithStatus("✓ Ready");
    }

    /**
     * 등록된 레포 목록을 비동기로 로드하고, 완료 후 지정된 상태 메시지를 표시한다.
     */
    private void loadRepoListWithStatus(String completionStatus) {
        RepositoryManager repoManager = GitMiniApp.getRepositoryManager();
        TaskManager taskManager = GitMiniApp.getTaskManager();
        if (repoManager == null || taskManager == null) return;

        setStatus("레포 목록 로딩...");
        taskManager.run(
                repoManager::getRepositories,
                repos -> {
                    repoListView.setItems(FXCollections.observableArrayList(repos));
                    setStatus(completionStatus);
                    log.info("레포 목록 로드 완료: {}개", repos.size());

                    // 이전에 선택된 레포 복원
                    if (selectedRepo != null) {
                        for (Repository r : repoListView.getItems()) {
                            if (r.getPath().equals(selectedRepo.getPath())) {
                                repoListView.getSelectionModel().select(r);
                                break;
                            }
                        }
                    }
                },
                error -> {
                    log.error("레포 목록 로드 실패", error);
                    setStatus("레포 목록 로드 실패: " + error.getMessage());
                }
        );
    }

    // ========== 사이드바: 레포 선택 ==========

    /**
     * 사이드바에서 레포를 선택했을 때 호출된다.
     */
    private void onRepoSelected(Repository repo) {
        if (repo == null) {
            selectedRepo = null;
            showWelcome();
            updateStatusBar(null);
            return;
        }

        // 이미 같은 레포가 선택된 상태면 중복 갱신 방지 (우클릭으로 선택 변경 시 불필요한 재로드 차단)
        if (selectedRepo != null && selectedRepo.getPath().equals(repo.getPath())) {
            return;
        }

        selectedRepo = repo;
        log.info("레포 선택: {}", repo.getName());

        showRepoDetail();

        // 액션 바 기본 정보 즉시 표시
        repoNameLabel.setText(repo.getName());

        // 이전 레포의 잔여 데이터 정리
        unstagedListView.setItems(FXCollections.observableArrayList());
        stagedListView.setItems(FXCollections.observableArrayList());
        diffFileLabel.setText("Diff");
        diffContent.getChildren().clear();
        updateStatusBar(repo);

        // 상세 정보 비동기 로드 (파일 목록, 브랜치 등)
        setStatus("상태 갱신 중...");
        refreshRepoDetail(repo);
    }

    /**
     * 선택된 레포의 상세 정보를 비동기로 갱신한다.
     * <p>
     * 백그라운드 스레드에서 Git 명령을 실행하고, 결과를 별도 데이터 객체에 담아
     * UI 스레드에서 Repository 및 UI에 적용한다 (스레드 안전성 보장).
     * </p>
     */
    private void refreshRepoDetail(Repository repo) {
        refreshRepoDetailWithStatus(repo, "✓ Ready");
    }

    /**
     * 선택된 레포의 상세 정보를 비동기로 갱신하고, 완료 후 지정된 상태 메시지를 표시한다.
     */
    private void refreshRepoDetailWithStatus(Repository repo, String completionStatus) {
        TaskManager taskManager = GitMiniApp.getTaskManager();
        GitService gitService = GitMiniApp.getGitService();
        if (taskManager == null || gitService == null) return;

        Path repoPath = Path.of(repo.getPath());

        taskManager.run(
                () -> {
                    // 백그라운드: 모든 Git 정보를 1회씩만 조회하여 데이터 객체에 담음
                    String currentBranch = gitService.currentBranch(repoPath);

                    List<FileChange> allChanges = gitService.status(repoPath);
                    List<FileChange> unstaged = allChanges.stream()
                            .filter(f -> !f.staged()).toList();
                    List<FileChange> staged = allChanges.stream()
                            .filter(FileChange::staged).toList();

                    int[] ab = gitService.aheadBehind(repoPath);

                    List<String> branches = gitService.branches(repoPath).stream()
                            .map(b -> b.name()).toList();

                    var logList = gitService.log(repoPath, 1);
                    String lastCommitMsg = logList.isEmpty() ? "" : logList.get(0).message();
                    var lastCommitDate = logList.isEmpty() ? null : logList.get(0).date();

                    return new RepoDetailData(
                            currentBranch, unstaged, staged, branches,
                            allChanges.size(), ab[0], ab[1],
                            lastCommitMsg, lastCommitDate);
                },
                data -> {
                    // UI 스레드: Repository 필드 갱신 (스레드 안전)
                    repo.setCurrentBranch(data.currentBranch);
                    repo.setChangedFileCount(data.changedFileCount);
                    repo.setAhead(data.ahead);
                    repo.setBehind(data.behind);
                    repo.setLastCommitMessage(data.lastCommitMsg);
                    repo.setLastCommitDate(data.lastCommitDate);

                    // UI 갱신
                    unstagedListView.setItems(FXCollections.observableArrayList(data.unstaged));
                    stagedListView.setItems(FXCollections.observableArrayList(data.staged));

                    branchComboBox.setItems(FXCollections.observableArrayList(data.branches));
                    branchComboBox.setValue(data.currentBranch);

                    updateAheadBehind(repo);
                    updateStatusBar(repo);

                    // 사이드바도 갱신 (변경 파일 수 등 반영)
                    repoListView.refresh();

                    setStatus(completionStatus);
                },
                error -> {
                    log.error("레포 상세 로드 실패: {}", repo.getName(), error);
                    setStatus("상태 갱신 실패: " + error.getMessage());
                }
        );
    }

    /** refreshRepoDetail의 백그라운드 결과를 담는 불변 데이터 객체. */
    private record RepoDetailData(
            String currentBranch,
            List<FileChange> unstaged,
            List<FileChange> staged,
            List<String> branches,
            int changedFileCount,
            int ahead,
            int behind,
            String lastCommitMsg,
            java.time.LocalDateTime lastCommitDate
    ) {}

    // ========== 사이드바 액션 ==========

    @FXML
    private void onAddRepo() {
        log.info("레포 추가 버튼 클릭");

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Git 레포지토리 폴더 선택");
        File selected = chooser.showDialog(sidebar.getScene().getWindow());

        if (selected == null) return; // 취소

        RepositoryManager repoManager = GitMiniApp.getRepositoryManager();
        TaskManager taskManager = GitMiniApp.getTaskManager();
        if (repoManager == null || taskManager == null) return;

        setStatus("레포 추가 중...");
        taskManager.run(
                () -> {
                    repoManager.add(selected.toPath());
                    return null;
                },
                result -> {
                    log.info("레포 추가 완료: {}", selected.getName());
                    // loadRepoList 완료 후 성공 메시지를 표시하기 위해 별도 메시지 전달
                    loadRepoListWithStatus("✓ 레포 추가 완료: " + selected.getName());
                },
                error -> {
                    log.error("레포 추가 실패: {}", selected, error);
                    showErrorAlert("레포 추가 실패", error.getMessage());
                    setStatus("레포 추가 실패");
                }
        );
    }

    @FXML
    private void onCloneRepo() {
        log.info("Clone 버튼 클릭");
        // Phase 4에서 구현
        setStatus("Clone — Phase 4에서 구현 예정");
    }

    // ========== 사이드바: 레포 제거 ==========

    /**
     * 컨텍스트 메뉴에서 레포 제거가 선택되었을 때 호출된다.
     */
    private void removeRepo(Repository repo) {
        log.info("레포 제거 요청: {}", repo.getName());

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(sidebar.getScene().getWindow());
        confirm.setTitle("레포 제거");
        confirm.setHeaderText(repo.getName());
        confirm.setContentText("이 레포를 목록에서 제거하시겠습니까?\n(로컬 파일은 삭제되지 않습니다)");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                RepositoryManager repoManager = GitMiniApp.getRepositoryManager();
                if (repoManager == null) return;

                repoManager.remove(Path.of(repo.getPath()));

                // 현재 선택된 레포를 제거했으면 welcome으로 전환
                if (selectedRepo != null && selectedRepo.getPath().equals(repo.getPath())) {
                    selectedRepo = null;
                    showWelcome();
                    updateStatusBar(null);
                }

                loadRepoList();
                setStatus("✓ 레포 제거 완료: " + repo.getName());
                log.info("레포 제거 완료: {}", repo.getName());
            }
        });
    }

    // ========== 화면 전환 ==========

    private void showWelcome() {
        welcomePane.setVisible(true);
        repoDetailPane.setVisible(false);
    }

    private void showRepoDetail() {
        welcomePane.setVisible(false);
        repoDetailPane.setVisible(true);
    }

    // ========== 상태바 / 피드백 ==========

    private void setStatus(String message) {
        statusLabel.setText(message);
    }

    private void updateStatusBar(Repository repo) {
        if (repo == null) {
            branchStatusLabel.setText("");
            changesCountLabel.setText("");
            return;
        }
        branchStatusLabel.setText(repo.getCurrentBranch());

        // unstaged + staged 별도 표시
        int unstaged = unstagedListView.getItems() != null ? unstagedListView.getItems().size() : 0;
        int staged = stagedListView.getItems() != null ? stagedListView.getItems().size() : 0;
        if (unstaged > 0 || staged > 0) {
            StringBuilder sb = new StringBuilder();
            if (unstaged > 0) sb.append(unstaged).append(" 변경");
            if (unstaged > 0 && staged > 0) sb.append(" | ");
            if (staged > 0) sb.append(staged).append(" 스테이징");
            changesCountLabel.setText(sb.toString());
        } else {
            changesCountLabel.setText("");
        }
    }

    private void updateAheadBehind(Repository repo) {
        int ahead = repo.getAhead();
        int behind = repo.getBehind();
        StringBuilder sb = new StringBuilder();
        if (ahead > 0) sb.append("↑").append(ahead);
        if (ahead > 0 && behind > 0) sb.append(" ");
        if (behind > 0) sb.append("↓").append(behind);
        aheadBehindLabel.setText(sb.toString());
    }

    private void showErrorAlert(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(sidebar.getScene().getWindow());
        alert.setTitle("오류");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    // ========== 브랜치 액션 ==========

    @FXML
    private void onNewBranch() {
        log.info("새 브랜치 생성 버튼 클릭");
        // Step 8에서 구현
        setStatus("새 브랜치 — Step 8에서 구현 예정");
    }

    // ========== 원격 액션 ==========

    @FXML
    private void onFetch() {
        log.info("Fetch 버튼 클릭");
        // Step 8에서 구현
        setStatus("Fetch — Step 8에서 구현 예정");
    }

    @FXML
    private void onPull() {
        log.info("Pull 버튼 클릭");
        // Step 8에서 구현
        setStatus("Pull — Step 8에서 구현 예정");
    }

    @FXML
    private void onPush() {
        log.info("Push 버튼 클릭");
        // Step 8에서 구현
        setStatus("Push — Step 8에서 구현 예정");
    }

    // ========== 스테이징 액션 ==========

    @FXML
    private void onStageAll() {
        if (selectedRepo == null) return;
        log.info("전체 Stage 클릭");

        TaskManager taskManager = GitMiniApp.getTaskManager();
        GitService gitService = GitMiniApp.getGitService();
        if (taskManager == null || gitService == null) return;

        Repository targetRepo = selectedRepo; // 콜백 시점에 selectedRepo가 바뀌어도 원래 레포를 갱신
        Path repoPath = Path.of(targetRepo.getPath());
        setStatus("전체 스테이징 중...");
        taskManager.run(
                () -> { gitService.addAll(repoPath); return null; },
                result -> refreshRepoDetailWithStatus(targetRepo, "✓ 전체 Stage 완료"),
                error -> {
                    log.error("전체 Stage 실패", error);
                    setStatus("Stage 실패: " + error.getMessage());
                }
        );
    }

    @FXML
    private void onStage() {
        if (selectedRepo == null) return;
        FileChange selected = unstagedListView.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        log.info("선택 Stage: {}", selected.path());

        TaskManager taskManager = GitMiniApp.getTaskManager();
        GitService gitService = GitMiniApp.getGitService();
        if (taskManager == null || gitService == null) return;

        Repository targetRepo = selectedRepo;
        Path repoPath = Path.of(targetRepo.getPath());
        setStatus("스테이징 중...");
        taskManager.run(
                () -> { gitService.add(repoPath, List.of(selected.path())); return null; },
                result -> refreshRepoDetailWithStatus(targetRepo, "✓ Stage 완료: " + selected.path()),
                error -> {
                    log.error("Stage 실패: {}", selected.path(), error);
                    setStatus("Stage 실패: " + error.getMessage());
                }
        );
    }

    @FXML
    private void onUnstage() {
        if (selectedRepo == null) return;
        FileChange selected = stagedListView.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        log.info("선택 Unstage: {}", selected.path());

        TaskManager taskManager = GitMiniApp.getTaskManager();
        GitService gitService = GitMiniApp.getGitService();
        if (taskManager == null || gitService == null) return;

        Repository targetRepo = selectedRepo;
        Path repoPath = Path.of(targetRepo.getPath());
        setStatus("언스테이징 중...");
        taskManager.run(
                () -> { gitService.unstage(repoPath, List.of(selected.path())); return null; },
                result -> refreshRepoDetailWithStatus(targetRepo, "✓ Unstage 완료: " + selected.path()),
                error -> {
                    log.error("Unstage 실패: {}", selected.path(), error);
                    setStatus("Unstage 실패: " + error.getMessage());
                }
        );
    }

    @FXML
    private void onUnstageAll() {
        if (selectedRepo == null) return;
        log.info("전체 Unstage 클릭");

        List<FileChange> stagedFiles = stagedListView.getItems();
        if (stagedFiles.isEmpty()) return;

        TaskManager taskManager = GitMiniApp.getTaskManager();
        GitService gitService = GitMiniApp.getGitService();
        if (taskManager == null || gitService == null) return;

        Repository targetRepo = selectedRepo;
        Path repoPath = Path.of(targetRepo.getPath());
        List<String> paths = stagedFiles.stream().map(FileChange::path).toList();
        setStatus("전체 언스테이징 중...");
        taskManager.run(
                () -> { gitService.unstage(repoPath, paths); return null; },
                result -> refreshRepoDetailWithStatus(targetRepo, "✓ 전체 Unstage 완료"),
                error -> {
                    log.error("전체 Unstage 실패", error);
                    setStatus("Unstage 실패: " + error.getMessage());
                }
        );
    }

    // ========== 파일 선택 → Diff 연동 ==========

    /**
     * 파일 변경 목록에서 파일을 선택했을 때 호출된다.
     * staged/unstaged에 따라 적절한 diff를 비동기로 로드하여 Diff 뷰어에 표시한다.
     */
    private void onFileSelected(FileChange file, boolean staged) {
        // 재진입 방지: clearSelection()이 반대쪽 리스너를 트리거하여
        // diffFileLabel을 "Diff"로 덮어쓰는 연쇄 호출 차단
        if (updatingFileSelection) return;
        updatingFileSelection = true;
        try {
            if (file == null) {
                diffFileLabel.setText("Diff");
                diffContent.getChildren().clear();
                return;
            }

            log.debug("파일 선택: {} (staged={})", file.path(), staged);
            diffFileLabel.setText(file.path());

            // 다른 쪽 리스트의 선택 해제
            if (staged) {
                unstagedListView.getSelectionModel().clearSelection();
            } else {
                stagedListView.getSelectionModel().clearSelection();
            }

            // Diff 비동기 로드
            loadDiff(file, staged);
        } finally {
            updatingFileSelection = false;
        }
    }

    /**
     * 선택된 파일의 diff를 비동기로 로드하여 Diff 뷰어에 표시한다.
     * staged 파일이면 git diff --cached, unstaged면 git diff를 사용한다.
     */
    /** 현재 diff 로딩 대상 파일. 콜백에서 스테일 체크용. */
    private String currentDiffFilePath = null;

    private void loadDiff(FileChange file, boolean staged) {
        if (selectedRepo == null) return;

        TaskManager taskManager = GitMiniApp.getTaskManager();
        GitService gitService = GitMiniApp.getGitService();
        if (taskManager == null || gitService == null) return;

        Path repoPath = Path.of(selectedRepo.getPath());

        // 현재 diff 대상 파일 기록 (콜백에서 스테일 체크)
        currentDiffFilePath = file.path();

        // 로딩 표시 + 스크롤 리셋
        diffContent.getChildren().clear();
        diffScrollPane.setVvalue(0);
        Label loading = new Label("Diff 로딩 중...");
        loading.getStyleClass().add("diff-line-context");
        diffContent.getChildren().add(loading);

        taskManager.run(
                () -> {
                    if (staged) {
                        return gitService.diffStaged(repoPath);
                    } else {
                        if (file.type() == FileChange.ChangeType.UNTRACKED) {
                            return List.<DiffEntry>of();
                        }
                        return gitService.diffFile(repoPath, file.path());
                    }
                },
                entries -> {
                    // 스테일 체크: 콜백 실행 시점에 다른 파일이 선택되었으면 무시
                    if (!file.path().equals(currentDiffFilePath)) {
                        return;
                    }

                    // staged diff는 전체를 반환하므로 해당 파일만 필터링
                    List<DiffEntry> filtered;
                    if (staged) {
                        filtered = entries.stream()
                                .filter(e -> e.newPath().equals(file.path())
                                        || e.oldPath().equals(file.path()))
                                .toList();
                    } else {
                        filtered = entries;
                    }

                    if (filtered.isEmpty() && file.type() == FileChange.ChangeType.UNTRACKED) {
                        diffContent.getChildren().clear();
                        Label info = new Label("새 파일 (untracked) — diff 없음");
                        info.getStyleClass().add("diff-line-hunk");
                        diffContent.getChildren().add(info);
                    } else {
                        DiffRenderer.render(diffContent, filtered);
                    }

                    // 렌더링 후 스크롤을 맨 위로
                    diffScrollPane.setVvalue(0);
                },
                error -> {
                    if (!file.path().equals(currentDiffFilePath)) return;
                    log.error("Diff 로드 실패: {}", file.path(), error);
                    diffContent.getChildren().clear();
                    Label err = new Label("Diff 로드 실패: " + error.getMessage());
                    err.getStyleClass().add("diff-line-removed");
                    diffContent.getChildren().add(err);
                }
        );
    }

    // ========== 커밋 액션 ==========

    @FXML
    private void onCommit() {
        if (selectedRepo == null) return;

        String message = commitMessageArea.getText();
        if (message == null || message.isBlank()) {
            showErrorAlert("커밋 실패", "커밋 메시지를 입력하세요.");
            return;
        }

        boolean amend = amendCheckBox.isSelected();

        // staged 파일이 없으면 경고 (amend인 경우 메시지만 변경 가능하므로 제외)
        if (!amend && (stagedListView.getItems() == null || stagedListView.getItems().isEmpty())) {
            showErrorAlert("커밋 실패", "스테이징된 파일이 없습니다.\n먼저 파일을 Stage하세요.");
            return;
        }
        log.info("Commit 실행: amend={}, message={}", amend, message.lines().findFirst().orElse(""));

        TaskManager taskManager = GitMiniApp.getTaskManager();
        GitService gitService = GitMiniApp.getGitService();
        if (taskManager == null || gitService == null) return;

        Repository targetRepo = selectedRepo;
        Path repoPath = Path.of(targetRepo.getPath());

        setStatus("커밋 중...");
        commitBtn.setDisable(true);

        taskManager.run(
                () -> {
                    if (amend) {
                        gitService.amend(repoPath, message);
                    } else {
                        gitService.commit(repoPath, message);
                    }
                    return null;
                },
                result -> {
                    commitBtn.setDisable(false);
                    commitMessageArea.clear();
                    amendCheckBox.setSelected(false);
                    refreshRepoDetailWithStatus(targetRepo,
                            amend ? "✓ Amend 완료" : "✓ 커밋 완료");
                    log.info("커밋 완료: {}", targetRepo.getName());
                },
                error -> {
                    commitBtn.setDisable(false);
                    log.error("커밋 실패: {}", targetRepo.getName(), error);
                    showErrorAlert("커밋 실패", error.getMessage());
                    setStatus("커밋 실패");
                }
        );
    }

    // ========== 키보드 단축키 ==========

    /**
     * Scene에 키보드 단축키를 등록한다.
     * MainController 외부에서 Scene이 생성된 후 호출해야 한다.
     * 현재는 initialize()에서 Platform.runLater로 지연 등록한다.
     */
    private void setupKeyboardShortcuts() {
        var scene = sidebar.getScene();
        if (scene == null) return;

        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.ENTER, KeyCombination.CONTROL_DOWN),
                this::onCommit
        );
        log.debug("키보드 단축키 등록: Ctrl+Enter → Commit");
    }
}
