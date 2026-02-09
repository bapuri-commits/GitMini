package com.gitmini;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import com.gitmini.async.TaskManager;
import com.gitmini.config.AppConfig;
import com.gitmini.config.ConfigManager;
import com.gitmini.event.EventBus;
import com.gitmini.git.GitExecutor;
import com.gitmini.model.GitCommandRecord;
import com.gitmini.event.GitOperationCompletedEvent;
import com.gitmini.config.TokenManager;
import com.gitmini.service.GitHubService;
import com.gitmini.service.GitService;
import com.gitmini.service.RepositoryManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;

/**
 * GitMini 애플리케이션의 JavaFX 진입점.
 * <p>
 * 앱 전역에서 필요한 인프라 객체(TaskManager, ConfigManager)에 대한
 * static getter를 제공한다. Controller가 서비스 계층에 접근할 때 사용한다.
 * </p>
 */
public class GitMiniApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(GitMiniApp.class);

    private Stage primaryStage;
    private ConfigManager configManager;
    private AppConfig appConfig;
    private TaskManager taskManager;
    private GitExecutor gitExecutor;
    private GitService gitService;
    private GitHubService gitHubService;
    private RepositoryManager repositoryManager;
    private com.gitmini.controller.MainController mainController;

    // --- 앱 전역 접근자 ---
    private static TaskManager taskManagerInstance;
    private static ConfigManager configManagerInstance;
    private static GitService gitServiceInstance;
    private static GitHubService gitHubServiceInstance;
    private static RepositoryManager repositoryManagerInstance;

    /**
     * TaskManager 인스턴스를 반환한다. Controller에서 비동기 작업 실행 시 사용.
     * 앱 시작 전에는 null을 반환한다.
     */
    public static TaskManager getTaskManager() {
        return taskManagerInstance;
    }

    /**
     * ConfigManager 인스턴스를 반환한다. Controller에서 설정 접근 시 사용.
     * 앱 시작 전에는 null을 반환한다.
     */
    public static ConfigManager getConfigManager() {
        return configManagerInstance;
    }

    /**
     * GitService 인스턴스를 반환한다. Controller에서 Git 작업 시 사용.
     * 앱 시작 전 또는 Git 버전 확인 실패로 종료된 경우 null을 반환한다.
     */
    public static GitService getGitService() {
        return gitServiceInstance;
    }

    /**
     * GitHubService 인스턴스를 반환한다. Controller에서 GitHub API 호출 시 사용.
     * 앱 시작 전에는 null을 반환한다.
     */
    public static GitHubService getGitHubService() {
        return gitHubServiceInstance;
    }

    /**
     * RepositoryManager 인스턴스를 반환한다. Controller에서 레포 목록 관리 시 사용.
     * 앱 시작 전에는 null을 반환한다.
     */
    public static RepositoryManager getRepositoryManager() {
        return repositoryManagerInstance;
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        log.info("GitMini 시작");
        this.primaryStage = primaryStage;

        // 설정 로드
        configManager = new ConfigManager();
        configManagerInstance = configManager;
        appConfig = configManager.load();

        // 테마 적용 (config의 theme 값에 따라 Dark/Light 전환)
        applyTheme(appConfig.getTheme());

        // TaskManager 생성
        taskManager = new TaskManager();
        taskManagerInstance = taskManager;

        // EventBus FX 스레드 안전장치 활성화
        EventBus.getInstance().enableFxThreadDispatch(true);

        // Git 설치/버전 확인 — 실패 시 Alert 후 앱 종료
        gitExecutor = new GitExecutor();
        if (!gitExecutor.isGitVersionSupported()) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Git 필요");
            alert.setHeaderText("Git이 설치되어 있지 않거나 버전이 2.23.0 미만입니다.");
            alert.setContentText("GitMini를 사용하려면 Git 2.23.0 이상이 필요합니다.\n확인을 누르면 앱이 종료됩니다.");
            alert.showAndWait();
            Platform.exit();
            return;
        }

        gitService = new GitService(gitExecutor);
        gitServiceInstance = gitService;

        // RepositoryManager 생성
        repositoryManager = new RepositoryManager(configManager, gitService);
        repositoryManagerInstance = repositoryManager;

        // GitHubService 생성 (GitHub REST API 클라이언트)
        TokenManager tokenManager = new TokenManager(configManager.getConfigDir());
        gitHubService = new GitHubService(tokenManager);
        gitHubServiceInstance = gitHubService;

        // Command Log: 모든 git 명령 실행 시 EventBus로 발행 (Step 10 UI에서 구독)
        gitExecutor.addCommandListener(record -> Platform.runLater(() -> {
                EventBus.getInstance().publish(record);  // 원본 GitCommandRecord 직접 발행
                EventBus.getInstance().publish(toOperationEvent(record));  // 기존 이벤트도 유지
        }));

        // FXML 로드
        FXMLLoader loader = new FXMLLoader(
                Objects.requireNonNull(getClass().getResource("/fxml/main.fxml"),
                        "main.fxml을 찾을 수 없습니다"));
        Parent root = loader.load();
        mainController = loader.getController();

        // 저장된 창 크기가 화면보다 크면 기본값으로 복원 (최대화 종료 후 재시작 시 잘림 방지)
        double savedW = appConfig.getWindowWidth();
        double savedH = appConfig.getWindowHeight();
        javafx.geometry.Rectangle2D screenBounds = javafx.stage.Screen.getPrimary().getVisualBounds();
        if (savedW >= screenBounds.getWidth() || savedH >= screenBounds.getHeight()) {
            savedW = Math.min(1200, screenBounds.getWidth() * 0.8);
            savedH = Math.min(800, screenBounds.getHeight() * 0.8);
            log.info("저장된 창 크기가 화면보다 큼 → 기본값으로 복원 ({}x{})", savedW, savedH);
        }

        // Scene 설정
        Scene scene = new Scene(root, savedW, savedH);
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/css/app.css"),
                        "app.css를 찾을 수 없습니다").toExternalForm());

        // Stage 설정
        primaryStage.setTitle("GitMini");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(600);

        // 종료 확인 다이얼로그: X 버튼 클릭 시 확인 후 종료
        primaryStage.setOnCloseRequest(event -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.initOwner(primaryStage); // 메인 창 중앙에 표시
            confirm.setTitle("GitMini 종료");
            confirm.setHeaderText("앱을 종료하시겠습니까?");
            confirm.setContentText("진행 중인 작업이 있으면 중단됩니다.");

            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.OK) {
                event.consume(); // 종료 취소
            }
            // OK를 누르면 event가 처리되어 정상 종료 → stop() 호출
        });

        // 저장된 창 위치 복원 (-1이면 화면 중앙, 화면 밖이면 무시)
        double savedX = appConfig.getWindowX();
        double savedY = appConfig.getWindowY();
        if (savedX >= 0 && savedY >= 0
                && savedX < screenBounds.getMaxX() && savedY < screenBounds.getMaxY()) {
            primaryStage.setX(savedX);
            primaryStage.setY(savedY);
        }

        primaryStage.show();
        log.info("GitMini UI 표시 완료 ({}x{}, 테마: {})",
                appConfig.getWindowWidth(), appConfig.getWindowHeight(), appConfig.getTheme());
    }

    @Override
    public void stop() {
        log.info("GitMini 종료 시작");

        // TaskManager 종료 (백그라운드 작업 정리)
        if (taskManager != null) {
            taskManager.shutdown();
        }

        // 현재 창 크기/위치를 설정에 반영 후 저장
        // 주의: 디스크에서 최신 config를 다시 로드하여 창 정보만 갱신한다.
        // RepositoryManager 등 다른 모듈이 저장한 repoPaths 등을 덮어쓰지 않기 위함.
        try {
            if (primaryStage != null && configManager != null) {
                AppConfig currentConfig = configManager.load();
                currentConfig.setWindowWidth(primaryStage.getWidth());
                currentConfig.setWindowHeight(primaryStage.getHeight());
                currentConfig.setWindowX(primaryStage.getX());
                currentConfig.setWindowY(primaryStage.getY());
                configManager.save(currentConfig);
                log.info("설정 저장 완료 (repoPaths: {}개, 창 크기: {}x{})",
                        currentConfig.getRepoPaths().size(),
                        primaryStage.getWidth(), primaryStage.getHeight());
            }
        } catch (Exception e) {
            log.error("종료 시 설정 저장 실패", e);
        }

        // MainController 리소스 정리 (타이머, EventBus 구독 해제)
        if (mainController != null) {
            try { mainController.dispose(); } catch (Exception e) { log.debug("컨트롤러 정리 실패", e); }
        }

        // 앱 전역 참조 정리
        repositoryManagerInstance = null;
        gitHubServiceInstance = null;
        gitServiceInstance = null;
        taskManagerInstance = null;
        configManagerInstance = null;

        log.info("GitMini 종료 완료");
    }

    // ========== 테마 ==========

    /**
     * 테마를 적용한다. 설정값에 따라 Dark/Light를 전환한다.
     *
     * @param theme "primer-dark" 또는 "primer-light"
     */
    public static void applyTheme(String theme) {
        if ("primer-light".equalsIgnoreCase(theme)) {
            Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
            log.info("테마 적용: Primer Light");
        } else {
            Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
            log.info("테마 적용: Primer Dark");
        }
    }

    private static GitOperationCompletedEvent toOperationEvent(GitCommandRecord record) {
        String message = record.timestamp().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                + " " + record.durationMs() + "ms";
        return new GitOperationCompletedEvent(
                record.repoPath(), record.command(), record.success(), message);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
