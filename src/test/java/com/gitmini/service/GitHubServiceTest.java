package com.gitmini.service;

import com.gitmini.config.TokenManager;
import com.gitmini.exception.GitHubApiException;
import com.gitmini.model.GitHubRepo;
import com.gitmini.model.GitHubUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * GitHubService 단위 테스트.
 * <p>
 * HttpClient를 모킹하여 네트워크 없이 서비스 로직을 검증한다.
 * 실제 GitHub API는 호출하지 않는다.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class GitHubServiceTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private TokenManager tokenManager;

    @Mock
    @SuppressWarnings("unchecked")
    private HttpResponse<String> httpResponse;

    private GitHubService service;

    private static final String VALID_TOKEN = "ghp_test1234567890";

    @BeforeEach
    void setUp() {
        service = new GitHubService(httpClient, tokenManager);
    }

    // ========== validateToken() — 저장된 토큰 ==========

    @Test
    void validateToken_성공_사용자정보_반환() throws Exception {
        when(tokenManager.loadToken()).thenReturn(Optional.of(VALID_TOKEN));
        mockResponse(200, USER_JSON);

        GitHubUser user = service.validateToken();

        assertNotNull(user);
        assertEquals("octocat", user.login());
        assertEquals("The Octocat", user.name());
        assertEquals("https://avatars.githubusercontent.com/u/1?v=4", user.avatarUrl());
    }

    @Test
    void validateToken_토큰없음_예외() {
        when(tokenManager.loadToken()).thenReturn(Optional.empty());

        GitHubApiException ex = assertThrows(GitHubApiException.class,
                () -> service.validateToken());
        assertTrue(ex.isUnauthorized());
        assertTrue(ex.getMessage().contains("토큰이 설정되지 않았습니다"));
    }

    @Test
    void validateToken_401_인증실패() throws Exception {
        when(tokenManager.loadToken()).thenReturn(Optional.of(VALID_TOKEN));
        mockResponse(401, "{\"message\":\"Bad credentials\"}");

        GitHubApiException ex = assertThrows(GitHubApiException.class,
                () -> service.validateToken());
        assertTrue(ex.isUnauthorized());
        assertEquals(401, ex.getStatusCode());
    }

    @Test
    void validateToken_403_권한부족() throws Exception {
        when(tokenManager.loadToken()).thenReturn(Optional.of(VALID_TOKEN));
        mockResponse(403, "{\"message\":\"forbidden\"}");

        GitHubApiException ex = assertThrows(GitHubApiException.class,
                () -> service.validateToken());
        assertTrue(ex.isForbidden());
    }

    @Test
    void validateToken_403_rateLimitExceeded() throws Exception {
        when(tokenManager.loadToken()).thenReturn(Optional.of(VALID_TOKEN));
        mockResponse(403, "{\"message\":\"API rate limit exceeded\"}");

        GitHubApiException ex = assertThrows(GitHubApiException.class,
                () -> service.validateToken());
        assertTrue(ex.isForbidden());
        assertTrue(ex.getMessage().contains("호출 한도"));
    }

    @Test
    void validateToken_네트워크오류() throws Exception {
        when(tokenManager.loadToken()).thenReturn(Optional.of(VALID_TOKEN));
        when(httpClient.send(any(HttpRequest.class), any()))
                .thenThrow(new IOException("Connection refused"));

        GitHubApiException ex = assertThrows(GitHubApiException.class,
                () -> service.validateToken());
        assertTrue(ex.isNetworkError());
        assertTrue(ex.getMessage().contains("연결할 수 없습니다"));
    }

    @Test
    void validateToken_인터럽트() throws Exception {
        when(tokenManager.loadToken()).thenReturn(Optional.of(VALID_TOKEN));
        when(httpClient.send(any(HttpRequest.class), any()))
                .thenThrow(new InterruptedException("interrupted"));

        GitHubApiException ex = assertThrows(GitHubApiException.class,
                () -> service.validateToken());
        assertTrue(ex.isNetworkError());

        // InterruptedException 후 인터럽트 상태가 복원되었는지 확인
        assertTrue(Thread.currentThread().isInterrupted());
        // 클린업: 다른 테스트에 영향 주지 않도록 인터럽트 플래그 해제
        Thread.interrupted();
    }

    // ========== validateToken(String) — 직접 전달 ==========

    @Test
    void validateToken_직접토큰_성공() throws Exception {
        mockResponse(200, USER_JSON);

        GitHubUser user = service.validateToken(VALID_TOKEN);

        assertEquals("octocat", user.login());
        verify(tokenManager, never()).loadToken(); // TokenManager 미사용
    }

    @Test
    void validateToken_직접토큰_빈문자열_예외() {
        GitHubApiException ex = assertThrows(GitHubApiException.class,
                () -> service.validateToken(""));
        assertTrue(ex.isUnauthorized());
    }

    @Test
    void validateToken_직접토큰_null_예외() {
        GitHubApiException ex = assertThrows(GitHubApiException.class,
                () -> service.validateToken((String) null));
        assertTrue(ex.isUnauthorized());
    }

    // ========== listRepositories() ==========

    @Test
    void listRepositories_성공() throws Exception {
        when(tokenManager.loadToken()).thenReturn(Optional.of(VALID_TOKEN));
        mockResponse(200, REPOS_JSON);

        List<GitHubRepo> repos = service.listRepositories();

        assertEquals(2, repos.size());

        GitHubRepo first = repos.get(0);
        assertEquals("octocat/Hello-World", first.fullName());
        assertEquals("https://github.com/octocat/Hello-World.git", first.cloneUrl());
        assertEquals("My first repository on GitHub!", first.description());
        assertFalse(first.isPrivate());
        assertEquals("main", first.defaultBranch());

        GitHubRepo second = repos.get(1);
        assertEquals("octocat/private-repo", second.fullName());
        assertTrue(second.isPrivate());
    }

    @Test
    void listRepositories_빈목록() throws Exception {
        when(tokenManager.loadToken()).thenReturn(Optional.of(VALID_TOKEN));
        mockResponse(200, "[]");

        List<GitHubRepo> repos = service.listRepositories();

        assertTrue(repos.isEmpty());
    }

    @Test
    void listRepositories_토큰없음_예외() {
        when(tokenManager.loadToken()).thenReturn(Optional.empty());

        assertThrows(GitHubApiException.class, () -> service.listRepositories());
    }

    @Test
    void listRepositories_401_예외() throws Exception {
        when(tokenManager.loadToken()).thenReturn(Optional.of(VALID_TOKEN));
        mockResponse(401, "{\"message\":\"Bad credentials\"}");

        GitHubApiException ex = assertThrows(GitHubApiException.class,
                () -> service.listRepositories());
        assertTrue(ex.isUnauthorized());
    }

    // ========== hasToken() ==========

    @Test
    void hasToken_토큰있음() {
        when(tokenManager.loadToken()).thenReturn(Optional.of(VALID_TOKEN));
        assertTrue(service.hasToken());
    }

    @Test
    void hasToken_토큰없음() {
        when(tokenManager.loadToken()).thenReturn(Optional.empty());
        assertFalse(service.hasToken());
    }

    // ========== createRepository() ==========

    @Test
    void createRepository_성공() throws Exception {
        when(tokenManager.loadToken()).thenReturn(Optional.of(VALID_TOKEN));
        mockResponse(201, """
                {"full_name":"octocat/new-repo","clone_url":"https://github.com/octocat/new-repo.git",
                 "description":"A new repo","private":true,"default_branch":"main"}
                """);

        GitHubRepo repo = service.createRepository("new-repo", "A new repo", true, true);

        assertEquals("octocat/new-repo", repo.fullName());
        assertEquals("https://github.com/octocat/new-repo.git", repo.cloneUrl());
        assertTrue(repo.isPrivate());
    }

    @Test
    void createRepository_이름없음_예외() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createRepository("", null, false, false));
    }

    @Test
    void createRepository_토큰없음_예외() {
        when(tokenManager.loadToken()).thenReturn(Optional.empty());
        assertThrows(GitHubApiException.class,
                () -> service.createRepository("test", null, false, false));
    }

    @Test
    void createRepository_422_이름중복() throws Exception {
        when(tokenManager.loadToken()).thenReturn(Optional.of(VALID_TOKEN));
        mockResponse(422, "{\"message\":\"Repository creation failed.\",\"errors\":[{\"message\":\"name already exists on this account\"}]}");

        GitHubApiException ex = assertThrows(GitHubApiException.class,
                () -> service.createRepository("existing-repo", null, false, false));
        assertEquals(422, ex.getStatusCode());
    }

    // ========== getToken() ==========

    @Test
    void getToken_토큰반환() {
        when(tokenManager.loadToken()).thenReturn(Optional.of(VALID_TOKEN));
        assertEquals(Optional.of(VALID_TOKEN), service.getToken());
    }

    // ========== 모델 테스트 ==========

    @Test
    void GitHubUser_displayName_이름있음() {
        GitHubUser user = new GitHubUser("octocat", "The Octocat", null);
        assertEquals("The Octocat (octocat)", user.displayName());
    }

    @Test
    void GitHubUser_displayName_이름없음() {
        GitHubUser user = new GitHubUser("octocat", null, null);
        assertEquals("octocat", user.displayName());
    }

    @Test
    void GitHubUser_displayName_빈이름() {
        GitHubUser user = new GitHubUser("octocat", "  ", null);
        assertEquals("octocat", user.displayName());
    }

    @Test
    void GitHubRepo_displayName_public() {
        GitHubRepo repo = new GitHubRepo("octocat/Hello-World",
                "https://github.com/octocat/Hello-World.git",
                "desc", false, "main");
        assertEquals("octocat/Hello-World", repo.displayName());
    }

    @Test
    void GitHubRepo_displayName_private() {
        GitHubRepo repo = new GitHubRepo("octocat/secret",
                "https://github.com/octocat/secret.git",
                null, true, "main");
        assertTrue(repo.displayName().contains("octocat/secret"));
        assertTrue(repo.displayName().contains("\uD83D\uDD12"));
    }

    // ========== 응답 파싱 엣지 케이스 ==========

    @Test
    void validateToken_name이_null인_사용자() throws Exception {
        when(tokenManager.loadToken()).thenReturn(Optional.of(VALID_TOKEN));
        mockResponse(200, """
                {"login":"bot","name":null,"avatar_url":null}
                """);

        GitHubUser user = service.validateToken();

        assertEquals("bot", user.login());
        assertNull(user.name());
        assertNull(user.avatarUrl());
    }

    @Test
    void listRepositories_description_null인_레포() throws Exception {
        when(tokenManager.loadToken()).thenReturn(Optional.of(VALID_TOKEN));
        mockResponse(200, """
                [{"full_name":"a/b","clone_url":"https://github.com/a/b.git",
                  "description":null,"private":false,"default_branch":"main"}]
                """);

        List<GitHubRepo> repos = service.listRepositories();

        assertEquals(1, repos.size());
        assertNull(repos.get(0).description());
    }

    @Test
    void validateToken_잘못된JSON_예외() throws Exception {
        when(tokenManager.loadToken()).thenReturn(Optional.of(VALID_TOKEN));
        mockResponse(200, "not json");

        assertThrows(GitHubApiException.class, () -> service.validateToken());
    }

    @Test
    void validateToken_서버오류_500() throws Exception {
        when(tokenManager.loadToken()).thenReturn(Optional.of(VALID_TOKEN));
        mockResponse(500, "{\"message\":\"Internal Server Error\"}");

        GitHubApiException ex = assertThrows(GitHubApiException.class,
                () -> service.validateToken());
        assertEquals(500, ex.getStatusCode());
    }

    @Test
    void validateToken_422_처리() throws Exception {
        when(tokenManager.loadToken()).thenReturn(Optional.of(VALID_TOKEN));
        mockResponse(422, "{\"message\":\"Validation Failed\"}");

        GitHubApiException ex = assertThrows(GitHubApiException.class,
                () -> service.validateToken());
        assertEquals(422, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("Validation Failed"));
    }

    // ========== 헬퍼 ==========

    @SuppressWarnings("unchecked")
    private void mockResponse(int statusCode, String body) throws Exception {
        when(httpResponse.statusCode()).thenReturn(statusCode);
        when(httpResponse.body()).thenReturn(body);
        when(httpClient.send(any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);
    }

    // ========== 테스트 데이터 ==========

    private static final String USER_JSON = """
            {
              "login": "octocat",
              "id": 1,
              "name": "The Octocat",
              "avatar_url": "https://avatars.githubusercontent.com/u/1?v=4",
              "bio": "GitHub mascot",
              "public_repos": 8,
              "followers": 100
            }
            """;

    private static final String REPOS_JSON = """
            [
              {
                "full_name": "octocat/Hello-World",
                "clone_url": "https://github.com/octocat/Hello-World.git",
                "description": "My first repository on GitHub!",
                "private": false,
                "default_branch": "main"
              },
              {
                "full_name": "octocat/private-repo",
                "clone_url": "https://github.com/octocat/private-repo.git",
                "description": "Secret project",
                "private": true,
                "default_branch": "develop"
              }
            ]
            """;
}
