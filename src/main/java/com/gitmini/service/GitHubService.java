package com.gitmini.service;

import com.gitmini.config.TokenManager;
import com.gitmini.exception.GitHubApiException;
import com.gitmini.model.GitHubRepo;
import com.gitmini.model.GitHubUser;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * GitHub REST API 클라이언트.
 * <p>
 * {@link java.net.http.HttpClient} 기반으로 GitHub API를 호출한다.
 * Java 표준 라이브러리만 사용하여 추가 의존성이 없다.
 * </p>
 *
 * <h3>인증</h3>
 * <p>
 * {@link TokenManager}에서 PAT(Personal Access Token)를 로드하여
 * {@code Authorization: Bearer <token>} 헤더로 전달한다.
 * 토큰이 없으면 {@link GitHubApiException}을 던진다 (인증 필수).
 * </p>
 *
 * <h3>에러 처리</h3>
 * <ul>
 *   <li>401 Unauthorized → {@link GitHubApiException} (isUnauthorized)</li>
 *   <li>403 Forbidden / Rate Limit → {@link GitHubApiException} (isForbidden)</li>
 *   <li>기타 4xx/5xx → {@link GitHubApiException} (statusCode 포함)</li>
 *   <li>네트워크 오류 / 타임아웃 → {@link GitHubApiException} (isNetworkError)</li>
 * </ul>
 *
 * <h3>의존성 방향</h3>
 * <pre>
 * Controller → GitHubService → TokenManager
 *                  ↓
 *                model (GitHubUser, GitHubRepo)
 * </pre>
 */
public class GitHubService {

    private static final Logger log = LoggerFactory.getLogger(GitHubService.class);

    private static final String API_BASE_URL = "https://api.github.com";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final String ACCEPT_HEADER = "application/vnd.github.v3+json";
    private static final String USER_AGENT = "GitMini/1.0";

    /** 레포 목록 조회 시 페이지당 최대 항목 수 */
    private static final int REPOS_PER_PAGE = 100;

    private final HttpClient httpClient;
    private final TokenManager tokenManager;
    private final Gson gson;

