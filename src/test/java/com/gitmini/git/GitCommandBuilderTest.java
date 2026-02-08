package com.gitmini.git;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GitCommandBuilder 단위 테스트.
 * 빌더가 올바른 명령어 리스트를 조립하는지 검증한다.
 */
class GitCommandBuilderTest {

    @Test
    void git_status_porcelain() {
        List<String> cmd = GitCommandBuilder.git().status().porcelain().build();
        assertEquals(List.of("git", "status", "--porcelain"), cmd);
    }

    @Test
    void git_commit_with_message() {
        List<String> cmd = GitCommandBuilder.git().commit().message("fix: typo").build();
        assertEquals(List.of("git", "commit", "-m", "fix: typo"), cmd);
    }

    @Test
    void git_commit_amend_no_edit() {
        List<String> cmd = GitCommandBuilder.git().commit().amend().noEdit().build();
        assertEquals(List.of("git", "commit", "--amend", "--no-edit"), cmd);
    }

    @Test
    void git_commit_amend_with_message() {
        List<String> cmd = GitCommandBuilder.git().commit().amend().message("new msg").build();
        assertEquals(List.of("git", "commit", "--amend", "-m", "new msg"), cmd);
    }

    @Test
    void git_add_with_files() {
        List<String> cmd = GitCommandBuilder.git().add()
                .files(List.of("file1.txt", "src/Main.java")).build();
        assertEquals(List.of("git", "add", "--", "file1.txt", "src/Main.java"), cmd);
    }

    @Test
    void git_add_all() {
        List<String> cmd = GitCommandBuilder.git().add().arg(".").build();
        assertEquals(List.of("git", "add", "."), cmd);
    }

    @Test
    void git_push() {
        List<String> cmd = GitCommandBuilder.git().push().build();
        assertEquals(List.of("git", "push"), cmd);
    }

    @Test
    void git_pull() {
        List<String> cmd = GitCommandBuilder.git().pull().build();
        assertEquals(List.of("git", "pull"), cmd);
    }

    @Test
    void git_fetch() {
        List<String> cmd = GitCommandBuilder.git().fetch().build();
        assertEquals(List.of("git", "fetch"), cmd);
    }

    @Test
    void git_branch_verbose_no_color() {
        List<String> cmd = GitCommandBuilder.git().branch().verbose().noColor().build();
        assertEquals(List.of("git", "branch", "-vv", "--no-color"), cmd);
    }

    @Test
    void git_checkout_branch() {
        List<String> cmd = GitCommandBuilder.git().checkout().arg("develop").build();
        assertEquals(List.of("git", "checkout", "develop"), cmd);
    }

    @Test
    void git_checkout_files_for_discard() {
        List<String> cmd = GitCommandBuilder.git().checkout()
                .files(List.of("README.md")).build();
        assertEquals(List.of("git", "checkout", "--", "README.md"), cmd);
    }

    @Test
    void git_log_with_format_and_count() {
        List<String> cmd = GitCommandBuilder.git().log()
                .format("%H%x00%an%x00%s%x00%aI").maxCount(20).build();
        assertEquals(List.of("git", "log", "--format=%H%x00%an%x00%s%x00%aI", "-n", "20"), cmd);
    }

    @Test
    void git_diff_cached_no_color() {
        List<String> cmd = GitCommandBuilder.git().diff().cached().noColor().build();
        assertEquals(List.of("git", "diff", "--cached", "--no-color"), cmd);
    }

    @Test
    void git_diff_file() {
        List<String> cmd = GitCommandBuilder.git().diff().noColor()
                .files(List.of("README.md")).build();
        assertEquals(List.of("git", "diff", "--no-color", "--", "README.md"), cmd);
    }

    @Test
    void git_reset_head_files() {
        List<String> cmd = GitCommandBuilder.git().reset().arg("HEAD")
                .files(List.of("file.txt")).build();
        assertEquals(List.of("git", "reset", "HEAD", "--", "file.txt"), cmd);
    }

    @Test
    void git_rev_parse_abbrev_ref_HEAD() {
        List<String> cmd = GitCommandBuilder.git().revParse()
                .arg("--abbrev-ref").arg("HEAD").build();
        assertEquals(List.of("git", "rev-parse", "--abbrev-ref", "HEAD"), cmd);
    }

    @Test
    void git_rev_list_count_left_right() {
        List<String> cmd = GitCommandBuilder.git().revList().count().leftRight()
                .arg("@{u}...HEAD").build();
        assertEquals(List.of("git", "rev-list", "--count", "--left-right", "@{u}...HEAD"), cmd);
    }

    @Test
    void git_version() {
        List<String> cmd = GitCommandBuilder.git().version().build();
        assertEquals(List.of("git", "--version"), cmd);
    }

    @Test
    void git_branch_create() {
        List<String> cmd = GitCommandBuilder.git().branch().arg("feature-x").build();
        assertEquals(List.of("git", "branch", "feature-x"), cmd);
    }

    @Test
    void build_returns_immutable_list() {
        List<String> cmd = GitCommandBuilder.git().status().build();
        assertThrows(UnsupportedOperationException.class, () -> cmd.add("extra"));
    }

    @Test
    void buildString_returns_space_joined() {
        String str = GitCommandBuilder.git().status().porcelain().buildString();
        assertEquals("git status --porcelain", str);
    }

    @Test
    void args_varargs() {
        List<String> cmd = GitCommandBuilder.git().log()
                .args("--oneline", "--all", "--graph").build();
        assertEquals(List.of("git", "log", "--oneline", "--all", "--graph"), cmd);
    }
}
