package com.gitmini.util;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ErrorMessages 유틸리티 단위 테스트.
 * 다양한 git/GitHub 에러 패턴이 올바른 사용자 친화적 메시지로 변환되는지 검증한다.
 */
class ErrorMessagesTest {

    // ========== mapGitError ==========

    @Nested
    class MapGitErrorTest {

        @Test
        void null_메시지_안전_처리() {
            String result = ErrorMessages.mapGitError(null);
            assertNotNull(result);
            assertTrue(result.contains("알 수 없는"));
        }

        @Test
        void index_lock_에러() {
            String raw = "fatal: Unable to create '/repo/.git/index.lock': File exists.";
            String result = ErrorMessages.mapGitError(raw);
            assertTrue(result.contains("잠금 파일"));
            assertTrue(result.contains("index.lock"));
        }

        @Test
        void 권한_거부_에러() {
            String raw = "error: open('.git/objects/pack/...': Permission denied";
            String result = ErrorMessages.mapGitError(raw);
            assertTrue(result.contains("권한"));
        }

        @Test
        void access_denied_에러_Windows() {
            String raw = "fatal: open('file.txt'): Access is denied";
            String result = ErrorMessages.mapGitError(raw);
            assertTrue(result.contains("권한"));
        }

        @Test
        void git_저장소_아님() {
            String raw = "fatal: not a git repository (or any of the parent directories): .git";
            String result = ErrorMessages.mapGitError(raw);
            assertTrue(result.contains("Git 저장소"));
        }

        @Test
        void 디스크_공간_부족() {
            String raw = "error: No space left on device";
            String result = ErrorMessages.mapGitError(raw);
            assertTrue(result.contains("디스크"));
        }

        @Test
        void 저장소_손상() {
            String raw = "error: bad object 1234abcd";
            String result = ErrorMessages.mapGitError(raw);
            assertTrue(result.contains("손상"));
        }

        @Test
        void 타임아웃() {
            String raw = "Git 명령 타임아웃 (30초): git status";
            String result = ErrorMessages.mapGitError(raw);
            assertTrue(result.contains("시간이 초과"));
        }

        @Test
        void 빈_커밋() {
            String raw = "nothing to commit, working tree clean";
            String result = ErrorMessages.mapGitError(raw);
            assertTrue(result.contains("커밋할 변경"));
        }

        @Test
        void 브랜치_이미_존재() {
            String raw = "fatal: A branch named 'feature' already exists.";
            String result = ErrorMessages.mapGitError(raw);
            assertTrue(result.contains("이미 존재"));
        }

        @Test
        void 유효하지_않은_브랜치_이름() {
            String raw = "fatal: 'my branch' is not a valid branch name";
            String result = ErrorMessages.mapGitError(raw);
            assertTrue(result.contains("유효하지 않은 브랜치"));
        }

        @Test
        void 알려지지_않은_메시지는_원본_반환() {
            String raw = "some unknown git error xyz";
            String result = ErrorMessages.mapGitError(raw);
            assertEquals(raw, result);
        }

        @Test
        void 긴_메시지_축약() {
            String raw = "a".repeat(400);
            String result = ErrorMessages.mapGitError(raw);
            assertTrue(result.length() < 400);
            assertTrue(result.contains("축약"));
        }
    }

    // ========== mapRemoteError ==========

    @Nested
    class MapRemoteErrorTest {

        @Test
        void null_메시지_안전_처리() {
            String result = ErrorMessages.mapRemoteError(null);
            assertNotNull(result);
            assertTrue(result.contains("알 수 없는"));
        }

        @Test
        void 원격_없음() {
            String raw = "fatal: No configured push destination.";
            String result = ErrorMessages.mapRemoteError(raw);
            assertTrue(result.contains("원격 저장소가 없습니다"));
        }

        @Test
        void upstream_미설정() {
            String raw = "fatal: The current branch main has no upstream branch.";
            String result = ErrorMessages.mapRemoteError(raw);
            assertTrue(result.contains("원격 추적 정보"));
        }

        @Test
        void DNS_해석_실패() {
            String raw = "fatal: unable to access '...': Could not resolve host: github.com";
            String result = ErrorMessages.mapRemoteError(raw);
            assertTrue(result.contains("네트워크"));
            assertTrue(result.contains("호스트"));
        }

        @Test
        void 연결_거부() {
            String raw = "fatal: unable to access '...': Connection refused";
            String result = ErrorMessages.mapRemoteError(raw);
            assertTrue(result.contains("네트워크"));
        }

        @Test
        void 연결_타임아웃() {
            String raw = "fatal: unable to access '...': Connection timed out";
            String result = ErrorMessages.mapRemoteError(raw);
            assertTrue(result.contains("네트워크"));
        }

