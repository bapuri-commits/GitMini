package com.gitmini.git.parser;

import com.gitmini.model.FileChange;
import com.gitmini.model.FileChange.ChangeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StatusParser 단위 테스트.
 * 실제 git status --porcelain 출력 문자열을 사용하여 파싱을 검증한다.
 */
class StatusParserTest {

    private StatusParser parser;

    @BeforeEach
    void setUp() {
        parser = new StatusParser();
    }

    @Test
    void null_입력시_빈_리스트() {
        assertEquals(List.of(), parser.parse(null));
    }

    @Test
    void 빈_문자열_입력시_빈_리스트() {
        assertEquals(List.of(), parser.parse(""));
        assertEquals(List.of(), parser.parse("   "));
    }

    @Test
    void untracked_파일() {
        String output = "?? new_file.txt";
        List<FileChange> changes = parser.parse(output);

        assertEquals(1, changes.size());
        assertEquals("new_file.txt", changes.get(0).path());
        assertEquals(ChangeType.UNTRACKED, changes.get(0).type());
        assertFalse(changes.get(0).staged());
    }

    @Test
    void staged_modified_파일() {
        String output = "M  README.md";
        List<FileChange> changes = parser.parse(output);

        assertEquals(1, changes.size());
        assertEquals("README.md", changes.get(0).path());
        assertEquals(ChangeType.MODIFIED, changes.get(0).type());
        assertTrue(changes.get(0).staged());
    }

    @Test
    void unstaged_modified_파일() {
        String output = " M README.md";
        List<FileChange> changes = parser.parse(output);

        assertEquals(1, changes.size());
        assertEquals("README.md", changes.get(0).path());
        assertEquals(ChangeType.MODIFIED, changes.get(0).type());
        assertFalse(changes.get(0).staged());
    }

    @Test
    void staged_와_unstaged_동시_modified() {
        // MM = staging area도 modified, working tree도 modified
        String output = "MM both.txt";
        List<FileChange> changes = parser.parse(output);

        assertEquals(2, changes.size());
        // staged
        assertEquals("both.txt", changes.get(0).path());
        assertEquals(ChangeType.MODIFIED, changes.get(0).type());
        assertTrue(changes.get(0).staged());
        // unstaged
        assertEquals("both.txt", changes.get(1).path());
        assertEquals(ChangeType.MODIFIED, changes.get(1).type());
        assertFalse(changes.get(1).staged());
    }

    @Test
    void staged_added_파일() {
        String output = "A  new_file.java";
        List<FileChange> changes = parser.parse(output);

        assertEquals(1, changes.size());
        assertEquals("new_file.java", changes.get(0).path());
        assertEquals(ChangeType.ADDED, changes.get(0).type());
        assertTrue(changes.get(0).staged());
    }

    @Test
    void staged_deleted_파일() {
        String output = "D  old_file.txt";
        List<FileChange> changes = parser.parse(output);

        assertEquals(1, changes.size());
        assertEquals("old_file.txt", changes.get(0).path());
        assertEquals(ChangeType.DELETED, changes.get(0).type());
        assertTrue(changes.get(0).staged());
    }

    @Test
    void renamed_파일() {
        String output = "R  old_name.txt -> new_name.txt";
        List<FileChange> changes = parser.parse(output);

        assertEquals(1, changes.size());
        assertEquals("new_name.txt", changes.get(0).path());
        assertEquals(ChangeType.RENAMED, changes.get(0).type());
        assertTrue(changes.get(0).staged());
    }

    @Test
    void 여러_파일_동시_파싱() {
        String output = String.join("\n",
                " M README.md",
                "M  src/Main.java",
                "?? test.txt",
                "A  new.java",
                "D  deleted.txt"
        );
        List<FileChange> changes = parser.parse(output);

        assertEquals(5, changes.size());

        // README.md — unstaged modified
        assertEquals("README.md", changes.get(0).path());
        assertFalse(changes.get(0).staged());
        assertEquals(ChangeType.MODIFIED, changes.get(0).type());

        // src/Main.java — staged modified
        assertEquals("src/Main.java", changes.get(1).path());
        assertTrue(changes.get(1).staged());
        assertEquals(ChangeType.MODIFIED, changes.get(1).type());

        // test.txt — untracked
        assertEquals("test.txt", changes.get(2).path());
        assertFalse(changes.get(2).staged());
        assertEquals(ChangeType.UNTRACKED, changes.get(2).type());

        // new.java — staged added
        assertEquals("new.java", changes.get(3).path());
        assertTrue(changes.get(3).staged());
        assertEquals(ChangeType.ADDED, changes.get(3).type());

        // deleted.txt — staged deleted
        assertEquals("deleted.txt", changes.get(4).path());
        assertTrue(changes.get(4).staged());
        assertEquals(ChangeType.DELETED, changes.get(4).type());
    }

    @Test
    void 경로에_공백_포함() {
        String output = " M path with spaces/file name.txt";
        List<FileChange> changes = parser.parse(output);

        assertEquals(1, changes.size());
        assertEquals("path with spaces/file name.txt", changes.get(0).path());
    }

    @Test
    void 경로에_한글_포함() {
        String output = "?? 한글폴더/테스트.txt";
        List<FileChange> changes = parser.parse(output);

        assertEquals(1, changes.size());
        assertEquals("한글폴더/테스트.txt", changes.get(0).path());
    }

    @Test
    void unstaged_deleted_파일() {
        String output = " D removed.txt";
        List<FileChange> changes = parser.parse(output);

        assertEquals(1, changes.size());
        assertEquals("removed.txt", changes.get(0).path());
        assertEquals(ChangeType.DELETED, changes.get(0).type());
        assertFalse(changes.get(0).staged());
    }
}