    /**
     * 프로덕션용 생성자.
     *
     * @param tokenManager PAT 관리자
     */
    public GitHubService(TokenManager tokenManager) {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build(),
                tokenManager
        );
    }

    /**
     * 테스트용 생성자 — HttpClient를 주입받는다.
     *
     * @param httpClient   HTTP 클라이언트 (테스트 시 모킹)
     * @param tokenManager PAT 관리자
     */
    GitHubService(HttpClient httpClient, TokenManager tokenManager) {
        this.httpClient = httpClient;
        this.tokenManager = tokenManager;
        this.gson = new Gson();
    }

    // ========== Public API ==========

    /**
     * 저장된 PAT로 GitHub 인증을 검증하고 사용자 정보를 반환한다.
     * <p>
     * {@code GET /user} API를 호출한다.
     * 토큰이 유효하면 사용자 정보를, 유효하지 않으면 예외를 던진다.
     * </p>
     *
     * @return 인증된 GitHub 사용자 정보
     * @throws GitHubApiException 토큰이 없거나, 인증 실패, 네트워크 오류 시
     */
    public GitHubUser validateToken() {
        String token = requireToken();

        HttpResponse<String> response = executeGet("/user", token);
        requireSuccess(response, "토큰 검증");

        return parseUser(response.body());
    }

    /**
     * 주어진 토큰으로 GitHub 인증을 검증한다.
     * <p>
     * 설정 UI에서 토큰 저장 전에 유효성을 확인할 때 사용한다.
     * {@link TokenManager}에 저장된 토큰이 아닌, 직접 전달받은 토큰을 사용한다.
     * </p>
     *
     * @param token 검증할 PAT
     * @return 인증된 GitHub 사용자 정보
     * @throws GitHubApiException 인증 실패, 네트워크 오류 시
     */
    public GitHubUser validateToken(String token) {
        if (token == null || token.isBlank()) {
            throw new GitHubApiException("토큰이 비어 있습니다", 401, "");
        }

        HttpResponse<String> response = executeGet("/user", token);
        requireSuccess(response, "토큰 검증");

        return parseUser(response.body());
    }

    /**
     * 인증된 사용자의 레포지토리 목록을 조회한다.
     * <p>
     * {@code GET /user/repos} API를 호출한다.
     * 최대 100개를 반환하며, 최근 push 순으로 정렬한다.
     * </p>
     *
     * @return 레포 목록
     * @throws GitHubApiException 인증 실패, 네트워크 오류 시
     */
    public List<GitHubRepo> listRepositories() {
        String token = requireToken();

        String path = "/user/repos?sort=pushed&direction=desc&per_page=" + REPOS_PER_PAGE;
        HttpResponse<String> response = executeGet(path, token);
        requireSuccess(response, "레포 목록 조회");

        return parseRepoList(response.body());
    }

    /**
     * GitHub에 새 레포지토리를 생성한다.
     * <p>
     * {@code POST /user/repos} API를 호출한다.
     * 생성된 레포의 정보를 반환하며, Clone URL을 포함한다.
     * </p>
     *
     * @param name        레포 이름 (예: "my-project")
     * @param description 레포 설명 (nullable)
     * @param isPrivate   Private 레포 여부
     * @param autoInit    README.md로 초기화할지 여부
     * @return 생성된 레포 정보
     * @throws GitHubApiException 토큰 없음, 인증 실패, 이름 중복 등
     */
    public GitHubRepo createRepository(String name, String description,
                                       boolean isPrivate, boolean autoInit) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("레포 이름이 비어 있습니다");
        }

        String token = requireToken();

        JsonObject body = new JsonObject();
        body.addProperty("name", name.trim());
        if (description != null && !description.isBlank()) {
            body.addProperty("description", description.trim());
        }
        body.addProperty("private", isPrivate);
        body.addProperty("auto_init", autoInit);

        HttpResponse<String> response = executePost("/user/repos", token, gson.toJson(body));
        requireSuccess(response, "레포 생성");

        return parseRepo(response.body());
    }

    /**
     * 토큰이 저장되어 있는지 확인한다.
     *
     * @return 토큰이 존재하면 true
     */
    public boolean hasToken() {
        return tokenManager.loadToken().isPresent();
    }

    /**
     * 저장된 토큰을 반환한다.
     *
     * @return 토큰 (없으면 Optional.empty)
     */
    public Optional<String> getToken() {
        return tokenManager.loadToken();
    }

    // ========== HTTP 실행 ==========

    /**
     * GET 요청을 실행한다.
     *
     * @param path  API 경로 (예: "/user", "/user/repos?per_page=100")
     * @param token PAT
     * @return HTTP 응답
     * @throws GitHubApiException 네트워크 오류, 타임아웃 시
     */
    private HttpResponse<String> executeGet(String path, String token) {
        URI uri = URI.create(API_BASE_URL + path);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", ACCEPT_HEADER)
                .header("User-Agent", USER_AGENT)
                .GET();

        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }

        HttpRequest request = builder.build();
        log.debug("GitHub API 요청: GET {}", path);

        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            log.debug("GitHub API 응답: {} ({}자)", response.statusCode(),
                    response.body() != null ? response.body().length() : 0);
            return response;
        } catch (IOException e) {
            log.error("GitHub API 네트워크 오류: GET {}", path, e);
            throw new GitHubApiException(
                    "GitHub 서버에 연결할 수 없습니다: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("GitHub API 요청 중단: GET {}", path, e);
            throw new GitHubApiException(
                    "GitHub API 요청이 중단되었습니다", e);
        }
    }

    /**
     * POST 요청을 실행한다.
     *
     * @param path  API 경로
     * @param token PAT
     * @param body  JSON 요청 본문
     * @return HTTP 응답
     * @throws GitHubApiException 네트워크 오류, 타임아웃 시
     */
    HttpResponse<String> executePost(String path, String token, String body) {
        if (token == null || token.isBlank()) {
            throw new GitHubApiException("POST 요청에 토큰이 필요합니다", 401, "");
        }

        URI uri = URI.create(API_BASE_URL + path);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", ACCEPT_HEADER)
                .header("User-Agent", USER_AGENT)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        log.debug("GitHub API 요청: POST {}", path);

        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            log.debug("GitHub API 응답: {} ({}자)", response.statusCode(),
                    response.body() != null ? response.body().length() : 0);
            return response;
        } catch (IOException e) {
            log.error("GitHub API 네트워크 오류: POST {}", path, e);
            throw new GitHubApiException(
                    "GitHub 서버에 연결할 수 없습니다: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("GitHub API 요청 중단: POST {}", path, e);
            throw new GitHubApiException(
                    "GitHub API 요청이 중단되었습니다", e);
        }
    }

    // ========== 응답 검증 ==========

    /**
     * HTTP 응답이 성공(2xx)인지 확인하고, 실패 시 적절한 예외를 던진다.
     */
    private void requireSuccess(HttpResponse<String> response, String operation) {
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return; // 성공
        }

        String body = response.body() != null ? response.body() : "";
        String message = switch (status) {
            case 401 -> "GitHub 인증에 실패했습니다. 토큰이 유효한지 확인해 주세요.";
            case 403 -> parseForbiddenMessage(body);
            case 404 -> operation + " 실패: 리소스를 찾을 수 없습니다.";
            case 422 -> operation + " 실패: " + parseErrorMessage(body);
            default -> operation + " 실패 (HTTP " + status + "): " + parseErrorMessage(body);
        };

        log.warn("GitHub API 실패: {} — HTTP {} — {}", operation, status, message);
        throw new GitHubApiException(message, status, body);
    }

    // ========== JSON 파싱 ==========

    /**
     * /user 응답 JSON을 GitHubUser로 변환한다.
     */
    private GitHubUser parseUser(String json) {
        try {
            JsonObject obj = gson.fromJson(json, JsonObject.class);
            String login = getStringOrNull(obj, "login");
            if (login == null || login.isBlank()) {
                throw new GitHubApiException(
                        "GitHub 응답에 login 필드가 없습니다", 0, json);
            }
            return new GitHubUser(
                    login,
                    getStringOrNull(obj, "name"),
                    getStringOrNull(obj, "avatar_url")
            );
        } catch (JsonSyntaxException e) {
            log.error("GitHub 사용자 정보 파싱 실패", e);
            throw new GitHubApiException("GitHub 응답 파싱 실패", e);
        }
    }

    /**
     * 단일 레포 응답 JSON을 GitHubRepo로 변환한다 (레포 생성 응답용).
     */
    private GitHubRepo parseRepo(String json) {
        try {
            JsonObject obj = gson.fromJson(json, JsonObject.class);
            return new GitHubRepo(
                    getStringOrNull(obj, "full_name"),
                    getStringOrNull(obj, "clone_url"),
                    getStringOrNull(obj, "description"),
                    obj.has("private") && obj.get("private").getAsBoolean(),
                    getStringOrNull(obj, "default_branch")
            );
        } catch (JsonSyntaxException e) {
            log.error("GitHub 레포 정보 파싱 실패", e);
            throw new GitHubApiException("GitHub 응답 파싱 실패", e);
        }
    }

    /**
     * /user/repos 응답 JSON 배열을 GitHubRepo 리스트로 변환한다.
     */
    private List<GitHubRepo> parseRepoList(String json) {
        try {
            JsonArray array = gson.fromJson(json, JsonArray.class);
            List<GitHubRepo> repos = new ArrayList<>();

            for (JsonElement element : array) {
                if (!element.isJsonObject()) {
                    log.warn("레포 목록에 비-객체 요소 무시: {}", element);
                    continue;
                }
                JsonObject obj = element.getAsJsonObject();
                repos.add(new GitHubRepo(
                        getStringOrNull(obj, "full_name"),
                        getStringOrNull(obj, "clone_url"),
                        getStringOrNull(obj, "description"),
                        obj.has("private") && obj.get("private").getAsBoolean(),
                        getStringOrNull(obj, "default_branch")
                ));
            }

            log.info("GitHub 레포 목록: {}개 조회", repos.size());
            return repos;
        } catch (JsonSyntaxException | IllegalStateException e) {
            log.error("GitHub 레포 목록 파싱 실패", e);
            throw new GitHubApiException("GitHub 응답 파싱 실패", e);
        }
    }

    /**
     * 403 응답의 에러 메시지를 파싱한다.
     * Rate Limit 초과인 경우 별도 메시지를 반환한다.
     */
    private String parseForbiddenMessage(String body) {
        if (body.toLowerCase().contains("rate limit")) {
            return "GitHub API 호출 한도를 초과했습니다. 잠시 후 다시 시도해 주세요.";
        }
        return "GitHub 접근이 거부되었습니다. 토큰 권한을 확인해 주세요.";
    }

    /**
     * GitHub API 에러 응답에서 "message" 필드를 추출한다.
     */
    private String parseErrorMessage(String body) {
        try {
            JsonObject obj = gson.fromJson(body, JsonObject.class);
            if (obj != null && obj.has("message")) {
                return obj.get("message").getAsString();
            }
        } catch (JsonSyntaxException ignored) {
            // JSON이 아닌 경우 무시
        }
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
    }

    /**
     * JsonObject에서 문자열 값을 안전하게 추출한다.
     */
    private String getStringOrNull(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return null;
    }

    // ========== 유틸리티 ==========

    /**
     * 저장된 토큰을 로드하고, 없으면 예외를 던진다.
     */
    private String requireToken() {
        return tokenManager.loadToken().orElseThrow(() ->
                new GitHubApiException(
                        "GitHub 토큰이 설정되지 않았습니다. 설정에서 토큰을 입력해 주세요.",
                        401, ""));
    }
}
