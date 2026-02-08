package com.gitmini;

import com.gitmini.config.ConfigManager;

import java.nio.file.Path;

/**
 * 애플리케이션 런처.
 * <p>
 * JavaFX Application 클래스를 직접 main class로 사용하면
 * module system 관련 이슈가 발생할 수 있어, 별도의 Launcher를 사용한다.
 * 앱 실행 전 필요한 시스템 속성 설정도 여기서 수행한다.
 * </p>
 */
public class Launcher {

    public static void main(String[] args) {
        // 로그 디렉토리 시스템 속성 설정 (logback.xml에서 사용)
        Path logDir = ConfigManager.resolveDefaultConfigDir().resolve("logs");
        System.setProperty("GITMINI_LOG_DIR", logDir.toString());

        GitMiniApp.main(args);
    }
}
