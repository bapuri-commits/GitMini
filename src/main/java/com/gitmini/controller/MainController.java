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
import javafx.collections.ObservableList;
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

    /** 파일 변경 목록에 한 번에 표시할 최대 개수 (큰 레포 UI 프리즈 방지). 검색으로 범위 좁힘 가능. */
    private static final int MAX_FILE_LIST_SIZE = 1000;
    /** 커밋 히스토리 목록에 표시할 최대 커밋 수. */
    private static final int COMMIT_HISTORY_MAX = 50;

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
    @FXML private TextField unstagedSearchField;
    @FXML private TextField stagedSearchField;
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

    /** 브랜치 ComboBox 프로그래밍 갱신 중 onBranchChanged 방지 플래그. */
    private boolean updatingBranchComboBox = false;

    /** 현재 표시 중인 레포의 전체 unstaged/staged 개수 (목록이 잘렸을 때 상태바 표시용). */
    private int totalUnstagedCount = 0;
    private int totalStagedCount = 0;

    /** 마지막으로 폴더를 선택한 디렉토리 (레포 추가 시 기억). */
    private File lastBrowsedDir = null;

    /** Command Log EventBus 핸들러 (구독 해제용 참조). */
    private java.util.function.Consumer<GitCommandRecord> commandLogEventHandler;

    /** 검색 필터 적용 전 전체 목록 (최대 MAX_FILE_LIST_SIZE개). 검색 시 여기서 필터링. */
    private ObservableList<FileChange> unstagedFullList = FXCollections.observableArrayList();
    private ObservableList<FileChange> stagedFullList = FXCollections.observableArrayList();

    // ========== 초기화 ==========

    @FXML
    public void initialize() {
        log.info("MainController 초기화");

        // StackPane 화면 전환: managed를 visible에 바인딩
        welcomePane.managedProperty().bind(welcomePane.visibleProperty());
        repoDetailPane.managedProperty().bind(repoDetailPane.visibleProperty());
        // 상태바 로딩 인디케이터: 숨김 시 공간 차지 안 함
        progressIndicator.managedProperty().bind(progressIndicator.visibleProperty());

        // 사이드바: Custom ListCell 설정
        repoListView.setCellFactory(listView -> new RepoListCell(this::removeRepo));

        // 사이드바: 선택 이벤트
        repoListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldRepo, newRepo) -> onRepoSelected(newRepo));

        // 파일 변경 목록: Custom ListCell 설정
        unstagedListView.setCellFactory(lv -> new FileChangeListCell());
        stagedListView.setCellFactory(lv -> new FileChangeListCell());

        // 파일 변경 목록: 우클릭 컨텍스트 메뉴 (Unstaged)
        ContextMenu unstagedCtxMenu = new ContextMenu();
        MenuItem stageCtxItem = new MenuItem("Stage");
        stageCtxItem.setOnAction(e -> onStage());
        MenuItem discardItem = new MenuItem("변경 취소 (Discard)");
        discardItem.setOnAction(e -> discardSelectedFile());
        MenuItem copyPathUnstaged = new MenuItem("경로 복사");
        copyPathUnstaged.setOnAction(e -> copySelectedFilePath(false));
        MenuItem openInExplorerUnstaged = new MenuItem("탐색기에서 열기");
        openInExplorerUnstaged.setOnAction(e -> openSelectedFileInExplorer(false));
        unstagedCtxMenu.getItems().addAll(stageCtxItem, discardItem, new SeparatorMenuItem(), copyPathUnstaged, openInExplorerUnstaged);
        unstagedListView.setContextMenu(unstagedCtxMenu);

        // 파일 변경 목록: 우클릭 컨텍스트 메뉴 (Staged)
        ContextMenu stagedCtxMenu = new ContextMenu();
        MenuItem unstageCtxItem = new MenuItem("Unstage");
        unstageCtxItem.setOnAction(e -> onUnstage());
        MenuItem copyPathStaged = new MenuItem("경로 복사");
        copyPathStaged.setOnAction(e -> copySelectedFilePath(true));
        MenuItem openInExplorerStaged = new MenuItem("탐색기에서 열기");
        openInExplorerStaged.setOnAction(e -> openSelectedFileInExplorer(true));
        stagedCtxMenu.getItems().addAll(unstageCtxItem, new SeparatorMenuItem(), copyPathStaged, openInExplorerStaged);
        stagedListView.setContextMenu(stagedCtxMenu);

        // 파일 변경 목록: 선택 이벤트 → Diff 연동
        unstagedListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldFile, newFile) -> onFileSelected(newFile, false));
        stagedListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldFile, newFile) -> onFileSelected(newFile, true));

        // 파일 목록 검색: 입력 시 해당 목록만 필터링 (Unstaged / Staged 각각)
        if (unstagedSearchField != null) {
            unstagedSearchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilterUnstaged());
        }
        if (stagedSearchField != null) {
            stagedSearchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilterStaged());
        }

        // 커밋 히스토리: ListCell — short hash, 메시지, 날짜 한 줄 표시
        commitHistoryListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(CommitInfo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                String shortHash = item.hash().length() > 7 ? item.hash().substring(0, 7) : item.hash();
                String dateStr = item.date() != null ? item.date().toString().replace("T", " ").substring(0, Math.min(16, item.date().toString().length())) : "";
                setText(shortHash + "  " + item.message() + "  " + dateStr);
            }
        });

        // Command Log: CellFactory — 시각, 성공/실패, 명령어, 소요시간 표시
        java.time.format.DateTimeFormatter cmdTimeFormat = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");
        commandLogListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(GitCommandRecord item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                String time = item.timestamp() != null ? item.timestamp().format(cmdTimeFormat) : "";
                String icon = item.success() ? "✓" : "✗";
                String dur = item.durationMs() + "ms";
                setText(time + "  " + icon + "  " + item.command() + "  (" + dur + ")");
                setStyle(item.success() ? "" : "-fx-text-fill: #e06060;");
            }
        });

        // Command Log: 우클릭 → 복사
        ContextMenu cmdLogMenu = new ContextMenu();
        MenuItem copyCmd = new MenuItem("명령어 복사");
        copyCmd.setOnAction(e -> {
            GitCommandRecord sel = commandLogListView.getSelectionModel().getSelectedItem();
            if (sel != null) {
                javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
                cc.putString(sel.command());
                javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
            }
        });
        MenuItem copyOutput = new MenuItem("출력 복사");
        copyOutput.setOnAction(e -> {
            GitCommandRecord sel = commandLogListView.getSelectionModel().getSelectedItem();
            if (sel != null && sel.output() != null) {
                javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
                cc.putString(sel.output());
                javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
            }
        });
        cmdLogMenu.getItems().addAll(copyCmd, copyOutput);
        commandLogListView.setContextMenu(cmdLogMenu);

        // Command Log: EventBus 구독 — git 명령 실행마다 실시간 추가 (원본 GitCommandRecord)
        commandLogEventHandler = record -> {
            commandLogListView.getItems().add(0, record);
            // 최대 500건 유지
            if (commandLogListView.getItems().size() > 500) {
                commandLogListView.getItems().remove(500, commandLogListView.getItems().size());
            }
        };
        com.gitmini.event.EventBus.getInstance().subscribe(
                GitCommandRecord.class, commandLogEventHandler);

        // 브랜치 ComboBox: 변경 이벤트 → 브랜치 전환
        branchComboBox.valueProperty().addListener(
                (obs, oldBranch, newBranch) -> onBranchChanged(newBranch));

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
        unstagedFullList.clear();
        stagedFullList.clear();
        if (unstagedSearchField != null) unstagedSearchField.clear();
        if (stagedSearchField != null) stagedSearchField.clear();
        unstagedListView.setItems(FXCollections.observableArrayList());
        stagedListView.setItems(FXCollections.observableArrayList());
        commitHistoryListView.setItems(FXCollections.observableArrayList());
        totalUnstagedCount = 0;
        totalStagedCount = 0;
        diffFileLabel.setText("Diff");
        diffContent.getChildren().clear();
        commitMessageArea.clear();
        amendCheckBox.setSelected(false);
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
                    // 커밋 없는 레포(HEAD 없음)에 대한 방어적 처리 포함
                    String currentBranch;
                    try {
                        currentBranch = gitService.currentBranch(repoPath);
                    } catch (Exception e) {
                        currentBranch = "(no commits)";
                    }

                    List<FileChange> allChanges = gitService.status(repoPath);
                    List<FileChange> unstaged = allChanges.stream()
                            .filter(f -> !f.staged()).toList();
                    List<FileChange> staged = allChanges.stream()
                            .filter(FileChange::staged).toList();

                    int[] ab = gitService.aheadBehind(repoPath);

                    List<String> branches;
                    try {
                        branches = gitService.branches(repoPath).stream()
                                .map(b -> b.name()).toList();
                    } catch (Exception e) {
                        branches = List.of();
                    }

                    List<com.gitmini.model.CommitInfo> logList;
                    try {
                        logList = gitService.log(repoPath, COMMIT_HISTORY_MAX);
                    } catch (Exception e) {
                        logList = List.of();
                    }
                    String lastCommitMsg = logList.isEmpty() ? "" : logList.get(0).message();
                    var lastCommitDate = logList.isEmpty() ? null : logList.get(0).date();
                    boolean hasRemote = gitService.hasRemote(repoPath);

                    return new RepoDetailData(
                            currentBranch, unstaged, staged, branches,
                            allChanges.size(), ab[0], ab[1], hasRemote,
                            lastCommitMsg, lastCommitDate, logList);
                },
                data -> {
                    // UI 스레드: Repository 필드 갱신 (스레드 안전)
                    repo.setCurrentBranch(data.currentBranch);
                    repo.setChangedFileCount(data.changedFileCount);
                    repo.setAhead(data.ahead);
                    repo.setBehind(data.behind);
                    repo.setHasRemote(data.hasRemote());
                    repo.setLastCommitMessage(data.lastCommitMsg);
                    repo.setLastCommitDate(data.lastCommitDate);

                    // 파일 목록: 큰 레포 방지를 위해 최대 MAX_FILE_LIST_SIZE개. 검색 필터용 전체 목록 저장
                    List<FileChange> unstagedToShow = data.unstaged.size() <= MAX_FILE_LIST_SIZE
                            ? data.unstaged
                            : data.unstaged.stream().limit(MAX_FILE_LIST_SIZE).toList();
                    List<FileChange> stagedToShow = data.staged.size() <= MAX_FILE_LIST_SIZE
                            ? data.staged
                            : data.staged.stream().limit(MAX_FILE_LIST_SIZE).toList();
                    unstagedFullList.clear();
                    unstagedFullList.addAll(unstagedToShow);
                    stagedFullList.clear();
                    stagedFullList.addAll(stagedToShow);
                    if (unstagedSearchField != null) unstagedSearchField.clear();
                    if (stagedSearchField != null) stagedSearchField.clear();
                    applyFilterUnstaged();
                    applyFilterStaged();
                    totalUnstagedCount = data.unstaged.size();
                    totalStagedCount = data.staged.size();

                    // 브랜치 ComboBox 갱신 (onBranchChanged 트리거 방지)
                    updatingBranchComboBox = true;
                    try {
                        branchComboBox.setItems(FXCollections.observableArrayList(data.branches));
                        branchComboBox.setValue(data.currentBranch);
                    } finally {
                        updatingBranchComboBox = false;
                    }

                    // 커밋 히스토리 목록 갱신
                    commitHistoryListView.setItems(FXCollections.observableArrayList(data.commitHistory()));

                    updateAheadBehind(repo);
                    updateStatusBar(repo);

                    // 사이드바도 갱신 (변경 파일 수 등 반영)
                    repoListView.refresh();

                    boolean truncated = data.unstaged.size() > MAX_FILE_LIST_SIZE || data.staged.size() > MAX_FILE_LIST_SIZE;
                    setStatus(truncated
                            ? completionStatus + " (파일 목록 " + MAX_FILE_LIST_SIZE + "개만 표시)"
                            : completionStatus);
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
            boolean hasRemote,
            String lastCommitMsg,
            java.time.LocalDateTime lastCommitDate,
            List<CommitInfo> commitHistory
    ) {}

    // ========== 사이드바 액션 ==========

    @FXML
    private void onAddRepo() {
        log.info("레포 추가 버튼 클릭");

        // 여러 폴더를 연속으로 추가할 수 있도록 루프
        boolean addMore = true;
        int addedCount = 0;

        RepositoryManager repoManager = GitMiniApp.getRepositoryManager();
        TaskManager taskManager = GitMiniApp.getTaskManager();
        if (repoManager == null || taskManager == null) return;

        while (addMore) {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Git 레포지토리 폴더 선택" + (addedCount > 0 ? " (추가 선택, 취소하면 종료)" : ""));
            if (lastBrowsedDir != null && lastBrowsedDir.isDirectory()) {
                chooser.setInitialDirectory(lastBrowsedDir);
            }

            javafx.stage.Window owner = getMainWindow();
            File selected = chooser.showDialog(owner);

            if (selected == null) {
                addMore = false; // 취소 → 루프 종료
            } else {
                lastBrowsedDir = selected.getParentFile(); // 마지막 경로 기억 (부모 폴더)
                addedCount++;
                final String name = selected.getName();
                final File finalSelected = selected;
                final int currentCount = addedCount;

                setStatus("레포 추가 중: " + name + "...");
                taskManager.run(
                        () -> {
                            repoManager.add(finalSelected.toPath());
                            return null;
                        },
                        result -> {
                            log.info("레포 추가 완료: {}", name);
                            loadRepoListWithStatus("✓ 레포 " + currentCount + "개 추가 완료");
                        },
                        error -> {
                            log.error("레포 추가 실패: {}", finalSelected, error);
                            showErrorAlert("레포 추가 실패", name + ": " + error.getMessage());
                            setStatus("레포 추가 실패");
                        }
                );
            }
        }
        if (addedCount == 0) {
            setStatus("레포 추가 취소");
        }
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
        javafx.stage.Window owner = getMainWindow();
        if (owner != null) confirm.initOwner(owner);
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

    /** 작업 진행 중 상태 (로딩 인디케이터 표시 + 메시지). */
    private void setStatusLoading(String message) {
        progressIndicator.setVisible(true);
        statusLabel.setText(message);
    }

    /** 작업 완료 상태 (로딩 숨김 + 메시지 + 5초 후 Ready 자동 복원). */
    private void setStatusDone(String message) {
        progressIndicator.setVisible(false);
        statusLabel.setText(message);
        // "✓ Ready" 자체일 때는 페이드 불필요
        if (!"✓ Ready".equals(message)) {
            scheduleStatusFade();
        }
    }

    /** 일반 상태 메시지 설정. 진행 중("...") 이면 로딩 표시, 아니면 완료 처리. */
    private void setStatus(String message) {
        if (message != null && message.endsWith("...")) {
            setStatusLoading(message);
        } else {
            setStatusDone(message);
        }
    }

    /**
     * 상태바 메시지를 5초 후 "✓ Ready"로 자동 복원한다 (토스트 효과).
     * 중간에 다른 메시지가 설정되면 이전 타이머는 무효화된다.
     */
    private int statusFadeGeneration = 0;

    private void scheduleStatusFade() {
        final int gen = ++statusFadeGeneration;
        javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(5));
        pause.setOnFinished(e -> {
            if (gen == statusFadeGeneration) {
                statusLabel.setText("✓ Ready");
            }
        });
        pause.play();
    }

    private void updateStatusBar(Repository repo) {
        if (repo == null) {
            branchStatusLabel.setText("");
            changesCountLabel.setText("");
            return;
        }
        branchStatusLabel.setText(repo.getCurrentBranch());

        // unstaged + staged 별도 표시 (목록 잘림 시 totalUnstagedCount/totalStagedCount 사용)
        int unstaged = totalUnstagedCount > 0 ? totalUnstagedCount : (unstagedListView.getItems() != null ? unstagedListView.getItems().size() : 0);
        int staged = totalStagedCount > 0 ? totalStagedCount : (stagedListView.getItems() != null ? stagedListView.getItems().size() : 0);
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
        if (!repo.isHasRemote()) {
            aheadBehindLabel.setText("(원격 없음)");
            return;
        }
        int ahead = repo.getAhead();
        int behind = repo.getBehind();
        StringBuilder sb = new StringBuilder();
        if (ahead > 0) sb.append("↑").append(ahead);
        if (ahead > 0 && behind > 0) sb.append(" ");
        if (behind > 0) sb.append("↓").append(behind);
        aheadBehindLabel.setText(sb.toString());
    }

    /** Unstaged 목록을 검색어로 필터링해 ListView에 반영. */
    private void applyFilterUnstaged() {
        String q = unstagedSearchField != null ? unstagedSearchField.getText() : null;
        if (q == null || q.isBlank()) {
            unstagedListView.setItems(unstagedFullList);
            return;
        }
        String lower = q.trim().toLowerCase();
        List<FileChange> filtered = unstagedFullList.stream()
                .filter(f -> f.path().toLowerCase().contains(lower))
                .toList();
        unstagedListView.setItems(FXCollections.observableArrayList(filtered));
    }

    /** Staged 목록을 검색어로 필터링해 ListView에 반영. */
    private void applyFilterStaged() {
        String q = stagedSearchField != null ? stagedSearchField.getText() : null;
        if (q == null || q.isBlank()) {
            stagedListView.setItems(stagedFullList);
            return;
        }
        String lower = q.trim().toLowerCase();
        List<FileChange> filtered = stagedFullList.stream()
                .filter(f -> f.path().toLowerCase().contains(lower))
                .toList();
        stagedListView.setItems(FXCollections.observableArrayList(filtered));
    }

    /**
     * 메인 창을 소유자로 하여 에러 다이얼로그를 띄운다. 메인 창 중앙에 표시된다.
     */
    private void showErrorAlert(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        javafx.stage.Window owner = getMainWindow();
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.setTitle("오류");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /** 메인 창(Stage) 참조. 다이얼로그 중앙 배치용. */
    private javafx.stage.Window getMainWindow() {
        if (statusBar != null && statusBar.getScene() != null && statusBar.getScene().getWindow() != null) {
            return statusBar.getScene().getWindow();
        }
        if (sidebar != null && sidebar.getScene() != null && sidebar.getScene().getWindow() != null) {
            return sidebar.getScene().getWindow();
        }
        if (repoDetailPane != null && repoDetailPane.getScene() != null && repoDetailPane.getScene().getWindow() != null) {
            return repoDetailPane.getScene().getWindow();
        }
        return null;
    }

    /**
     * Push/Pull/Fetch 등 원격 작업 실패 시 git raw 메시지를 사용자 친화적인 한국어로 변환한다.
     */
    private static String mapRemoteErrorMessage(String rawMessage) {
        if (rawMessage == null) return "알 수 없는 오류";
        String msg = rawMessage.trim().toLowerCase();

        // ── 원격 저장소 자체가 없음 ──
        if (msg.contains("no configured push destination")
                || msg.contains("does not have any remotes")
                || msg.contains("no remote repository specified")) {
            return "이 레포에는 원격 저장소가 없습니다.\n\n터미널에서 'git remote add origin <URL>' 로 원격을 추가하세요.";
        }
        // ── upstream 브랜치 미설정 (원격은 있지만 이 브랜치에 추적 정보 없음) ──
        if (msg.contains("no upstream")
                || (msg.contains("upstream") && msg.contains("not set"))
                || msg.contains("no tracking information")
                || msg.contains("specify which branch you want to merge")
                || msg.contains("현재 브랜치에 위쪽 추적 브랜치가 없습니다")) {
            return "현재 브랜치에 원격 추적 정보가 없습니다.\n\n터미널에서 아래 명령으로 설정할 수 있습니다:\n  git push -u origin <브랜치이름>";
        }

        // ── 네트워크 ──
        if (msg.contains("could not resolve host") || msg.contains("unknown host") || msg.contains("name or service not known")) {
            return "네트워크 연결을 확인하세요.\n(호스트를 찾을 수 없습니다)";
        }
        if (msg.contains("connection refused") || msg.contains("timed out") || msg.contains("connection timed out")) {
            return "네트워크 연결을 확인하세요.\n(연결이 거부되었거나 시간이 초과되었습니다)";
        }

        // ── 인증 ──
        if (msg.contains("authentication failed") || msg.contains("permission denied") || msg.contains("access denied")) {
            return "인증에 실패했습니다.\n자격 증명(비밀번호·토큰)을 확인하세요.";
        }

        // ── 충돌 / non-fast-forward ──
        if (msg.contains("rejected") && msg.contains("non-fast-forward")) {
            return "원격에 새 커밋이 있습니다.\n먼저 Pull 한 뒤 다시 Push 하세요.";
        }

        // ── 너무 긴 메시지 축약 ──
        if (rawMessage.length() > 300) {
            return rawMessage.substring(0, 300) + "\n\n... (메시지 축약됨)";
        }
        return rawMessage;
    }

    /**
     * 브랜치 전환 실패 시 git raw 메시지를 사용자 친화적인 한국어로 변환한다.
     */
    private static String mapBranchErrorMessage(String rawMessage) {
        if (rawMessage == null) return "알 수 없는 오류";
        String msg = rawMessage.trim().toLowerCase();
        if (msg.contains("your local changes") || msg.contains("would be overwritten")
                || msg.contains("conflict") || msg.contains("uncommitted changes")) {
            return "커밋하지 않은 변경이 있어 브랜치를 전환할 수 없습니다.\n\n먼저 변경 사항을 커밋하거나 Stash하세요.";
        }
        if (msg.contains("pathspec") && msg.contains("did not match")) {
            return "해당 브랜치를 찾을 수 없습니다.";
        }
        // 너무 긴 에러 메시지 잘라내기 (git이 변경 파일 목록을 쭉 붙일 때)
        if (rawMessage.length() > 300) {
            return rawMessage.substring(0, 300) + "\n\n... (메시지 축약됨)";
        }
        return rawMessage;
    }

    // ========== 브랜치 액션 ==========

    @FXML
    private void onNewBranch() {
        if (selectedRepo == null) return;
        log.info("새 브랜치 생성 버튼 클릭");

        TextInputDialog dialog = new TextInputDialog();
        javafx.stage.Window owner = getMainWindow();
        if (owner != null) dialog.initOwner(owner);
        dialog.setTitle("새 브랜치 생성");
        dialog.setHeaderText("브랜치 이름을 입력하세요");
        dialog.setContentText("이름:");

        dialog.showAndWait().ifPresent(name -> {
            if (name.isBlank()) return;

            TaskManager taskManager = GitMiniApp.getTaskManager();
            GitService gitService = GitMiniApp.getGitService();
            if (taskManager == null || gitService == null) return;

            Repository targetRepo = selectedRepo;
            Path repoPath = Path.of(targetRepo.getPath());
            setStatus("브랜치 생성 중...");
            taskManager.run(
                    () -> { gitService.createBranch(repoPath, name.trim()); return null; },
                    result -> {
                        refreshRepoDetailWithStatus(targetRepo, "✓ 브랜치 생성 완료: " + name.trim());
                        log.info("브랜치 생성 완료: {}", name.trim());
                    },
                    error -> {
                        log.error("브랜치 생성 실패: {}", name, error);
                        showErrorAlert("브랜치 생성 실패", error.getMessage());
                        setStatus("브랜치 생성 실패");
                    }
            );
        });
    }

    /**
     * 브랜치 ComboBox 변경 시 호출된다. initialize()에서 리스너로 등록.
     */
    private void onBranchChanged(String newBranch) {
        if (updatingBranchComboBox) return; // 프로그래밍 갱신 중에는 무시
        if (selectedRepo == null || newBranch == null || newBranch.isEmpty()) return;
        // 현재 브랜치와 같으면 무시
        if (newBranch.equals(selectedRepo.getCurrentBranch())) return;

        log.info("브랜치 전환: {} → {}", selectedRepo.getCurrentBranch(), newBranch);

        TaskManager taskManager = GitMiniApp.getTaskManager();
        GitService gitService = GitMiniApp.getGitService();
        if (taskManager == null || gitService == null) return;

        Repository targetRepo = selectedRepo;
        Path repoPath = Path.of(targetRepo.getPath());
        setStatus("브랜치 전환 중...");
        taskManager.run(
                () -> { gitService.checkout(repoPath, newBranch); return null; },
                result -> {
                    refreshRepoDetailWithStatus(targetRepo, "✓ 브랜치 전환: " + newBranch);
                    log.info("브랜치 전환 완료: {}", newBranch);
                },
                error -> {
                    log.error("브랜치 전환 실패: {}", newBranch, error);
                    showErrorAlert("브랜치 전환 실패", mapBranchErrorMessage(error.getMessage()));
                    // 실패 시 이전 브랜치로 복원 (onBranchChanged 재트리거 방지)
                    updatingBranchComboBox = true;
                    try {
                        branchComboBox.setValue(targetRepo.getCurrentBranch());
                    } finally {
                        updatingBranchComboBox = false;
                    }
                    setStatus("브랜치 전환 실패");
                }
        );
    }

    // ========== 원격 액션 ==========

    /** 네트워크 작업 중 원격 버튼들을 비활성화/활성화. */
    private void setRemoteButtonsDisable(boolean disable) {
        fetchBtn.setDisable(disable);
        pullBtn.setDisable(disable);
        pushBtn.setDisable(disable);
    }

    @FXML
    private void onFetch() {
        if (selectedRepo == null) return;
        log.info("Fetch 클릭");

        TaskManager taskManager = GitMiniApp.getTaskManager();
        GitService gitService = GitMiniApp.getGitService();
        if (taskManager == null || gitService == null) return;

        Repository targetRepo = selectedRepo;
        Path repoPath = Path.of(targetRepo.getPath());
        setStatus("Fetch 중...");
        setRemoteButtonsDisable(true);
        taskManager.run(
                () -> { gitService.fetch(repoPath); return null; },
                result -> {
                    setRemoteButtonsDisable(false);
                    refreshRepoDetailWithStatus(targetRepo, "✓ Fetch 완료");
                },
                error -> {
                    setRemoteButtonsDisable(false);
                    log.error("Fetch 실패", error);
                    showErrorAlert("Fetch 실패", mapRemoteErrorMessage(error.getMessage()));
                    setStatus("Fetch 실패");
                }
        );
    }

    @FXML
    private void onPull() {
        if (selectedRepo == null) return;
        log.info("Pull 클릭");

        TaskManager taskManager = GitMiniApp.getTaskManager();
        GitService gitService = GitMiniApp.getGitService();
        if (taskManager == null || gitService == null) return;

        Repository targetRepo = selectedRepo;
        Path repoPath = Path.of(targetRepo.getPath());
        setStatus("Pull 중...");
        setRemoteButtonsDisable(true);
        taskManager.run(
                () -> { gitService.pull(repoPath); return null; },
                result -> {
                    setRemoteButtonsDisable(false);
                    refreshRepoDetailWithStatus(targetRepo, "✓ Pull 완료");
                },
                error -> {
                    setRemoteButtonsDisable(false);
                    log.error("Pull 실패", error);
                    showErrorAlert("Pull 실패", mapRemoteErrorMessage(error.getMessage()));
                    setStatus("Pull 실패");
                }
        );
    }

    @FXML
    private void onPush() {
        if (selectedRepo == null) return;
        log.info("Push 클릭");

        TaskManager taskManager = GitMiniApp.getTaskManager();
        GitService gitService = GitMiniApp.getGitService();
        if (taskManager == null || gitService == null) return;

        Repository targetRepo = selectedRepo;
        Path repoPath = Path.of(targetRepo.getPath());
        setStatus("Push 중...");
        setRemoteButtonsDisable(true);
        taskManager.run(
                () -> { gitService.push(repoPath); return null; },
                result -> {
                    setRemoteButtonsDisable(false);
                    refreshRepoDetailWithStatus(targetRepo, "✓ Push 완료");
                },
                error -> {
                    String errMsg = error.getMessage() != null ? error.getMessage().toLowerCase() : "";
                    // upstream 미설정 시 자동으로 push -u origin <브랜치> 재시도
                    if (errMsg.contains("no upstream") || errMsg.contains("has no upstream branch")) {
                        log.info("upstream 미설정 → push -u origin {} 자동 실행", targetRepo.getCurrentBranch());
                        setStatus("upstream 설정 중...");
                        taskManager.run(
                                () -> { gitService.pushSetUpstream(repoPath, targetRepo.getCurrentBranch()); return null; },
                                result2 -> {
                                    setRemoteButtonsDisable(false);
                                    refreshRepoDetailWithStatus(targetRepo, "✓ Push 완료 (upstream 자동 설정)");
                                },
                                error2 -> {
                                    setRemoteButtonsDisable(false);
                                    log.error("Push -u 실패", error2);
                                    showErrorAlert("Push 실패", mapRemoteErrorMessage(error2.getMessage()));
                                    setStatus("Push 실패");
                                }
                        );
                    } else {
                        setRemoteButtonsDisable(false);
                        log.error("Push 실패", error);
                        showErrorAlert("Push 실패", mapRemoteErrorMessage(error.getMessage()));
                        setStatus("Push 실패");
                    }
                }
        );
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
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.P, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                this::onPush
        );
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                this::onPull
        );
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                this::onFetch
        );
        log.debug("키보드 단축키 등록: Ctrl+Enter(Commit), Ctrl+Shift+P(Push), Ctrl+Shift+L(Pull), Ctrl+Shift+F(Fetch)");
    }

    // ========== 컨텍스트 메뉴 액션 ==========

    /** Unstaged 파일의 변경을 취소한다 (git checkout -- file). 확인 다이얼로그 포함. */
    private void discardSelectedFile() {
        FileChange selected = unstagedListView.getSelectionModel().getSelectedItem();
        if (selected == null || selectedRepo == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        javafx.stage.Window owner = getMainWindow();
        if (owner != null) confirm.initOwner(owner);
        confirm.setTitle("변경 취소");
        confirm.setHeaderText(selected.path());
        confirm.setContentText("이 파일의 변경 사항을 되돌리시겠습니까?\n이 작업은 되돌릴 수 없습니다.");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                TaskManager taskManager = GitMiniApp.getTaskManager();
                GitService gitService = GitMiniApp.getGitService();
                if (taskManager == null || gitService == null) return;

                Repository targetRepo = selectedRepo;
                Path repoPath = Path.of(targetRepo.getPath());
                setStatus("변경 취소 중...");
                taskManager.run(
                        () -> { gitService.discard(repoPath, List.of(selected.path())); return null; },
                        result -> {
                            refreshRepoDetailWithStatus(targetRepo, "✓ 변경 취소: " + selected.path());
                            log.info("변경 취소 완료: {}", selected.path());
                        },
                        error -> {
                            log.error("변경 취소 실패: {}", selected.path(), error);
                            showErrorAlert("변경 취소 실패", error.getMessage());
                            setStatus("변경 취소 실패");
                        }
                );
            }
        });
    }

    /** 선택된 파일의 경로를 클립보드에 복사한다. */
    private void copySelectedFilePath(boolean fromStaged) {
        FileChange selected = fromStaged
                ? stagedListView.getSelectionModel().getSelectedItem()
                : unstagedListView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        String fullPath = selectedRepo != null
                ? Path.of(selectedRepo.getPath(), selected.path()).toString()
                : selected.path();
        javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
        cc.putString(fullPath);
        javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
        setStatus("경로 복사됨: " + selected.path());
    }

    /** 선택된 파일을 OS 탐색기에서 연다 (파일 선택 상태로). */
    private void openSelectedFileInExplorer(boolean fromStaged) {
        FileChange selected = fromStaged
                ? stagedListView.getSelectionModel().getSelectedItem()
                : unstagedListView.getSelectionModel().getSelectedItem();
        if (selected == null || selectedRepo == null) return;

        try {
            Path filePath = Path.of(selectedRepo.getPath(), selected.path()).toAbsolutePath().normalize();
            if (java.nio.file.Files.exists(filePath)) {
                // Windows: explorer /select,<파일> → 해당 파일이 선택된 상태로 탐색기 열림
                Runtime.getRuntime().exec(new String[]{"explorer", "/select,", filePath.toString()});
            } else {
                // 파일이 삭제된 경우 등 → 부모 폴더 열기
                Path dir = filePath.getParent();
                if (dir != null && java.nio.file.Files.isDirectory(dir)) {
                    Runtime.getRuntime().exec(new String[]{"explorer", dir.toString()});
                } else {
                    Runtime.getRuntime().exec(new String[]{"explorer", selectedRepo.getPath()});
                }
            }
        } catch (Exception e) {
            log.error("탐색기 열기 실패: {}", selected.path(), e);
            setStatus("탐색기 열기 실패");
        }
    }
}
