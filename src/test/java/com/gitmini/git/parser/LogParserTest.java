package com.gitmini.git.parser;

import com.gitmini.model.CommitInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LogParser 단위 테스트.
 * git log --format="%H%x00%an%x00%s%x00%aI" 출력을 파싱 검증한다.
 */
class LogParserTest {

    /** null 문자 구분자. Java에서 \0 뒤에 숫자가 오면 8진수로 해석되므로 \u0000 사용. */
    private static final String SEP = "\u0000";

    private LogParser parser;

    @BeforeEach
    void setUp() {
        parser = new LogParser();
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
    void 단일_커밋_파싱() {
        String output = "abc1234567890def" + SEP + "John Doe" + SEP
                + "fix: typo in README" + SEP + "2024-01-15T10:30:00+09:00";
        List<CommitInfo> commits = parser.parse(output);

        assertEquals(1, commits.size());
        CommitInfo commit = commits.get(0);
        assertEquals("abc1234567890def", commit.hash());
        assertEquals("John Doe", commit.author());
        assertEquals("fix: typo in README", commit.message());
        assertNotNull(commit.date());
        assertEquals(2024, commit.date().getYear());
        assertEquals(1, commit.date().getMonthValue());
        assertEquals(15, commit.date().getDayOfMonth());
    }

    @Test
    void 여러_커밋_파싱() {
        String output = String.join("\n",
                "aaaa" + SEP + "Alice" + SEP + "feat: add login" + SEP + "2024-03-10T09:00:00+09:00",
                "bbbb" + SEP + "Bob" + SEP + "fix: null check" + SEP + "2024-03-09T18:30:00+09:00",
                "cccc" + SEP + "Charlie" + SEP + "docs: update README" + SEP + "2024-03-08T12:00:00+09:00"
        );
        List<CommitInfo> commits = parser.parse(output);

        assertEquals(3, commits.size());
        assertEquals("Alice", commits.get(0).author());
        assertEquals("Bob", commits.get(1).author());
        assertEquals("Charlie", commits.get(2).author());
    }

    @Test
    void UTC_날짜_파싱() {
        String output = "hash1" + SEP + "Author" + SEP + "message" + SEP + "2024-06-01T00:00:00+00:00";
        List<CommitInfo> commits = parser.parse(output);

        assertEquals(1, commits.size());
        assertEquals(LocalDateTime.of(2024, 6, 1, 0, 0, 0), commits.get(0).date());
    }

    @Test
    void 오프셋_없는_날짜_파싱() {
        String output = "hash1" + SEP + "Author" + SEP + "message" + SEP + "2024-06-01T15:30:00";
        List<CommitInfo> commits = parser.parse(output);

        assertEquals(1, commits.size());
        assertEquals(LocalDateTime.of(2024, 6, 1, 15, 30, 0), commits.get(0).date());
    }

    @Test
    void 잘못된_날짜는_null() {
        String output = "hash1" + SEP + "Author" + SEP + "message" + SEP + "invalid-date";
        List<CommitInfo> commits = parser.parse(output);

        assertEquals(1, commits.size());
        assertNull(commits.get(0).date());
    }

    @Test
    void 빈_줄_무시() {
        String output = "hash1" + SEP + "Author" + SEP + "message" + SEP
                + "2024-01-01T00:00:00+00:00" + "\n\n\n";
        List<CommitInfo> commits = parser.parse(output);

        assertEquals(1, commits.size());
    }

    @Test
    void 커밋_메시지에_특수문자() {
        String output = "hash1" + SEP + "Author" + SEP
                + "feat: add \"quotes\" & special <chars>" + SEP + "2024-01-01T00:00:00+00:00";
        List<CommitInfo> commits = parser.parse(output);

        assertEquals(1, commits.size());
        assertEquals("feat: add \"quotes\" & special <chars>", commits.get(0).message());
    }

    @Test
    void 구분자_부족하면_무시() {
        // 필드가 3개뿐 (4개 필요)
        String output = "hash1" + SEP + "Author" + SEP + "message";
        List<CommitInfo> commits = parser.parse(output);

        assertEquals(0, commits.size());
    }

    @Test
    void LOG_FORMAT_상수_확인() {
        assertEquals("%H%x00%an%x00%s%x00%aI", LogParser.LOG_FORMAT);
    }
}
