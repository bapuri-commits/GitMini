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

    @FXML private ComboBox<String> autoFetchComboBox;
    @FXML private TextField defaultClonePathField;

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

    /** 자동 Fetch 주기 옵션 (분). */
    private static final int[] FETCH_INTERVALS = {1, 2, 3, 5, 10, 15, 30, 60};

    @FXML
    public void initialize() {
        log.info("SettingsController 초기화");

        // 자동 Fetch 주기 ComboBox 세팅
        autoFetchComboBox.setItems(FXCollections.observableArrayList(
                "1분", "2분", "3분", "5분", "10분", "15분", "30분", "60분"
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
        ConfigManager configManager = GitMiniApp.getConfigManager();
        if (configManager == null) return;

        TokenManager tokenManager = new TokenManager(configManager.getConfigDir());
        tokenManager.deleteToken();

        tokenField.clear();
        tokenField.setPromptText("ghp_xxxxxxxxxxxx");
        tokenStatusLabel.setText("토큰 삭제됨");
        tokenStatusLabel.setStyle("-fx-text-fill: -color-fg-muted;");
        log.info("토큰 삭제 완료");
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

            // 자동 Fetch 주기
            int selectedIdx = autoFetchComboBox.getSelectionModel().getSelectedIndex();
            if (selectedIdx >= 0 && selectedIdx < FETCH_INTERVALS.length) {
                config.setAutoFetchIntervalMinutes(FETCH_INTERVALS[selectedIdx]);
            }

            // 기본 Clone 경로
            config.setDefaultClonePath(defaultClonePathField.getText().trim());

            // 설정 저장
            configManager.save(config);
            log.info("설정 저장 완료: autoFetch={}분, clonePath={}",
                    config.getAutoFetchIntervalMinutes(), config.getDefaultClonePath());

            // 토큰 저장 (입력된 경우에만)
            String inputToken = tokenField.getText();
            if (inputToken != null && !inputToken.isBlank()) {
                TokenManager tokenManager = new TokenManager(configManager.getConfigDir());
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
