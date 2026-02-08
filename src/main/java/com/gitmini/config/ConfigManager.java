package com.gitmini.config;

import com.gitmini.exception.ConfigException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 앱 설정을 JSON 파일로 관리한다.
 * <p>
 * 기본 저장 위치: %APPDATA%/GitMini/config.json (Windows)
 * 또는 ~/.gitmini/config.json (기타 OS)
 * </p>
 */
public class ConfigManager {

    private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);
    private static final String CONFIG_FILE_NAME = "config.json";

    private final Gson gson;
    private final Path configDir;
    private final Path configFile;

    /**
     * 기본 설정 디렉토리를 사용하는 생성자.
     */
    public ConfigManager() {
        this(resolveDefaultConfigDir());
    }

    /**
     * 지정된 디렉토리를 사용하는 생성자 (테스트용).
     */
    ConfigManager(Path configDir) {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.configDir = configDir;
        this.configFile = configDir.resolve(CONFIG_FILE_NAME);
    }

    /**
     * OS에 따른 기본 설정 디렉토리를 반환한다.
     */
    public static Path resolveDefaultConfigDir() {
        String appdata = System.getenv("APPDATA");
        if (appdata != null) {
            return Path.of(appdata, "GitMini");
        }
        return Path.of(System.getProperty("user.home"), ".gitmini");
    }

    /**
     * 설정 파일을 로드한다.
     * 파일이 없거나 파싱 실패 시 기본값을 반환한다 (절대 예외를 던지지 않음).
     */
    public AppConfig load() {
        if (!Files.exists(configFile)) {
            log.info("설정 파일 없음, 기본값 사용: {}", configFile);
            return new AppConfig();
        }

        try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            AppConfig config = gson.fromJson(reader, AppConfig.class);
            if (config == null) {
                log.warn("설정 파일이 비어 있음, 기본값 사용");
                return new AppConfig();
            }
            config.validate();
            log.info("설정 로드 완료: {}", configFile);
            return config;
        } catch (Exception e) {
            log.error("설정 로드 실패, 기본값 사용: {}", configFile, e);
            return new AppConfig();
        }
    }

    /**
     * 설정을 JSON 파일로 저장한다.
     *
     * @throws ConfigException 저장 실패 시
     */
    public void save(AppConfig config) {
        try {
            config.validate(); // NaN 등 비정상 값 방어
            Files.createDirectories(configDir);
            // 원자적 저장: 임시 파일에 쓴 후 이동 (강제 종료 시 파일 깨짐 방지)
            Path tempFile = configDir.resolve(CONFIG_FILE_NAME + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                gson.toJson(config, writer);
            }
            Files.move(tempFile, configFile,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            log.debug("설정 저장 완료: {}", configFile);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            // ATOMIC_MOVE 미지원 시 일반 이동
            try {
                Path tempFile = configDir.resolve(CONFIG_FILE_NAME + ".tmp");
                Files.move(tempFile, configFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                log.debug("설정 저장 완료 (non-atomic): {}", configFile);
            } catch (IOException ex) {
                log.error("설정 저장 실패: {}", configFile, ex);
                throw new ConfigException("설정 저장 실패: " + ex.getMessage(), ex);
            }
        } catch (IOException e) {
            log.error("설정 저장 실패: {}", configFile, e);
            throw new ConfigException("설정 저장 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 설정 디렉토리 경로를 반환한다.
     */
    public Path getConfigDir() {
        return configDir;
    }

    /**
     * 설정 파일 경로를 반환한다.
     */
    public Path getConfigFile() {
        return configFile;
    }
}
