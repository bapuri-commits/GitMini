package com.gitmini.config;

import com.gitmini.exception.ConfigException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * GitHub Personal Access Token을 파일로 관리한다.
 * <p>
 * 저장 위치: 설정 디렉토리 내 .token 파일
 * Git push/pull 인증은 git credential helper에 위임하고,
 * 이 클래스는 GitHub REST API용 PAT만 관리한다.
 * </p>
 */
public class TokenManager {

    private static final Logger log = LoggerFactory.getLogger(TokenManager.class);
    private static final String TOKEN_FILE_NAME = ".token";

    private final Path tokenFile;

    public TokenManager(Path configDir) {
        this.tokenFile = configDir.resolve(TOKEN_FILE_NAME);
    }

    /**
     * 토큰을 파일에 저장한다.
     *
     * @throws ConfigException 저장 실패 시
     */
    public void saveToken(String token) {
        try {
            Files.createDirectories(tokenFile.getParent());
            Files.writeString(tokenFile, token, StandardCharsets.UTF_8);
            log.info("토큰 저장 완료");
        } catch (IOException e) {
            log.error("토큰 저장 실패", e);
            throw new ConfigException("토큰 저장 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 저장된 토큰을 로드한다.
     * 파일이 없거나 읽기 실패 시 Optional.empty()를 반환한다.
     */
    public Optional<String> loadToken() {
        if (!Files.exists(tokenFile)) {
            return Optional.empty();
        }
        try {
            String token = Files.readString(tokenFile, StandardCharsets.UTF_8).trim();
            return token.isEmpty() ? Optional.empty() : Optional.of(token);
        } catch (IOException e) {
            log.error("토큰 로드 실패", e);
            return Optional.empty();
        }
    }

    /**
     * 저장된 토큰을 삭제한다.
     */
    public void deleteToken() {
        try {
            if (Files.deleteIfExists(tokenFile)) {
                log.info("토큰 삭제 완료");
            }
        } catch (IOException e) {
            log.error("토큰 삭제 실패", e);
        }
    }
}
