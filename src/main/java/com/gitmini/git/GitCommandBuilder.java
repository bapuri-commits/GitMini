package com.gitmini.git;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Git 명령어를 조립하는 Fluent API 빌더.
 * <p>
 * 사용 예:
 * <pre>
 * // git status --porcelain
 * GitCommandBuilder.git().status().porcelain().build();
 *
 * // git commit -m "fix: typo"
 * GitCommandBuilder.git().commit().message("fix: typo").build();
 *
 * // git log --format="%H%x00%an%x00%s%x00%aI" -n 20
 * GitCommandBuilder.git().log().format("%H%x00%an%x00%s%x00%aI").maxCount(20).build();
 *
 * // git diff --cached --no-color
 * GitCommandBuilder.git().diff().cached().noColor().build();
 *
 * // git add -- file1.txt file2.txt
 * GitCommandBuilder.git().add().files(List.of("file1.txt", "file2.txt")).build();
 * </pre>
 * </p>
 */
public class GitCommandBuilder {

    private final List<String> args = new ArrayList<>();

    private GitCommandBuilder() {
        args.add("git");
    }

    /**
     * 새 빌더를 생성한다. "git" 이 자동으로 첫 인자로 추가된다.
     */
    public static GitCommandBuilder git() {
        return new GitCommandBuilder();
    }

    // ========== Git 명령어 (Commands) ==========

    public GitCommandBuilder status() {
        args.add("status");
        return this;
    }

    public GitCommandBuilder add() {
        args.add("add");
        return this;
    }

    public GitCommandBuilder commit() {
        args.add("commit");
        return this;
    }

    public GitCommandBuilder push() {
        args.add("push");
        return this;
    }

    public GitCommandBuilder pull() {
        args.add("pull");
        return this;
    }

    public GitCommandBuilder fetch() {
        args.add("fetch");
        return this;
    }

    public GitCommandBuilder branch() {
        args.add("branch");
        return this;
    }

    public GitCommandBuilder checkout() {
        args.add("checkout");
        return this;
    }

    public GitCommandBuilder log() {
        args.add("log");
        return this;
    }

    public GitCommandBuilder diff() {
        args.add("diff");
        return this;
    }

    public GitCommandBuilder reset() {
        args.add("reset");
        return this;
    }

    public GitCommandBuilder revList() {
        args.add("rev-list");
        return this;
    }

    public GitCommandBuilder revParse() {
        args.add("rev-parse");
        return this;
    }

    public GitCommandBuilder remote() {
        args.add("remote");
        return this;
    }

    /** git clone (Java의 clone()과 충돌 방지를 위해 cloneRepo로 명명). */
    public GitCommandBuilder cloneRepo() {
        args.add("clone");
        return this;
    }

    public GitCommandBuilder version() {
        args.add("--version");
        return this;
    }

    // ========== 옵션 (Options) ==========

    public GitCommandBuilder porcelain() {
        args.add("--porcelain");
        return this;
    }

    public GitCommandBuilder message(String msg) {
        args.add("-m");
        args.add(msg);
        return this;
    }

    public GitCommandBuilder amend() {
        args.add("--amend");
        return this;
    }

    public GitCommandBuilder noEdit() {
        args.add("--no-edit");
        return this;
    }

    public GitCommandBuilder noColor() {
        args.add("--no-color");
        return this;
    }

    public GitCommandBuilder verbose() {
        args.add("-vv");
        return this;
    }

    public GitCommandBuilder cached() {
        args.add("--cached");
        return this;
    }

    public GitCommandBuilder nameOnly() {
        args.add("--name-only");
        return this;
    }

    public GitCommandBuilder oneline() {
        args.add("--oneline");
        return this;
    }

    public GitCommandBuilder all() {
        args.add("--all");
        return this;
    }

    public GitCommandBuilder count() {
        args.add("--count");
        return this;
    }

    public GitCommandBuilder leftRight() {
        args.add("--left-right");
        return this;
    }

    public GitCommandBuilder progress() {
        args.add("--progress");
        return this;
    }

    public GitCommandBuilder maxCount(int n) {
        args.add("-n");
        args.add(String.valueOf(n));
        return this;
    }

    public GitCommandBuilder format(String format) {
        args.add("--format=" + format);
        return this;
    }

    // ========== 범용 인자 ==========

    /**
     * 단일 인자를 추가한다.
     */
    public GitCommandBuilder arg(String arg) {
        args.add(arg);
        return this;
    }

    /**
     * 여러 인자를 추가한다.
     */
    public GitCommandBuilder args(String... moreArgs) {
        Collections.addAll(args, moreArgs);
        return this;
    }

    /**
     * "--" 구분자 후에 파일 목록을 추가한다.
     * git에서 파일 경로를 옵션과 분리하기 위해 사용한다.
     */
    public GitCommandBuilder files(List<String> files) {
        args.add("--");
        args.addAll(files);
        return this;
    }

    // ========== 빌드 ==========

    /**
     * 조립된 명령어를 불변 리스트로 반환한다.
     */
    public List<String> build() {
        return List.copyOf(args);
    }

    /**
     * 조립된 명령어를 공백으로 연결한 문자열로 반환한다 (로깅/표시용).
     */
    public String buildString() {
        return String.join(" ", args);
    }
}
