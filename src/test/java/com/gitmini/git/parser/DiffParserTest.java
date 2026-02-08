package com.gitmini.git.parser;

import com.gitmini.model.DiffEntry;
import com.gitmini.model.DiffHunk;
import com.gitmini.model.DiffLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DiffParser 단위 테스트.
 * 실제 git diff 출력 문자열을 사용하여 파싱을 검증한다.
 */
class DiffParserTest {

    private DiffParser parser;

    @BeforeEach
    void setUp() {
        parser = new DiffParser();
    }

    @Test
    void null_입력시_빈_리스트() {
        assertEquals(List.of(), parser.parse(null));
    }

    @Test
    void 빈_문자열_입력시_빈_리스트() {
        assertEquals(List.of(), parser.parse(""));
    }

    @Test
    void 단일_파일_단일_hunk() {
        String output = String.join("\n",
                "diff --git a/README.md b/README.md",
                "index abc1234..def5678 100644",
                "--- a/README.md",
                "+++ b/README.md",
                "@@ -1,3 +1,4 @@",
                " unchanged line",
                "-removed line",
                "+added line",
                "+another new line",
                " context line"
        );
        List<DiffEntry> entries = parser.parse(output);

        assertEquals(1, entries.size());
        DiffEntry entry = entries.get(0);
        assertEquals("README.md", entry.oldPath());
        assertEquals("README.md", entry.newPath());
        assertEquals(1, entry.hunks().size());

        DiffHunk hunk = entry.hunks().get(0);
        assertEquals(1, hunk.oldStart());
        assertEquals(3, hunk.oldCount());
        assertEquals(1, hunk.newStart());
        assertEquals(4, hunk.newCount());

        List<DiffLine> lines = hunk.lines();
        assertEquals(5, lines.size());
        assertEquals(DiffLine.Type.CONTEXT, lines.get(0).type());
        assertEquals("unchanged line", lines.get(0).content());
        assertEquals(DiffLine.Type.REMOVED, lines.get(1).type());
        assertEquals("removed line", lines.get(1).content());
        assertEquals(DiffLine.Type.ADDED, lines.get(2).type());
        assertEquals("added line", lines.get(2).content());
        assertEquals(DiffLine.Type.ADDED, lines.get(3).type());
        assertEquals("another new line", lines.get(3).content());
        assertEquals(DiffLine.Type.CONTEXT, lines.get(4).type());
        assertEquals("context line", lines.get(4).content());
    }

    @Test
    void 여러_hunk() {
        String output = String.join("\n",
                "diff --git a/file.txt b/file.txt",
                "index 1234..5678 100644",
                "--- a/file.txt",
                "+++ b/file.txt",
                "@@ -1,3 +1,3 @@",
                " line1",
                "-old line2",
                "+new line2",
                " line3",
                "@@ -10,2 +10,3 @@",
                " line10",
                "+inserted",
                " line11"
        );
        List<DiffEntry> entries = parser.parse(output);

        assertEquals(1, entries.size());
        assertEquals(2, entries.get(0).hunks().size());

        DiffHunk hunk1 = entries.get(0).hunks().get(0);
        assertEquals(1, hunk1.oldStart());
        assertEquals(3, hunk1.oldCount());
        assertEquals(4, hunk1.lines().size());

        DiffHunk hunk2 = entries.get(0).hunks().get(1);
        assertEquals(10, hunk2.oldStart());
        assertEquals(2, hunk2.oldCount());
        assertEquals(10, hunk2.newStart());
        assertEquals(3, hunk2.newCount());
    }

    @Test
    void 여러_파일() {
        String output = String.join("\n",
                "diff --git a/file1.txt b/file1.txt",
                "index 1234..5678 100644",
                "--- a/file1.txt",
                "+++ b/file1.txt",
                "@@ -1,2 +1,2 @@",
                "-old",
                "+new",
                " same",
                "diff --git a/file2.java b/file2.java",
                "index aaaa..bbbb 100644",
                "--- a/file2.java",
                "+++ b/file2.java",
                "@@ -5,3 +5,4 @@",
                " context",
                "+added line",
                " context2",
                " context3"
        );
        List<DiffEntry> entries = parser.parse(output);

        assertEquals(2, entries.size());
        assertEquals("file1.txt", entries.get(0).oldPath());
        assertEquals("file2.java", entries.get(1).oldPath());
    }

    @Test
    void rename된_파일() {
        String output = String.join("\n",
                "diff --git a/old_name.txt b/new_name.txt",
                "index 1234..5678 100644",
                "--- a/old_name.txt",
                "+++ b/new_name.txt",
                "@@ -1,1 +1,1 @@",
                "-old content",
                "+new content"
        );
        List<DiffEntry> entries = parser.parse(output);

        assertEquals(1, entries.size());
        assertEquals("old_name.txt", entries.get(0).oldPath());
        assertEquals("new_name.txt", entries.get(0).newPath());
    }

    @Test
    void hunk_count_생략시_기본값_1() {
        // @@ -5 +5 @@ 형식 (count 생략 = 1줄)
        String output = String.join("\n",
                "diff --git a/file.txt b/file.txt",
                "index 1234..5678 100644",
                "--- a/file.txt",
                "+++ b/file.txt",
                "@@ -5 +5 @@",
                "-old",
                "+new"
        );
        List<DiffEntry> entries = parser.parse(output);

        assertEquals(1, entries.size());
        DiffHunk hunk = entries.get(0).hunks().get(0);
        assertEquals(5, hunk.oldStart());
        assertEquals(1, hunk.oldCount()); // 기본값
        assertEquals(5, hunk.newStart());
        assertEquals(1, hunk.newCount()); // 기본값
    }

    @Test
    void hunk_header_뒤_context_문자열() {
        // @@ -1,3 +1,4 @@ function name 형식
        String output = String.join("\n",
                "diff --git a/Main.java b/Main.java",
                "index 1234..5678 100644",
                "--- a/Main.java",
                "+++ b/Main.java",
                "@@ -1,3 +1,4 @@ public class Main {",
                " line1",
                "+added",
                " line2",
                " line3"
        );
        List<DiffEntry> entries = parser.parse(output);

        assertEquals(1, entries.size());
        assertEquals(4, entries.get(0).hunks().get(0).lines().size());
    }

    @Test
    void no_newline_마커_무시() {
        String output = String.join("\n",
                "diff --git a/file.txt b/file.txt",
                "index 1234..5678 100644",
                "--- a/file.txt",
                "+++ b/file.txt",
                "@@ -1,1 +1,1 @@",
                "-old content",
                "+new content",
                "\\ No newline at end of file"
        );
        List<DiffEntry> entries = parser.parse(output);

        assertEquals(1, entries.size());
        assertEquals(2, entries.get(0).hunks().get(0).lines().size());
    }

    @Test
    void hunks_리스트는_불변() {
        String output = String.join("\n",
                "diff --git a/file.txt b/file.txt",
                "index 1234..5678 100644",
                "--- a/file.txt",
                "+++ b/file.txt",
                "@@ -1,1 +1,1 @@",
                "-old",
                "+new"
        );
        List<DiffEntry> entries = parser.parse(output);

        assertThrows(UnsupportedOperationException.class, () ->
                entries.get(0).hunks().add(null));
        assertThrows(UnsupportedOperationException.class, () ->
                entries.get(0).hunks().get(0).lines().add(null));
    }
}
