package com.gitmini.util;

/**
 * Git/GitHub 에러 메시지를 사용자 친화적인 한국어로 변환하는 유틸리티.
 * <p>
 * Controller가 서비스 계층에서 올라온 raw 에러 메시지를 사용자에게
 * 표시할 때, 이 클래스를 통해 일관된 안내 메시지로 변환한다.
 * </p>
 *
 * <h3>분류</h3>
 * <ul>
 *   <li>{@link #mapGitError(String)} — 일반 git CLI 에러 (stage, commit, checkout 등)</li>
 *   <li>{@link #mapRemoteError(String)} — 원격 작업 에러 (push, pull, fetch, clone)</li>
 *   <li>{@link #mapBranchError(String)} — 브랜치 전환 에러</li>
 * </ul>
 */
public final class ErrorMessages {

    /** 에러 메시지 최대 표시 길이 (초과 시 축약). */
    private static final int MAX_MESSAGE_LENGTH = 300;

    private ErrorMessages() {
        // 유틸리티 클래스
    }

    // ========== 일반 Git 에러 ==========

    /**
     * 일반 git CLI 에러 메시지를 사용자 친화적 한국어로 변환한다.
     * <p>
     * stage, unstage, commit, discard, branch create 등
     * 로컬 git 명령 실패 시 사용한다.
     * </p>
     *
     * @param rawMessage git 에러 원본 메시지 (null 안전)
     * @return 사용자 친화적 한국어 메시지
     */
    public static String mapGitError(String rawMessage) {
        if (rawMessage == null) return "알 수 없는 오류가 발생했습니다.";
        String msg = rawMessage.trim().toLowerCase();

        // ── index.lock (다른 git 프로세스 또는 비정상 종료) ──
        if (msg.contains("index.lock") || (msg.contains("unable to create") && msg.contains(".lock"))) {
            return "Git 잠금 파일(index.lock)이 존재합니다.\n\n"
                    + "다른 Git 프로그램이 실행 중이거나, 이전 작업이 비정상 종료되었을 수 있습니다.\n"
                    + "문제가 지속되면 레포의 .git/index.lock 파일을 수동으로 삭제하세요.";
        }

        // ── 권한 오류 (파일 시스템) ──
        if (msg.contains("permission denied") || msg.contains("access is denied")
                || msg.contains("access denied")) {
            return "파일 접근 권한이 부족합니다.\n\n"
                    + "해당 파일이나 폴더에 대한 읽기/쓰기 권한을 확인하세요.";
        }

        // ── 유효하지 않은 Git 저장소 ──
        if (msg.contains("not a git repository") || msg.contains("fatal: not a git repository")) {
            return "유효한 Git 저장소가 아닙니다.\n\n"
                    + "폴더에 .git 디렉토리가 있는지 확인하세요.";
        }

        // ── 디스크 공간 부족 ──
        if (msg.contains("no space left on device") || msg.contains("disk full")
                || msg.contains("not enough space") || msg.contains("insufficient disk space")) {
            return "디스크 공간이 부족합니다.\n\n"
                    + "불필요한 파일을 정리하고 다시 시도하세요.";
        }

        // ── 저장소 손상 ──
        if (msg.contains("bad object") || msg.contains("corrupt")
                || msg.contains("broken") || msg.contains("missing object")) {
            return "Git 저장소가 손상되었을 수 있습니다.\n\n"
                    + "터미널에서 'git fsck'로 확인하거나, 원격에서 다시 Clone하세요.";
        }

        // ── 타임아웃 ──
        if (msg.contains("타임아웃") || msg.contains("timed out") || msg.contains("timeout")) {
            return "작업 시간이 초과되었습니다.\n\n"
                    + "네트워크 연결을 확인하거나, 잠시 후 다시 시도하세요.";
        }

        // ── 빈 커밋 ──
        if (msg.contains("nothing to commit") || msg.contains("nothing added to commit")) {
            return "커밋할 변경 사항이 없습니다.";
        }

        // ── 이미 존재하는 브랜치 ──
        if (msg.contains("already exists")) {
            return "같은 이름의 브랜치가 이미 존재합니다.";
        }

        // ── 유효하지 않은 브랜치 이름 ──
        if (msg.contains("is not a valid branch name") || msg.contains("invalid branch name")) {
            return "유효하지 않은 브랜치 이름입니다.\n\n"
                    + "공백, 특수문자(~, ^, :, \\, ..) 등은 사용할 수 없습니다.";
        }

        // ── 너무 긴 메시지 축약 ──
        return truncate(rawMessage);
    }

    // ========== 원격(Remote) 작업 에러 ==========

