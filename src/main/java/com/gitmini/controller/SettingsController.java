package com.gitmini.controller;

import com.gitmini.GitMiniApp;
import com.gitmini.async.TaskManager;
import com.gitmini.config.AppConfig;
import com.gitmini.config.ConfigManager;
import com.gitmini.config.TokenManager;
import com.gitmini.model.GitHubUser;
import com.gitmini.service.GitHubService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * 설정 다이얼로그 컨트롤러.
 * <p>
 * 모달 다이얼로그로 표시되며, 저장/취소 버튼으로 닫는다.
 * 토큰 테스트는 GitHubService를 통해 비동기로 검증한다.
 * </p>
 *
 * <h3>설정 항목 (Phase 4)</h3>
 * <ul>
 *   <li>일반: 자동 Fetch 주기, 기본 Clone 경로</li>
 *   <li>GitHub: PAT 입력/저장/테스트/삭제</li>
 * </ul>
 */
public class SettingsController {

    private static final Logger log = LoggerFactory.getLogger(SettingsController.class);

    // ========== FXML 바인딩: 일반 ==========

    @FXML private ComboBox<String> themeComboBox;
    @FXML private ComboBox<String> autoFetchComboBox;
    @FXML private TextField defaultClonePathField;
    @FXML private ComboBox<String> terminalComboBox;
    @FXML private CheckBox minimizeToTrayCheckBox;

    // ========== FXML 바인딩: GitHub ==========

    @FXML private PasswordField tokenField;
    @FXML private Button tokenTestBtn;
    @FXML private Button tokenDeleteBtn;
    @FXML private Label tokenStatusLabel;
    @FXML private Label tokenHintLabel;

    // ========== FXML 바인딩: 하단 ==========

    @FXML private Button cancelBtn;
    @FXML private Button saveBtn;

    /** 저장 성공 여부. 호출자(MainController)가 확인할 수 있다. */
    private boolean saved = false;

    /** 토큰 삭제 예약 플래그. "저장" 시에만 실제 삭제를 수행한다. */
    private boolean tokenDeletePending = false;

    /** 자동 Fetch 주기 옵션 (분). */
    private static final int[] FETCH_INTERVALS = {1, 2, 3, 5, 10, 15, 30, 60};

    /** 테마 옵션: 표시명 → config 값. */
    private static final String[][] THEME_OPTIONS = {
            {"Primer Dark", "primer-dark"},
            {"Primer Light", "primer-light"}
    };

    /** 외부 터미널 옵션: 표시명 → 실행 명령어. */
    private static final String[][] TERMINAL_OPTIONS = {
            {"CMD (기본)", "cmd"},
            {"PowerShell", "powershell"},
            {"Windows Terminal", "wt"}
    };

    @FXML
    public void initialize() {
        log.info("SettingsController 초기화");

        // 테마 ComboBox 세팅
        themeComboBox.setItems(FXCollections.observableArrayList(
                THEME_OPTIONS[0][0], THEME_OPTIONS[1][0]
        ));

        // 자동 Fetch 주기 ComboBox 세팅
        autoFetchComboBox.setItems(FXCollections.observableArrayList(
                "1분", "2분", "3분", "5분", "10분", "15분", "30분", "60분"
        ));

        // 외부 터미널 ComboBox 세팅
        terminalComboBox.setItems(FXCollections.observableArrayList(
                TERMINAL_OPTIONS[0][0], TERMINAL_OPTIONS[1][0], TERMINAL_OPTIONS[2][0]
        ));

        // 현재 설정값 로드
        loadCurrentSettings();
    }

