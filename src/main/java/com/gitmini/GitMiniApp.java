package com.gitmini;

import atlantafx.base.theme.PrimerDark;
import com.gitmini.async.TaskManager;
import com.gitmini.config.AppConfig;
import com.gitmini.config.ConfigManager;
import com.gitmini.event.EventBus;
import com.gitmini.git.GitExecutor;
import com.gitmini.model.GitCommandRecord;
import com.gitmini.event.GitOperationCompletedEvent;
import com.gitmini.service.GitService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.Objects;

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

    // --- 앱 전역 접근자 ---
    private static TaskManager taskManagerInstance;
    private static ConfigManager configManagerInstance;
    private static GitService gitServiceInstance;

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

    @Override
    public void start(Stage primaryStage) throws Exception {
        log.info("GitMini 시작");
        this.primaryStage = primaryStage;

        // 테마 적용
        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());

        // 설정 로드
        configManager = new ConfigManager();
        configManagerInstance = configManager;
        appConfig = configManager.load();

        // TaskManager 생성
        taskManager = new TaskManager();
        taskManagerInstance = taskManager;

        // EventBus FX 스레드 안전장치 활성화
        EventBus.getInstance().enableFxThreadDispatch(true);

        // Git 설치/버전 확인 — 실패 시 Alert 후 앱 종료 (Q4)
        gitExecutor = new GitExecutor();
        if (!gitExecutor.isGitVersionSupported()) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Git 필요");
            alert.setHeaderText("Git이 설치되어 있지 않거나 버전이 2.23.0 미만입니다.");
            alert.setContentText("GitMini를 사용하려면 Git 2.23.0 이상이 필요합니다. 확인을 누르면 앱이 종료됩니다.");
            alert.showAndWait();
            Platform.exit();
            return;
        }

        gitService = new GitService(gitExecutor);
        gitServiceInstance = gitService;

        // Command Log: 모든 git 명령 실행 시 EventBus로 발행 (Step 10 UI에서 구독)
        gitExecutor.addCommandListener(record -> Platform.runLater(() ->
                EventBus.getInstance().publish(toOperationEvent(record))));

        // FXML 로드
        FXMLLoader loader = new FXMLLoader(
                Objects.requireNonNull(getClass().getResource("/fxml/main.fxml"),
                        "main.fxml을 찾을 수 없습니다"));
        Parent root = loader.load();

        // Scene 설정
        Scene scene = new Scene(root, appConfig.getWindowWidth(), appConfig.getWindowHeight());
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/css/app.css"),
                        "app.css를 찾을 수 없습니다").toExternalForm());

        // Stage 설정
        primaryStage.setTitle("GitMini");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(600);

        // 저장된 창 위치 복원 (-1이면 화면 중앙)
        if (appConfig.getWindowX() >= 0 && appConfig.getWindowY() >= 0) {
            primaryStage.setX(appConfig.getWindowX());
            primaryStage.setY(appConfig.getWindowY());
        }

        primaryStage.show();
        log.info("GitMini UI 표시 완료 ({}x{})", appConfig.getWindowWidth(), appConfig.getWindowHeight());
    }

    @Override
    public void stop() {
        log.info("GitMini 종료 시작");

        // TaskManager 종료 (백그라운드 작업 정리)
        if (taskManager != null) {
            taskManager.shutdown();
        }

        // 현재 창 크기/위치를 설정에 반영 후 저장
        try {
            if (primaryStage != null && configManager != null && appConfig != null) {
                appConfig.setWindowWidth(primaryStage.getWidth());
                appConfig.setWindowHeight(primaryStage.getHeight());
                appConfig.setWindowX(primaryStage.getX());
                appConfig.setWindowY(primaryStage.getY());
                configManager.save(appConfig);
                log.info("설정 저장 완료 (창 크기: {}x{}, 위치: {}, {})",
                        primaryStage.getWidth(), primaryStage.getHeight(),
                        primaryStage.getX(), primaryStage.getY());
            }
        } catch (Exception e) {
            log.error("종료 시 설정 저장 실패", e);
        }

        // 앱 전역 참조 정리
        taskManagerInstance = null;
        configManagerInstance = null;
        gitServiceInstance = null;

        log.info("GitMini 종료 완료");
    }

    private static GitOperationCompletedEvent toOperationEvent(GitCommandRecord record) {
        String message = record.timestamp().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                + " " + record.durationMs() + "ms";
        return new GitOperationCompletedEvent("", record.command(), record.success(), message);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