    /**
     * Push/Pull/Fetch/Clone 등 원격 작업 에러 메시지를 사용자 친화적 한국어로 변환한다.
     *
     * @param rawMessage git 에러 원본 메시지 (null 안전)
     * @return 사용자 친화적 한국어 메시지
     */
    public static String mapRemoteError(String rawMessage) {
        if (rawMessage == null) return "알 수 없는 오류가 발생했습니다.";
        String msg = rawMessage.trim().toLowerCase();

        // ── 원격 저장소 자체가 없음 ──
        if (msg.contains("no configured push destination")
                || msg.contains("does not have any remotes")
                || msg.contains("no remote repository specified")) {
            return "이 레포에는 원격 저장소가 없습니다.\n\n"
                    + "터미널에서 'git remote add origin <URL>'로 원격을 추가하세요.";
        }

        // ── upstream 브랜치 미설정 ──
        if (msg.contains("no upstream")
                || (msg.contains("upstream") && msg.contains("not set"))
                || msg.contains("no tracking information")
                || msg.contains("specify which branch you want to merge")
                || msg.contains("현재 브랜치에 위쪽 추적 브랜치가 없습니다")) {
            return "현재 브랜치에 원격 추적 정보가 없습니다.\n\n"
                    + "터미널에서 아래 명령으로 설정할 수 있습니다:\n"
                    + "  git push -u origin <브랜치이름>";
        }

        // ── DNS / 호스트 찾을 수 없음 ──
        if (msg.contains("could not resolve host") || msg.contains("unknown host")
                || msg.contains("name or service not known")
                || msg.contains("getaddrinfo failed")) {
            return "네트워크 연결을 확인하세요.\n(호스트를 찾을 수 없습니다)";
        }

        // ── 연결 거부 / 타임아웃 ──
        if (msg.contains("connection refused") || msg.contains("timed out")
                || msg.contains("connection timed out") || msg.contains("connection reset")) {
            return "네트워크 연결을 확인하세요.\n(연결이 거부되었거나 시간이 초과되었습니다)";
        }

        // ── SSL 인증서 오류 ──
        if (msg.contains("ssl certificate") || msg.contains("ssl_error")
                || (msg.contains("unable to access") && msg.contains("ssl"))) {
            return "SSL 인증서 오류가 발생했습니다.\n\n"
                    + "네트워크 환경(VPN, 프록시 등)을 확인하세요.";
        }

        // ── 인증 실패 ──
        if (msg.contains("authentication failed") || msg.contains("permission denied")
                || msg.contains("access denied") || msg.contains("invalid credentials")
                || msg.contains("logon failed")) {
            return "인증에 실패했습니다.\n자격 증명(비밀번호·토큰)을 확인하세요.";
        }

        // ── 원격 레포를 찾을 수 없음 ──
        if (msg.contains("repository not found") || msg.contains("remote: not found")
                || msg.contains("could not read from remote repository")) {
            return "원격 저장소를 찾을 수 없습니다.\n\n"
                    + "URL이 정확한지, 접근 권한이 있는지 확인하세요.";
        }

        // ── 충돌 / non-fast-forward ──
        if (msg.contains("rejected") && msg.contains("non-fast-forward")) {
            return "원격에 새 커밋이 있습니다.\n먼저 Pull 한 뒤 다시 Push 하세요.";
        }

        // ── Pull 시 Merge 충돌 ──
        if (msg.contains("merge conflict") || msg.contains("automatic merge failed")
                || msg.contains("fix conflicts")) {
            return "병합 충돌이 발생했습니다.\n\n"
                    + "충돌 파일을 수정하고, 변경 사항을 Stage한 뒤 커밋하세요.";
        }

        // ── fatal: unable to access ──
        if (msg.contains("unable to access")) {
            return "원격 저장소에 접근할 수 없습니다.\n\n"
                    + "네트워크 연결과 URL을 확인하세요.";
        }

        // ── 타임아웃 (GitExecutor에서 올라온) ──
        if (msg.contains("타임아웃") || msg.contains("timeout")) {
            return "네트워크 작업 시간이 초과되었습니다.\n\n"
                    + "연결 상태를 확인하고 다시 시도하세요.";
        }

        // ── 일반 git 에러도 체크 ──
        String gitMapped = mapGitError(rawMessage);
        if (!gitMapped.equals(truncate(rawMessage))) {
            return gitMapped; // 매칭된 일반 git 에러가 있으면 사용
        }

        // ── 너무 긴 메시지 축약 ──
        return truncate(rawMessage);
    }

    // ========== 브랜치 전환 에러 ==========

    /**
     * 브랜치 전환(checkout/switch) 실패 시 에러 메시지를 변환한다.
     *
     * @param rawMessage git 에러 원본 메시지 (null 안전)
     * @return 사용자 친화적 한국어 메시지
     */
    public static String mapBranchError(String rawMessage) {
        if (rawMessage == null) return "알 수 없는 오류가 발생했습니다.";
        String msg = rawMessage.trim().toLowerCase();

        // ── 커밋하지 않은 변경이 있어 전환 불가 ──
        if (msg.contains("your local changes") || msg.contains("would be overwritten")
                || msg.contains("conflict") || msg.contains("uncommitted changes")) {
            return "커밋하지 않은 변경이 있어 브랜치를 전환할 수 없습니다.\n\n"
                    + "먼저 변경 사항을 커밋하거나 Stash하세요.";
        }

        // ── 브랜치를 찾을 수 없음 ──
        if (msg.contains("pathspec") && msg.contains("did not match")) {
            return "해당 브랜치를 찾을 수 없습니다.";
        }

        // ── 일반 git 에러도 체크 ──
        String gitMapped = mapGitError(rawMessage);
        if (!gitMapped.equals(truncate(rawMessage))) {
            return gitMapped;
        }

        // ── 너무 긴 메시지 축약 ──
        return truncate(rawMessage);
    }

    // ========== 유틸리티 ==========

    /**
     * 메시지가 {@link #MAX_MESSAGE_LENGTH}를 초과하면 축약한다.
     *
     * @param message 원본 메시지
     * @return 축약된 메시지 (또는 원본)
     */
    static String truncate(String message) {
        if (message == null) return "";
        if (message.length() > MAX_MESSAGE_LENGTH) {
            return message.substring(0, MAX_MESSAGE_LENGTH) + "\n\n... (메시지 축약됨)";
        }
        return message;
    }
}