    /**
     * 현재 저장된 설정/토큰을 UI에 반영한다.
     */
    private void loadCurrentSettings() {
        ConfigManager configManager = GitMiniApp.getConfigManager();
        if (configManager == null) return;

        AppConfig config = configManager.load();

        // 테마
        String currentTheme = config.getTheme();
        boolean themeSet = false;
        for (int i = 0; i < THEME_OPTIONS.length; i++) {
            if (THEME_OPTIONS[i][1].equalsIgnoreCase(currentTheme)) {
                themeComboBox.getSelectionModel().select(i);
                themeSet = true;
                break;
            }
        }
        if (!themeSet) {
            themeComboBox.getSelectionModel().select(0); // 기본 Primer Dark
        }

        // 자동 Fetch 주기
        int interval = config.getAutoFetchIntervalMinutes();
        for (int i = 0; i < FETCH_INTERVALS.length; i++) {
            if (FETCH_INTERVALS[i] == interval) {
                autoFetchComboBox.getSelectionModel().select(i);
                break;
            }
        }
        if (autoFetchComboBox.getSelectionModel().isEmpty()) {
            autoFetchComboBox.getSelectionModel().select(3); // 기본 5분
        }

        // 기본 Clone 경로
        defaultClonePathField.setText(config.getDefaultClonePath());

        // 외부 터미널
        String currentTerminal = config.getExternalTerminal();
        boolean terminalSet = false;
        for (int i = 0; i < TERMINAL_OPTIONS.length; i++) {
            if (TERMINAL_OPTIONS[i][1].equalsIgnoreCase(currentTerminal)) {
                terminalComboBox.getSelectionModel().select(i);
                terminalSet = true;
                break;
            }
        }
        if (!terminalSet) {
            terminalComboBox.getSelectionModel().select(0); // 기본 CMD
        }

        // 트레이 최소화
        if (minimizeToTrayCheckBox != null) {
            minimizeToTrayCheckBox.setSelected(config.isMinimizeToTray());
        }

        // 토큰: 저장된 토큰이 있으면 마스킹 표시
        GitHubService gitHubService = GitMiniApp.getGitHubService();
        if (gitHubService != null && gitHubService.hasToken()) {
            tokenField.setPromptText("저장된 토큰 있음 (변경하려면 새 토큰 입력)");
            tokenStatusLabel.setText("토큰 저장됨");
            tokenStatusLabel.setStyle("-fx-text-fill: -color-success-fg;");
        }
    }

    // ========== GitHub 토큰 ==========

    /**
     * 토큰 테스트 버튼 클릭.
     * 입력된 토큰(또는 저장된 토큰)으로 GitHub API를 호출하여 유효성을 검증한다.
     */
    @FXML
    private void onTokenTest() {
        GitHubService gitHubService = GitMiniApp.getGitHubService();
        TaskManager taskManager = GitMiniApp.getTaskManager();
        if (gitHubService == null || taskManager == null) return;

        String inputToken = tokenField.getText();
        boolean useInputToken = inputToken != null && !inputToken.isBlank();

        // 입력도 없고 저장된 토큰도 없으면 안내
        if (!useInputToken && !gitHubService.hasToken()) {
            tokenStatusLabel.setText("토큰을 입력하세요");
            tokenStatusLabel.setStyle("-fx-text-fill: -color-danger-fg;");
            return;
        }

        tokenTestBtn.setDisable(true);
        tokenStatusLabel.setText("검증 중...");
        tokenStatusLabel.setStyle("");

        taskManager.run(
                () -> {
                    if (useInputToken) {
                        return gitHubService.validateToken(inputToken);
                    } else {
                        return gitHubService.validateToken();
                    }
                },
                user -> {
                    tokenTestBtn.setDisable(false);
                    tokenStatusLabel.setText("✓ " + user.displayName());
                    tokenStatusLabel.setStyle("-fx-text-fill: -color-success-fg;");
                    log.info("토큰 검증 성공: {}", user.login());
                },
                error -> {
                    tokenTestBtn.setDisable(false);
                    String errMsg = error.getMessage();
                    if (errMsg != null && errMsg.length() > 80) {
                        errMsg = errMsg.substring(0, 80) + "...";
                    }
                    tokenStatusLabel.setText("✗ " + errMsg);
                    tokenStatusLabel.setStyle("-fx-text-fill: -color-danger-fg;");
                    log.warn("토큰 검증 실패: {}", error.getMessage());
                }
        );
    }

    /**
     * 토큰 삭제 버튼 클릭.
     */
    @FXML
    private void onTokenDelete() {
        // 즉시 삭제하지 않고, 저장 시에만 실제 삭제 (취소 시 복원 가능)
        tokenDeletePending = true;
        tokenField.clear();
        tokenField.setPromptText("ghp_xxxxxxxxxxxx");
        tokenStatusLabel.setText("토큰 삭제 예정 (저장 시 적용)");
        tokenStatusLabel.setStyle("-fx-text-fill: -color-fg-muted;");
        log.info("토큰 삭제 예약됨 (저장 시 실제 삭제)");
    }

    // ========== 일반 설정 ==========

    /**
     * Clone 경로 찾아보기 버튼.
     */
    @FXML
    private void onBrowseClonePath() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("기본 Clone 경로 선택");

        String currentPath = defaultClonePathField.getText();
        if (currentPath != null && !currentPath.isBlank()) {
            File dir = new File(currentPath);
            if (dir.isDirectory()) {
                chooser.setInitialDirectory(dir);
            }
        }

