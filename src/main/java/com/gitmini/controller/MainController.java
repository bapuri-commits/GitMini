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
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitmini.util.ErrorMessages;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

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

    /** 자동 Fetch 타이머. */
    private javafx.animation.Timeline autoFetchTimeline;

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
        repoListView.setCellFactory(listView -> new RepoListCell(
                this::removeRepo, this::openRepoInExplorer, this::openRepoInTerminal));

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
        MenuItem openInTerminalUnstaged = new MenuItem("터미널에서 열기");
        openInTerminalUnstaged.setOnAction(e -> openInTerminal());
        unstagedCtxMenu.getItems().addAll(stageCtxItem, discardItem, new SeparatorMenuItem(), copyPathUnstaged, openInExplorerUnstaged, openInTerminalUnstaged);
        unstagedListView.setContextMenu(unstagedCtxMenu);

        // 파일 변경 목록: 우클릭 컨텍스트 메뉴 (Staged)
        ContextMenu stagedCtxMenu = new ContextMenu();
        MenuItem unstageCtxItem = new MenuItem("Unstage");
        unstageCtxItem.setOnAction(e -> onUnstage());
        MenuItem copyPathStaged = new MenuItem("경로 복사");
        copyPathStaged.setOnAction(e -> copySelectedFilePath(true));
        MenuItem openInExplorerStaged = new MenuItem("탐색기에서 열기");
        openInExplorerStaged.setOnAction(e -> openSelectedFileInExplorer(true));
        MenuItem openInTerminalStaged = new MenuItem("터미널에서 열기");
        openInTerminalStaged.setOnAction(e -> openInTerminal());
        stagedCtxMenu.getItems().addAll(unstageCtxItem, new SeparatorMenuItem(), copyPathStaged, openInExplorerStaged, openInTerminalStaged);
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

        // 커밋 히스토리: 우클릭 메뉴 — 최근 커밋 취소 (첫 번째 항목만 가능)
        ContextMenu commitHistoryCtxMenu = new ContextMenu();
        MenuItem undoCommitItem = new MenuItem("최근 커밋 취소 (soft reset)");
        undoCommitItem.setOnAction(e -> onUndoLastCommit());
        commitHistoryCtxMenu.getItems().add(undoCommitItem);
        // 표시 전에 첫 번째 커밋만 허용
        commitHistoryCtxMenu.setOnShowing(e -> {
            int selectedIdx = commitHistoryListView.getSelectionModel().getSelectedIndex();
            undoCommitItem.setDisable(selectedIdx != 0);
        });
        commitHistoryListView.setContextMenu(commitHistoryCtxMenu);

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

        // 사이드바: 드래그 & 드롭으로 레포 추가
        sidebar.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(javafx.scene.input.TransferMode.LINK);
            }
            event.consume();
        });
        sidebar.setOnDragDropped(event -> {
            var db = event.getDragboard();
            if (db.hasFiles()) {
                RepositoryManager repoManager = GitMiniApp.getRepositoryManager();
                TaskManager taskManager = GitMiniApp.getTaskManager();
                if (repoManager != null && taskManager != null) {
                    // 디렉토리만 필터
                    List<File> dirs = db.getFiles().stream().filter(File::isDirectory).toList();
                    if (!dirs.isEmpty()) {
                        setStatus("레포 추가 중...");
                        final int total = dirs.size();
                        for (File dir : dirs) {
                            final String name = dir.getName();
                            taskManager.run(
                                    () -> { repoManager.add(dir.toPath()); return null; },
                                    r -> loadRepoListWithStatus("✓ 드롭으로 레포 추가: " + name),
                                    err -> {
                                        log.warn("드롭 레포 추가 실패: {}: {}", name, err.getMessage());
                                        showErrorAlert("레포 추가 실패", name + "\n\n" + ErrorMessages.mapGitError(err.getMessage()));
                                        setStatus("레포 추가 실패: " + name);
                                    }
                            );
                        }
                    }
                }
            }
            event.setDropCompleted(true);
            event.consume();
        });

        // 초기 상태
        showWelcome();

        // 키보드 단축키 등록 (Scene 생성 후 지연 실행)
        Platform.runLater(this::setupKeyboardShortcuts);

        // 자동 Fetch: 설정된 간격(분)으로 선택된 레포를 백그라운드 fetch
        startAutoFetch();

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
                    setStatus("레포 목록 로드 실패: " + ErrorMessages.mapGitError(error.getMessage()));
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
                    setStatus("상태 갱신 실패: " + ErrorMessages.mapGitError(error.getMessage()));
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
                            showErrorAlert("레포 추가 실패", name + ": " + ErrorMessages.mapGitError(error.getMessage()));
                            setStatus("레포 추가 실패");
                        }
                );
            }
        }
        if (addedCount == 0) {
            setStatus("레포 추가 취소");
        }
    }

    /**
     * 새 GitHub 레포지토리를 생성하는 다이얼로그를 연다.
     * 생성 후 자동 Clone을 제안한다.
     */
    @FXML
    private void onCreateRepo() {
        log.info("새 레포 생성 버튼 클릭");

        com.gitmini.service.GitHubService ghService = GitMiniApp.getGitHubService();
        if (ghService == null || !ghService.hasToken()) {
            showErrorAlert("GitHub 토큰이 필요합니다",
                    "레포를 생성하려면 GitHub Personal Access Token이 필요합니다.\n\n"
                    + "사이드바 상단 ⚙ 버튼 → 설정 → GitHub 섹션에서 토큰을 등록하세요.");
            return;
        }

        javafx.scene.control.Dialog<Void> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("새 GitHub 레포지토리");
        dialog.setHeaderText("GitHub에 새 레포지토리를 생성합니다");
        javafx.stage.Window owner = getMainWindow();
        if (owner != null) dialog.initOwner(owner);

        // 다이얼로그 레이아웃
        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(10);
        content.setPrefWidth(420);
        content.setPadding(new javafx.geometry.Insets(12, 0, 0, 0));

        TextField nameField = new TextField();
        nameField.setPromptText("my-project");

        TextField descField = new TextField();
        descField.setPromptText("(선택) 레포 설명");

        CheckBox privateCheck = new CheckBox("Private");
        privateCheck.setSelected(false);

        CheckBox autoInitCheck = new CheckBox("README.md로 초기화");
        autoInitCheck.setSelected(true);

        Label statusLbl = new Label("");
        statusLbl.setStyle("-fx-font-size: 12px;");

        ProgressIndicator createProgress = new ProgressIndicator();
        createProgress.setPrefSize(18, 18);
        createProgress.setVisible(false);
        createProgress.managedProperty().bind(createProgress.visibleProperty());

        javafx.scene.layout.HBox statusRow = new javafx.scene.layout.HBox(8);
        statusRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        statusRow.getChildren().addAll(createProgress, statusLbl);

        javafx.scene.layout.HBox optionsRow = new javafx.scene.layout.HBox(16);
        optionsRow.getChildren().addAll(privateCheck, autoInitCheck);

        content.getChildren().addAll(
                new Label("레포지토리 이름:"), nameField,
                new Label("설명:"), descField,
                optionsRow,
                statusRow
        );

        dialog.getDialogPane().setContent(content);

        // 버튼
        javafx.scene.control.ButtonType createType =
                new javafx.scene.control.ButtonType("생성", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createType, javafx.scene.control.ButtonType.CANCEL);

        Button createBtn = (Button) dialog.getDialogPane().lookupButton(createType);
        createBtn.setDisable(true);
        nameField.textProperty().addListener((obs, o, n) ->
                createBtn.setDisable(n == null || n.trim().isEmpty()));

        // 생성 클릭
        createBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();

            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                statusLbl.setText("이름을 입력하세요");
                statusLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: -color-danger-fg;");
                return;
            }

            nameField.setDisable(true);
            descField.setDisable(true);
            privateCheck.setDisable(true);
            autoInitCheck.setDisable(true);
            createBtn.setDisable(true);
            createProgress.setVisible(true);
            statusLbl.setText("생성 중...");
            statusLbl.setStyle("-fx-font-size: 12px;");

            TaskManager taskManager = GitMiniApp.getTaskManager();
            if (taskManager == null) return;

            String desc = descField.getText();
            boolean isPrivate = privateCheck.isSelected();
            boolean autoInit = autoInitCheck.isSelected();

            taskManager.run(
                    () -> ghService.createRepository(name, desc, isPrivate, autoInit),
                    repo -> {
                        createProgress.setVisible(false);
                        statusLbl.setText("✓ 생성 완료: " + repo.fullName());
                        statusLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: -color-success-fg;");
                        log.info("레포 생성 완료: {}", repo.fullName());

                        // Clone 제안
                        Alert cloneConfirm = new Alert(Alert.AlertType.CONFIRMATION);
                        cloneConfirm.initOwner(dialog.getDialogPane().getScene().getWindow());
                        cloneConfirm.setTitle("Clone");
                        cloneConfirm.setHeaderText(repo.fullName() + " 생성 완료");
                        cloneConfirm.setContentText("이 레포를 바로 Clone하시겠습니까?");

                        cloneConfirm.showAndWait().ifPresent(response -> {
                            dialog.setResult(null);
                            dialog.close();
                            if (response == ButtonType.OK) {
                                // Clone 다이얼로그를 열면서 URL 자동 채움은 복잡하므로,
                                // 직접 Clone 실행
                                autoCloneNewRepo(repo);
                            }
                        });

                        // Clone 안 하면 다이얼로그만 닫기
                        if (dialog.isShowing()) {
                            dialog.setResult(null);
                            dialog.close();
                        }
                    },
                    error -> {
                        createProgress.setVisible(false);
                        nameField.setDisable(false);
                        descField.setDisable(false);
                        privateCheck.setDisable(false);
                        autoInitCheck.setDisable(false);
                        createBtn.setDisable(false);

                        String errMsg = error.getMessage();
                        if (errMsg != null && errMsg.length() > 80) {
                            errMsg = errMsg.substring(0, 80) + "...";
                        }
                        statusLbl.setText("✗ " + errMsg);
                        statusLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: -color-danger-fg;");
                        log.error("레포 생성 실패: {}", error.getMessage());
                    }
            );
        });

        dialog.getDialogPane().getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/css/app.css"),
                        "app.css를 찾을 수 없습니다").toExternalForm());
        dialog.showAndWait();
    }

    /**
     * 방금 생성한 레포를 기본 Clone 경로에 자동으로 Clone한다.
     */
    private void autoCloneNewRepo(com.gitmini.model.GitHubRepo repo) {
        com.gitmini.config.ConfigManager cm = GitMiniApp.getConfigManager();
        String basePath = cm != null ? cm.load().getDefaultClonePath() : "";

        if (basePath.isEmpty()) {
            // Clone 경로 미설정 → 사용자에게 선택하게 함
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Clone 경로 선택");
            javafx.stage.Window win = getMainWindow();
            File selected = chooser.showDialog(win);
            if (selected == null) {
                setStatus("Clone 취소");
                return;
            }
            basePath = selected.getAbsolutePath();
        }

        String repoName = extractRepoName(repo.cloneUrl());
        Path targetDir = Path.of(basePath, repoName);

        if (java.nio.file.Files.exists(targetDir)) {
            showErrorAlert("Clone 실패", "이미 존재하는 폴더: " + targetDir);
            return;
        }

        // PAT 삽입
        com.gitmini.service.GitHubService ghService = GitMiniApp.getGitHubService();
        String cloneUrl = repo.cloneUrl();
        if (ghService != null) {
            java.util.Optional<String> token = ghService.getToken();
            if (token.isPresent()) {
                cloneUrl = GitService.injectTokenIntoUrl(repo.cloneUrl(), token.get());
            }
        }

        TaskManager taskManager = GitMiniApp.getTaskManager();
        GitService gitService = GitMiniApp.getGitService();
        if (taskManager == null || gitService == null) return;

        final String finalUrl = cloneUrl;
        final Path finalTarget = targetDir;

        setStatus("Clone 중...");
        taskManager.run(
                () -> { gitService.cloneRepo(finalUrl, finalTarget); return finalTarget; },
                result -> {
                    RepositoryManager repoManager = GitMiniApp.getRepositoryManager();
                    if (repoManager != null) {
                        taskManager.run(
                                () -> { repoManager.add(finalTarget); return null; },
                                r -> loadRepoListWithStatus("✓ 레포 생성 + Clone 완료: " + repoName),
                                err -> setStatus("Clone 완료, 레포 등록 실패")
                        );
                    }
                },
                error -> {
                    log.error("자동 Clone 실패: {}", error.getMessage());
                    showErrorAlert("Clone 실패", ErrorMessages.mapRemoteError(error.getMessage()));
                    setStatus("Clone 실패");
                }
        );
    }

    @FXML
    private void onCloneRepo() {
        log.info("Clone 버튼 클릭");

        // Clone 다이얼로그 구성 (URL + 경로 + Clone 버튼)
        javafx.scene.control.Dialog<Void> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("레포지토리 Clone");
        dialog.setHeaderText("GitHub 레포지토리를 로컬에 Clone합니다");
        javafx.stage.Window owner = getMainWindow();
        if (owner != null) dialog.initOwner(owner);

        // 다이얼로그 내부 레이아웃
        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(12);
        content.setPrefWidth(480);
        content.setPadding(new javafx.geometry.Insets(12, 0, 0, 0));

        TextField urlField = new TextField();
        urlField.setPromptText("https://github.com/owner/repo.git");

        // 기본 Clone 경로 로드
        com.gitmini.config.ConfigManager cm = GitMiniApp.getConfigManager();
        String defaultPath = cm != null ? cm.load().getDefaultClonePath() : "";

        TextField pathField = new TextField(defaultPath);
        pathField.setPromptText("Clone 대상 폴더");

        Button browseBtn = new Button("...");
        browseBtn.setPrefWidth(36);
        browseBtn.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Clone 대상 폴더 선택");
            String current = pathField.getText();
            if (current != null && !current.isBlank()) {
                File dir = new File(current);
                if (dir.isDirectory()) chooser.setInitialDirectory(dir);
            }
            File selected = chooser.showDialog(dialog.getDialogPane().getScene().getWindow());
            if (selected != null) pathField.setText(selected.getAbsolutePath());
        });

        javafx.scene.layout.HBox pathRow = new javafx.scene.layout.HBox(8);
        pathRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        pathField.setMaxWidth(Double.MAX_VALUE);
        javafx.scene.layout.HBox.setHgrow(pathField, javafx.scene.layout.Priority.ALWAYS);
        pathRow.getChildren().addAll(pathField, browseBtn);

        Label statusLbl = new Label("");
        statusLbl.setStyle("-fx-font-size: 12px;");

        ProgressIndicator cloneProgress = new ProgressIndicator();
        cloneProgress.setPrefSize(18, 18);
        cloneProgress.setVisible(false);
        cloneProgress.managedProperty().bind(cloneProgress.visibleProperty());

        javafx.scene.layout.HBox statusRow = new javafx.scene.layout.HBox(8);
        statusRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        statusRow.getChildren().addAll(cloneProgress, statusLbl);

        content.getChildren().addAll(
                new Label("레포지토리 URL:"), urlField,
                new Label("Clone 경로:"), pathRow,
                statusRow
        );

        dialog.getDialogPane().setContent(content);

        // 버튼: Clone + 취소
        javafx.scene.control.ButtonType cloneButtonType =
                new javafx.scene.control.ButtonType("Clone", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(cloneButtonType, javafx.scene.control.ButtonType.CANCEL);

        // Clone 버튼 참조
        Button cloneBtn = (Button) dialog.getDialogPane().lookupButton(cloneButtonType);
        // URL 비어 있으면 Clone 버튼 비활성화
        // 바인딩 대신 리스너 사용 (이후 setDisable 직접 호출과 충돌 방지)
        cloneBtn.setDisable(true);
        urlField.textProperty().addListener((obs, o, n) ->
                cloneBtn.setDisable(n == null || n.trim().isEmpty()));

        // Clone 버튼 클릭 시 직접 처리 (다이얼로그 자동 닫힘 방지)
        cloneBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume(); // 다이얼로그 자동 닫힘 방지

            String url = urlField.getText().trim();
            String basePath = pathField.getText().trim();

            if (url.isEmpty()) {
                statusLbl.setText("URL을 입력하세요");
                statusLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: -color-danger-fg;");
                return;
            }
            if (basePath.isEmpty()) {
                statusLbl.setText("Clone 경로를 선택하세요");
                statusLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: -color-danger-fg;");
                return;
            }

            // URL에서 레포 이름 추출 → 대상 경로 결정
            String repoName = extractRepoName(url);
            Path targetDir = Path.of(basePath, repoName);

            if (java.nio.file.Files.exists(targetDir)) {
                statusLbl.setText("이미 존재하는 폴더: " + repoName);
                statusLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: -color-danger-fg;");
                return;
            }

            // PAT가 있으면 HTTPS URL에 삽입
            com.gitmini.service.GitHubService ghService = GitMiniApp.getGitHubService();
            String cloneUrl = url;
            if (ghService != null) {
                java.util.Optional<String> token = ghService.getToken();
                if (token.isPresent()) {
                    cloneUrl = GitService.injectTokenIntoUrl(url, token.get());
                }
            }

            // UI 잠금
            urlField.setDisable(true);
            pathField.setDisable(true);
            browseBtn.setDisable(true);
            cloneBtn.setDisable(true);
            cloneProgress.setVisible(true);
            statusLbl.setText("Clone 중...");
            statusLbl.setStyle("-fx-font-size: 12px;");

            TaskManager taskManager = GitMiniApp.getTaskManager();
            GitService gitService = GitMiniApp.getGitService();
            if (taskManager == null || gitService == null) return;

            final String finalCloneUrl = cloneUrl;
            final Path finalTargetDir = targetDir;

            taskManager.run(
                    () -> { gitService.cloneRepo(finalCloneUrl, finalTargetDir); return finalTargetDir; },
                    result -> {
                        cloneProgress.setVisible(false);
                        statusLbl.setText("✓ Clone 완료: " + repoName);
                        statusLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: -color-success-fg;");
                        log.info("Clone 완료: {}", finalTargetDir);

                        // 레포 목록에 추가
                        RepositoryManager repoManager = GitMiniApp.getRepositoryManager();
                        if (repoManager != null) {
                            taskManager.run(
                                    () -> { repoManager.add(finalTargetDir); return null; },
                                    r -> {
                                        loadRepoListWithStatus("✓ Clone + 레포 추가 완료: " + repoName);
                                        // 다이얼로그 닫기
                                        dialog.setResult(null);
                                        dialog.close();
                                    },
                                    err -> {
                                        log.warn("Clone 후 레포 추가 실패: {}", err.getMessage());
                                        statusLbl.setText("Clone 완료, 레포 등록 실패: " + err.getMessage());
                                        statusLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: -color-warning-fg;");
                                        // 버튼 복원
                                        urlField.setDisable(false);
                                        pathField.setDisable(false);
                                        browseBtn.setDisable(false);
                                        cloneBtn.setDisable(false);
                                    }
                            );
                        }
                    },
                    error -> {
                        cloneProgress.setVisible(false);
                        urlField.setDisable(false);
                        pathField.setDisable(false);
                        browseBtn.setDisable(false);
                        cloneBtn.setDisable(false);

                        String errMsg = ErrorMessages.mapRemoteError(error.getMessage());
                        statusLbl.setText("✗ " + errMsg);
                        statusLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: -color-danger-fg;");
                        log.error("Clone 실패: {}", error.getMessage());
                    }
            );
        });

        // 다이얼로그 CSS 적용
        dialog.getDialogPane().getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/css/app.css"),
                        "app.css를 찾을 수 없습니다").toExternalForm());

        dialog.showAndWait();
    }

    /**
     * Clone URL에서 레포지토리 이름을 추출한다.
     * <p>
     * 예: "https://github.com/owner/repo.git" → "repo"
     * 예: "https://github.com/owner/repo" → "repo"
     * 예: "git@github.com:owner/repo.git" → "repo"
     * </p>
     */
    private static String extractRepoName(String url) {
        if (url == null || url.isBlank()) return "repo";

        // 끝의 .git 제거
        String cleaned = url.endsWith(".git") ? url.substring(0, url.length() - 4) : url;
        // 끝의 / 제거
        if (cleaned.endsWith("/")) cleaned = cleaned.substring(0, cleaned.length() - 1);
        // 마지막 / 뒤의 이름
        int lastSlash = cleaned.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < cleaned.length() - 1) {
            return cleaned.substring(lastSlash + 1);
        }
        // SSH 형식: git@github.com:owner/repo
        int lastColon = cleaned.lastIndexOf(':');
        if (lastColon >= 0) {
            String afterColon = cleaned.substring(lastColon + 1);
            int slash = afterColon.lastIndexOf('/');
            if (slash >= 0) return afterColon.substring(slash + 1);
            return afterColon;
        }
        return "repo";
    }

    /**
     * 설정 다이얼로그를 모달로 연다.
     * 저장 후 자동 Fetch 타이머를 갱신한다.
     */
    @FXML
    private void onSettings() {
        log.info("설정 버튼 클릭");
        try {
            FXMLLoader loader = new FXMLLoader(
                    Objects.requireNonNull(getClass().getResource("/fxml/settings.fxml"),
                            "settings.fxml을 찾을 수 없습니다"));
            Parent root = loader.load();
            SettingsController settingsController = loader.getController();

            Stage dialog = new Stage();
            dialog.setTitle("설정");
            dialog.initModality(Modality.APPLICATION_MODAL);
            javafx.stage.Window owner = getMainWindow();
            if (owner != null) dialog.initOwner(owner);

            Scene scene = new Scene(root);
            // 메인 앱과 동일한 CSS 적용
            scene.getStylesheets().add(
                    Objects.requireNonNull(getClass().getResource("/css/app.css"),
                            "app.css를 찾을 수 없습니다").toExternalForm());
            dialog.setScene(scene);
            dialog.setResizable(false);

            // 메인 창 중앙에 배치 (Windows에서 initOwner만으로는 중앙 배치 안 됨)
            if (owner != null) {
                dialog.setOnShown(e -> {
                    dialog.setX(owner.getX() + (owner.getWidth() - dialog.getWidth()) / 2);
                    dialog.setY(owner.getY() + (owner.getHeight() - dialog.getHeight()) / 2);
                });
            }

            dialog.showAndWait();

            // 저장된 경우 자동 Fetch 타이머 갱신 + 테마 즉시 적용
            if (settingsController.isSaved()) {
                restartAutoFetch();

                // 테마 즉시 적용
                com.gitmini.config.ConfigManager cm = GitMiniApp.getConfigManager();
                if (cm != null) {
                    String theme = cm.load().getTheme();
                    GitMiniApp.applyTheme(theme);
                }

                setStatus("✓ 설정 저장 완료");
            }
        } catch (Exception e) {
            log.error("설정 다이얼로그 열기 실패", e);
            showErrorAlert("설정 열기 실패", ErrorMessages.mapGitError(e.getMessage()));
        }
    }

    /**
     * 자동 Fetch 타이머를 중지하고 현재 설정값으로 재시작한다.
     * 설정 저장 후 호출된다.
     */
    private void restartAutoFetch() {
        if (autoFetchTimeline != null) {
            autoFetchTimeline.stop();
        }
        startAutoFetch();
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

    // 에러 메시지 매핑은 ErrorMessages 유틸리티 클래스로 이관됨.
    // → ErrorMessages.mapRemoteError(), ErrorMessages.mapBranchError(), ErrorMessages.mapGitError()

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
                        showErrorAlert("브랜치 생성 실패", ErrorMessages.mapGitError(error.getMessage()));
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
                    showErrorAlert("브랜치 전환 실패", ErrorMessages.mapBranchError(error.getMessage()));
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
                    showErrorAlert("Fetch 실패", ErrorMessages.mapRemoteError(error.getMessage()));
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
                    showErrorAlert("Pull 실패", ErrorMessages.mapRemoteError(error.getMessage()));
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
                                    showErrorAlert("Push 실패", ErrorMessages.mapRemoteError(error2.getMessage()));
                                    setStatus("Push 실패");
                                }
                        );
                    } else {
                        setRemoteButtonsDisable(false);
                        log.error("Push 실패", error);
                        showErrorAlert("Push 실패", ErrorMessages.mapRemoteError(error.getMessage()));
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
                    setStatus("Stage 실패: " + ErrorMessages.mapGitError(error.getMessage()));
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
                    setStatus("Stage 실패: " + ErrorMessages.mapGitError(error.getMessage()));
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
                    setStatus("Unstage 실패: " + ErrorMessages.mapGitError(error.getMessage()));
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
                    setStatus("Unstage 실패: " + ErrorMessages.mapGitError(error.getMessage()));
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
                    showErrorAlert("커밋 실패", ErrorMessages.mapGitError(error.getMessage()));
                    setStatus("커밋 실패");
                }
        );
    }

    // ========== Undo 최근 커밋 ==========

    /**
     * 최근 커밋을 취소한다 (git reset --soft HEAD~1).
     * 확인 다이얼로그 후 실행. 변경 사항은 스테이징 상태로 유지된다.
     */
    private void onUndoLastCommit() {
        if (selectedRepo == null) return;

        CommitInfo latestCommit = commitHistoryListView.getItems().isEmpty()
                ? null : commitHistoryListView.getItems().get(0);

        String commitDesc = latestCommit != null
                ? latestCommit.hash().substring(0, Math.min(7, latestCommit.hash().length()))
                  + " " + latestCommit.message()
                : "(최근 커밋)";

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        javafx.stage.Window owner = getMainWindow();
        if (owner != null) confirm.initOwner(owner);
        confirm.setTitle("최근 커밋 취소");
        confirm.setHeaderText("최근 커밋을 취소하시겠습니까?");
        confirm.setContentText(commitDesc
                + "\n\n이 작업은 커밋만 취소하고, 변경 사항은 스테이징 상태로 유지합니다."
                + "\n(git reset --soft HEAD~1)");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                TaskManager taskManager = GitMiniApp.getTaskManager();
                GitService gitService = GitMiniApp.getGitService();
                if (taskManager == null || gitService == null) return;

                Repository targetRepo = selectedRepo;
                Path repoPath = Path.of(targetRepo.getPath());
                setStatus("커밋 취소 중...");
                taskManager.run(
                        () -> { gitService.resetSoft(repoPath); return null; },
                        result -> {
                            refreshRepoDetailWithStatus(targetRepo, "✓ 최근 커밋 취소 완료");
                            log.info("최근 커밋 취소 완료: {}", targetRepo.getName());
                        },
                        error -> {
                            log.error("커밋 취소 실패: {}", targetRepo.getName(), error);
                            showErrorAlert("커밋 취소 실패", ErrorMessages.mapGitError(error.getMessage()));
                            setStatus("커밋 취소 실패");
                        }
                );
            }
        });
    }

    // ========== 키보드 단축키 ==========

    // ========== 자동 Fetch ==========

    /** 리소스 정리 (앱 종료 시 호출). */
    public void dispose() {
        if (autoFetchTimeline != null) {
            autoFetchTimeline.stop();
            log.debug("자동 Fetch 타이머 정지");
        }
        if (commandLogEventHandler != null) {
            com.gitmini.event.EventBus.getInstance().unsubscribe(GitCommandRecord.class, commandLogEventHandler);
        }
    }

    /** 설정된 간격(분)으로 선택된 레포를 백그라운드 fetch한다. 원격 없는 레포는 스킵. */
    private void startAutoFetch() {
        com.gitmini.config.ConfigManager configManager = GitMiniApp.getConfigManager();
        int intervalMin = 5;
        if (configManager != null) {
            intervalMin = configManager.load().getAutoFetchIntervalMinutes();
        }
        if (intervalMin <= 0) intervalMin = 5;

        autoFetchTimeline = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.minutes(intervalMin), e -> doAutoFetch())
        );
        autoFetchTimeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
        autoFetchTimeline.play();
        log.info("자동 Fetch 시작: {}분 간격", intervalMin);
    }

    private void doAutoFetch() {
        if (selectedRepo == null || !selectedRepo.isHasRemote()) {
            log.debug("자동 Fetch 스킵: 레포 미선택 또는 원격 없음");
            return;
        }

        TaskManager taskManager = GitMiniApp.getTaskManager();
        GitService gitService = GitMiniApp.getGitService();
        if (taskManager == null || gitService == null) return;

        Repository targetRepo = selectedRepo;
        Path repoPath = Path.of(targetRepo.getPath());

        // 추가 방어: 실제로 원격이 있는지 실시간 확인
        taskManager.run(
                () -> {
                    if (!gitService.hasRemote(repoPath)) return null; // 원격 없으면 스킵
                    gitService.fetch(repoPath);
                    return "fetched";
                },
                result -> {
                    if (result != null) {
                        refreshRepoDetailWithStatus(targetRepo, "✓ 자동 Fetch 완료");
                        log.debug("자동 Fetch 완료: {}", targetRepo.getName());
                    }
                },
                error -> log.debug("자동 Fetch 실패 (무시): {}", error.getMessage())
        );
    }

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

        // 단축키 툴팁 — 마우스 올리면 단축키 표시
        commitBtn.setTooltip(new Tooltip("Commit (Ctrl+Enter)"));
        pushBtn.setTooltip(new Tooltip("Push (Ctrl+Shift+P)"));
        pullBtn.setTooltip(new Tooltip("Pull (Ctrl+Shift+L)"));
        fetchBtn.setTooltip(new Tooltip("Fetch (Ctrl+Shift+F)"));

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
                            showErrorAlert("변경 취소 실패", ErrorMessages.mapGitError(error.getMessage()));
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

    /** 선택된 레포 폴더를 OS 탐색기에서 연다. 사이드바 컨텍스트 메뉴에서 호출. */
    private void openRepoInExplorer(Repository repo) {
        if (repo == null) return;
        try {
            Runtime.getRuntime().exec(new String[]{"explorer", repo.getPath()});
        } catch (Exception e) {
            log.error("탐색기 열기 실패: {}", repo.getPath(), e);
            setStatus("탐색기 열기 실패");
        }
    }

    /** 선택된 레포 경로에서 외부 터미널을 연다. 사이드바 컨텍스트 메뉴에서 호출. */
    private void openRepoInTerminal(Repository repo) {
        if (repo == null) return;
        openTerminalAt(repo.getPath());
    }

    /** 현재 선택된 레포의 루트 경로에서 외부 터미널을 연다. 파일 컨텍스트 메뉴에서 호출. */
    private void openInTerminal() {
        if (selectedRepo == null) return;
        openTerminalAt(selectedRepo.getPath());
    }

    /**
     * 지정된 디렉토리에서 설정된 외부 터미널을 연다.
     * 설정의 externalTerminal 값에 따라 cmd/powershell/wt를 실행한다.
     */
    private void openTerminalAt(String directory) {
        try {
            String terminal = "cmd";
            com.gitmini.config.ConfigManager cm = GitMiniApp.getConfigManager();
            if (cm != null) {
                terminal = cm.load().getExternalTerminal();
                if (terminal == null || terminal.isBlank()) terminal = "cmd";
            }

            ProcessBuilder pb;
            switch (terminal.toLowerCase()) {
                case "powershell":
                    pb = new ProcessBuilder("powershell", "-NoExit", "-Command",
                            "Set-Location '" + directory + "'");
                    break;
                case "wt":
                    pb = new ProcessBuilder("wt", "-d", directory);
                    break;
                default: // cmd
                    pb = new ProcessBuilder("cmd", "/c", "start", "cmd", "/k",
                            "cd /d " + directory);
                    break;
            }
            pb.directory(new File(directory));
            pb.start();
            setStatus("터미널 열기: " + directory);
            log.info("터미널 열기: {} ({})", directory, terminal);
        } catch (Exception e) {
            log.error("터미널 열기 실패: {}", directory, e);
            showErrorAlert("터미널 열기 실패",
                    "외부 터미널을 실행할 수 없습니다.\n\n"
                    + "설정에서 터미널 프로그램을 확인하세요.\n"
                    + e.getMessage());
            setStatus("터미널 열기 실패");
        }
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