        @Test
        void SSL_인증서_오류() {
            String raw = "fatal: unable to access '...': SSL certificate problem: unable to get local issuer certificate";
            String result = ErrorMessages.mapRemoteError(raw);
            assertTrue(result.contains("SSL"));
        }

        @Test
        void 인증_실패() {
            String raw = "fatal: Authentication failed for 'https://github.com/owner/repo.git'";
            String result = ErrorMessages.mapRemoteError(raw);
            assertTrue(result.contains("인증"));
        }

        @Test
        void 레포_찾을_수_없음() {
            String raw = "remote: Repository not found.\nfatal: repository 'https://...' not found";
            String result = ErrorMessages.mapRemoteError(raw);
            assertTrue(result.contains("원격 저장소를 찾을 수 없습니다"));
        }

        @Test
        void non_fast_forward() {
            String raw = "! [rejected]        main -> main (non-fast-forward)";
            String result = ErrorMessages.mapRemoteError(raw);
            assertTrue(result.contains("Pull"));
        }

        @Test
        void Merge_충돌() {
            String raw = "Automatic merge failed; fix conflicts and then commit the result.";
            String result = ErrorMessages.mapRemoteError(raw);
            assertTrue(result.contains("병합 충돌"));
        }

        @Test
        void unable_to_access_일반() {
            String raw = "fatal: unable to access 'https://github.com/owner/repo.git/': Failed to connect";
            String result = ErrorMessages.mapRemoteError(raw);
            assertTrue(result.contains("접근할 수 없습니다") || result.contains("네트워크"));
        }

        @Test
        void 네트워크_타임아웃_GitExecutor() {
            String raw = "Git 명령 타임아웃 (120초): git push";
            String result = ErrorMessages.mapRemoteError(raw);
            assertTrue(result.contains("시간이 초과"));
        }

        @Test
        void index_lock은_일반_git_에러로_매핑() {
            String raw = "fatal: Unable to create '.git/index.lock': File exists.";
            String result = ErrorMessages.mapRemoteError(raw);
            assertTrue(result.contains("잠금 파일"));
        }

        @Test
        void connection_reset() {
            String raw = "fatal: unable to access '...': Recv failure: Connection reset by peer";
            // contains "connection reset"
            String result = ErrorMessages.mapRemoteError(raw);
            assertTrue(result.contains("네트워크"));
        }

        @Test
        void Windows_logon_failed() {
            String raw = "Logon failed, use ctrl+c to cancel basic credential prompt.";
            String result = ErrorMessages.mapRemoteError(raw);
            assertTrue(result.contains("인증"));
        }
    }

    // ========== mapBranchError ==========

    @Nested
    class MapBranchErrorTest {

        @Test
        void null_메시지_안전_처리() {
            String result = ErrorMessages.mapBranchError(null);
            assertNotNull(result);
            assertTrue(result.contains("알 수 없는"));
        }

        @Test
        void 커밋하지_않은_변경() {
            String raw = "error: Your local changes to the following files would be overwritten by checkout:\n  src/Main.java\nPlease commit your changes or stash them before you switch branches.";
            String result = ErrorMessages.mapBranchError(raw);
            assertTrue(result.contains("커밋하지 않은 변경"));
        }

        @Test
        void 브랜치_찾을_수_없음() {
            String raw = "error: pathspec 'nonexistent' did not match any file(s) known to git";
            String result = ErrorMessages.mapBranchError(raw);
            assertTrue(result.contains("찾을 수 없습니다"));
        }

        @Test
        void index_lock은_일반_git_에러로_매핑() {
            String raw = "fatal: Unable to create '.git/index.lock': File exists.";
            String result = ErrorMessages.mapBranchError(raw);
            assertTrue(result.contains("잠금 파일"));
        }

        @Test
        void 알려지지_않은_메시지는_원본_반환() {
            String raw = "some unknown branch error";
            String result = ErrorMessages.mapBranchError(raw);
            assertEquals(raw, result);
        }
    }

    // ========== truncate ==========

    @Nested
    class TruncateTest {

        @Test
        void null_안전() {
            assertEquals("", ErrorMessages.truncate(null));
        }

        @Test
        void 짧은_메시지_그대로() {
            assertEquals("short msg", ErrorMessages.truncate("short msg"));
        }

        @Test
        void 긴_메시지_축약() {
            String longMsg = "x".repeat(500);
            String result = ErrorMessages.truncate(longMsg);
            assertTrue(result.length() < 500);
            assertTrue(result.contains("축약"));
        }

        @Test
        void 정확히_300자_축약_안_함() {
            String msg300 = "a".repeat(300);
            String result = ErrorMessages.truncate(msg300);
            assertEquals(msg300, result);
        }

        @Test
        void _301자_축약() {
            String msg301 = "a".repeat(301);
            String result = ErrorMessages.truncate(msg301);
            assertTrue(result.contains("축약"));
        }
    }
}
