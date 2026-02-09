package com.gitmini.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    // ========== 커밋 메시지 히스토리 (레포별) ==========

    @Test
    void 기본_commitMessageHistory는_빈_맵(@TempDir Path tempDir) {
        ConfigManager manager = new ConfigManager(tempDir);
        AppConfig config = manager.load();

        assertNotNull(config.getCommitMessageHistory());
        assertTrue(config.getCommitMessageHistory().isEmpty());
    }

    @Test
    void commitMessageHistory_레포별_저장_후_로드(@TempDir Path tempDir) {
        ConfigManager manager = new ConfigManager(tempDir);

        AppConfig config = new AppConfig();
        Map<String, List<String>> historyMap = new LinkedHashMap<>();
        historyMap.put("C:/repo1", List.of("fix: typo", "feat: add login"));
        historyMap.put("C:/repo2", List.of("docs: update README"));
        config.setCommitMessageHistory(historyMap);
        manager.save(config);

        AppConfig loaded = manager.load();
        assertEquals(2, loaded.getCommitMessageHistory().size());
        assertEquals(List.of("fix: typo", "feat: add login"), loaded.getCommitMessageHistory().get("C:/repo1"));
        assertEquals(List.of("docs: update README"), loaded.getCommitMessageHistory().get("C:/repo2"));
    }

    @Test
    void commitMessageHistory_null이면_validate가_빈_맵으로_복구() {
        AppConfig config = new AppConfig();
        config.setCommitMessageHistory(null);
        config.validate();

        assertNotNull(config.getCommitMessageHistory());
        assertTrue(config.getCommitMessageHistory().isEmpty());
    }

    @Test
    void commitMessageHistory_setter_null_방어() {
        AppConfig config = new AppConfig();
        config.setCommitMessageHistory(null);

        assertNotNull(config.getCommitMessageHistory());
        assertTrue(config.getCommitMessageHistory().isEmpty());
    }

    @Test
    void commitMessageHistory_기존설정에_없어도_로드시_기본값(@TempDir Path tempDir) throws Exception {
        // commitMessageHistory 필드가 없는 구버전 JSON 시뮬레이션
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile, """
                {
                  "theme": "primer-dark",
                  "autoFetchIntervalMinutes": 5,
                  "repoPaths": []
                }
                """);

        ConfigManager manager = new ConfigManager(tempDir);
        AppConfig config = manager.load();

        assertNotNull(config.getCommitMessageHistory());
        assertTrue(config.getCommitMessageHistory().isEmpty());
    }

    @Test
    void commitMessageHistory_구버전_리스트형식이면_기본값으로_복구(@TempDir Path tempDir) throws Exception {
        // Phase 6 초기 구현의 List<String> 형식 → Map으로 변경 시 하위 호환
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile, """
                {
                  "theme": "primer-dark",
                  "commitMessageHistory": ["old1", "old2"]
                }
                """);

        ConfigManager manager = new ConfigManager(tempDir);
        AppConfig config = manager.load();

        // Gson이 List를 Map으로 역직렬화 실패 → 전체 config 기본값 반환
        assertNotNull(config.getCommitMessageHistory());
        // 타입 불일치 시 기본값으로 복구됨
    }

    @Test
    void commitMessageHistory_레포별_중복제거_및_순서보존(@TempDir Path tempDir) {
        ConfigManager manager = new ConfigManager(tempDir);
        String repoKey = "C:/my-repo";

        AppConfig config = new AppConfig();
        Map<String, List<String>> historyMap = new LinkedHashMap<>();
        List<String> history = new ArrayList<>(List.of("msg1", "msg2", "msg3"));
        historyMap.put(repoKey, history);
        config.setCommitMessageHistory(historyMap);
        manager.save(config);

        // "msg2"를 다시 커밋 → 맨 앞으로 (MainController 로직 시뮬레이션)
        AppConfig loaded = manager.load();
        List<String> updated = new ArrayList<>(loaded.getCommitMessageHistory().getOrDefault(repoKey, List.of()));
        updated.remove("msg2");
        updated.add(0, "msg2");
        loaded.getCommitMessageHistory().put(repoKey, updated);
        manager.save(loaded);

        AppConfig result = manager.load();
        assertEquals(List.of("msg2", "msg1", "msg3"), result.getCommitMessageHistory().get(repoKey));
    }

    @Test
    void commitMessageHistory_레포_삭제시_해당_레포만_제거(@TempDir Path tempDir) {
        ConfigManager manager = new ConfigManager(tempDir);

        AppConfig config = new AppConfig();
        Map<String, List<String>> historyMap = new LinkedHashMap<>();
        historyMap.put("C:/repo1", new ArrayList<>(List.of("msg-a")));
        historyMap.put("C:/repo2", new ArrayList<>(List.of("msg-b")));
        config.setCommitMessageHistory(historyMap);
        manager.save(config);

        // repo1 히스토리만 삭제
        AppConfig loaded = manager.load();
        loaded.getCommitMessageHistory().remove("C:/repo1");
        manager.save(loaded);

        AppConfig result = manager.load();
        assertNull(result.getCommitMessageHistory().get("C:/repo1"));
        assertEquals(List.of("msg-b"), result.getCommitMessageHistory().get("C:/repo2"));
    }
}
