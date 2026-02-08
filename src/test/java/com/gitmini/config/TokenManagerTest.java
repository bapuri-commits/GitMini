package com.gitmini.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TokenManagerTest {

    @Test
    void 토큰_없으면_empty_반환(@TempDir Path tempDir) {
        TokenManager manager = new TokenManager(tempDir);

        Optional<String> token = manager.loadToken();

        assertTrue(token.isEmpty());
    }

    @Test
    void 토큰_저장_후_로드(@TempDir Path tempDir) {
        TokenManager manager = new TokenManager(tempDir);

        manager.saveToken("ghp_test1234567890");

        Optional<String> token = manager.loadToken();
        assertTrue(token.isPresent());
        assertEquals("ghp_test1234567890", token.get());
    }

    @Test
    void 토큰_삭제(@TempDir Path tempDir) {
        TokenManager manager = new TokenManager(tempDir);

        manager.saveToken("ghp_test1234567890");
        assertTrue(manager.loadToken().isPresent());

        manager.deleteToken();
        assertTrue(manager.loadToken().isEmpty());
    }

    @Test
    void 빈_토큰은_empty_반환(@TempDir Path tempDir) {
        TokenManager manager = new TokenManager(tempDir);

        manager.saveToken("   ");

        Optional<String> token = manager.loadToken();
        assertTrue(token.isEmpty());
    }

    @Test
    void 토큰_덮어쓰기(@TempDir Path tempDir) {
        TokenManager manager = new TokenManager(tempDir);

        manager.saveToken("old_token");
        manager.saveToken("new_token");

        assertEquals("new_token", manager.loadToken().orElse(""));
    }
}
