package com.gitmini.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigManagerTest {

    @Test
    void 설정파일_없으면_기본값_반환(@TempDir Path tempDir) {
        ConfigManager manager = new ConfigManager(tempDir);

        AppConfig config = manager.load();

        assertNotNull(config);
        assertEquals("primer-dark", config.getTheme());
        assertEquals(5, config.getAutoFetchIntervalMinutes());
        assertEquals(1200, config.getWindowWidth());
        assertEquals(800, config.getWindowHeight());
        assertTrue(config.getRepoPaths().isEmpty());
    }

    @Test
    void 설정_저장_후_로드(@TempDir Path tempDir) {
        ConfigManager manager = new ConfigManager(tempDir);

        // 저장
        AppConfig config = new AppConfig();
        config.setTheme("nord-light");
        config.setAutoFetchIntervalMinutes(10);
        config.getRepoPaths().add("C:/Projects/test-repo");
        config.getRepoPaths().add("D:/Work/another-repo");
        config.setWindowWidth(1400);
        config.setWindowHeight(900);
        config.setWindowX(100);
        config.setWindowY(50);

        manager.save(config);

        // 파일 존재 확인
        assertTrue(Files.exists(tempDir.resolve("config.json")));

        // 로드
        AppConfig loaded = manager.load();
        assertEquals("nord-light", loaded.getTheme());
        assertEquals(10, loaded.getAutoFetchIntervalMinutes());
        assertEquals(2, loaded.getRepoPaths().size());
        assertEquals("C:/Projects/test-repo", loaded.getRepoPaths().get(0));
        assertEquals("D:/Work/another-repo", loaded.getRepoPaths().get(1));
        assertEquals(1400, loaded.getWindowWidth());
        assertEquals(900, loaded.getWindowHeight());
        assertEquals(100, loaded.getWindowX());
        assertEquals(50, loaded.getWindowY());
    }

    @Test
    void 손상된_설정파일은_기본값_반환(@TempDir Path tempDir) throws Exception {
        // 손상된 JSON 파일 생성
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile, "{ invalid json content !!!");

        ConfigManager manager = new ConfigManager(tempDir);
        AppConfig config = manager.load();

        // 예외 없이 기본값 반환
        assertNotNull(config);
        assertEquals("primer-dark", config.getTheme());
    }

    @Test
    void 빈_설정파일은_기본값_반환(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile, "");

        ConfigManager manager = new ConfigManager(tempDir);
        AppConfig config = manager.load();

        assertNotNull(config);
        assertEquals("primer-dark", config.getTheme());
    }

    @Test
    void 설정_디렉토리_자동_생성(@TempDir Path tempDir) {
        Path nestedDir = tempDir.resolve("sub").resolve("dir");
        ConfigManager manager = new ConfigManager(nestedDir);

        manager.save(new AppConfig());

        assertTrue(Files.exists(nestedDir.resolve("config.json")));
    }
}