        Stage stage = (Stage) saveBtn.getScene().getWindow();
        File selected = chooser.showDialog(stage);
        if (selected != null) {
            defaultClonePathField.setText(selected.getAbsolutePath());
        }
    }

    // ========== 저장 / 취소 ==========

    /**
     * 저장 버튼 클릭. 설정을 config.json에 저장하고, 토큰이 입력되었으면 .token에도 저장한다.
     */
    @FXML
    private void onSave() {
        ConfigManager configManager = GitMiniApp.getConfigManager();
        if (configManager == null) return;

        try {
            // 현재 디스크 설정 로드 (다른 모듈이 변경한 repoPaths 등을 보존)
            AppConfig config = configManager.load();

            // 테마
            int themeIdx = themeComboBox.getSelectionModel().getSelectedIndex();
            if (themeIdx >= 0 && themeIdx < THEME_OPTIONS.length) {
                config.setTheme(THEME_OPTIONS[themeIdx][1]);
            }

            // 자동 Fetch 주기
            int selectedIdx = autoFetchComboBox.getSelectionModel().getSelectedIndex();
            if (selectedIdx >= 0 && selectedIdx < FETCH_INTERVALS.length) {
                config.setAutoFetchIntervalMinutes(FETCH_INTERVALS[selectedIdx]);
            }

            // 외부 터미널
            int terminalIdx = terminalComboBox.getSelectionModel().getSelectedIndex();
            if (terminalIdx >= 0 && terminalIdx < TERMINAL_OPTIONS.length) {
                config.setExternalTerminal(TERMINAL_OPTIONS[terminalIdx][1]);
            }

            // 트레이 최소화
            if (minimizeToTrayCheckBox != null) {
                config.setMinimizeToTray(minimizeToTrayCheckBox.isSelected());
            }

            // 기본 Clone 경로 — 비어 있지 않으면 유효성 검증
            String clonePath = defaultClonePathField.getText().trim();
            if (!clonePath.isEmpty()) {
                java.io.File cloneDir = new java.io.File(clonePath);
                if (!cloneDir.isDirectory()) {
                    Alert pathAlert = new Alert(Alert.AlertType.WARNING);
                    Stage stage = (Stage) saveBtn.getScene().getWindow();
                    pathAlert.initOwner(stage);
                    pathAlert.setTitle("Clone 경로 확인");
                    pathAlert.setHeaderText("기본 Clone 경로가 존재하지 않습니다");
                    pathAlert.setContentText(clonePath + "\n\n이 경로를 그대로 사용하시겠습니까?");
                    pathAlert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
                    var pathResult = pathAlert.showAndWait();
                    if (pathResult.isEmpty() || pathResult.get() != ButtonType.YES) {
                        return; // 저장 취소 → 사용자가 경로를 수정하도록
                    }
                }
            }
            config.setDefaultClonePath(clonePath);

            // 설정 저장
            configManager.save(config);
            log.info("설정 저장 완료: autoFetch={}분, clonePath={}",
                    config.getAutoFetchIntervalMinutes(), config.getDefaultClonePath());

            // 토큰 처리: 삭제 예약 > 새 입력 > 변경 없음
            TokenManager tokenManager = new TokenManager(configManager.getConfigDir());
            String inputToken = tokenField.getText();
            if (tokenDeletePending && (inputToken == null || inputToken.isBlank())) {
                // 삭제 예약 + 새 입력 없음 → 실제 삭제
                tokenManager.deleteToken();
                log.info("토큰 삭제 완료");
            } else if (inputToken != null && !inputToken.isBlank()) {
                // 새 토큰 입력됨 → 저장 (삭제 예약은 무시)
                tokenManager.saveToken(inputToken.trim());
                log.info("토큰 저장 완료");
            }

            saved = true;
            closeDialog();

        } catch (Exception e) {
            log.error("설정 저장 실패", e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            Stage stage = (Stage) saveBtn.getScene().getWindow();
            alert.initOwner(stage);
            alert.setTitle("저장 실패");
            alert.setHeaderText("설정 저장에 실패했습니다");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }

    /**
     * 취소 버튼 클릭. 변경 사항 없이 다이얼로그를 닫는다.
     */
    @FXML
    private void onCancel() {
        saved = false;
        closeDialog();
    }

    /**
     * 저장이 성공했는지 여부. 다이얼로그가 닫힌 후 호출자가 확인한다.
     */
    public boolean isSaved() {
        return saved;
    }

    private void closeDialog() {
        Stage stage = (Stage) saveBtn.getScene().getWindow();
        stage.close();
    }
}
