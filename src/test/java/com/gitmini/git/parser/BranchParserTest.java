package com.gitmini.git.parser;

import com.gitmini.model.BranchInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BranchParser 단위 테스트.
 * 실제 git branch -vv 출력 문자열을 사용하여 파싱을 검증한다.
 */
class BranchParserTest {

    private BranchParser parser;

    @BeforeEach
    void setUp() {
        parser = new BranchParser();
    }

    // ========== git branch -vv 파싱 ==========

    @Test
    void null_입력시_빈_리스트() {
        assertEquals(List.of(), parser.parse(null));
    }

    @Test
    void 빈_문자열_입력시_빈_리스트() {
        assertEquals(List.of(), parser.parse(""));
    }

    @Test
    void 현재_브랜치_파싱() {
        String output = "* main abc1234 [origin/main] latest commit";
        List<BranchInfo> branches = parser.parse(output);

        assertEquals(1, branches.size());
        BranchInfo branch = branches.get(0);
        assertEquals("main", branch.name());
        assertTrue(branch.current());
        assertEquals("origin/main", branch.trackingBranch());
        assertEquals(0, branch.ahead());
        assertEquals(0, branch.behind());
    }

    @Test
    void 비현재_브랜치_파싱() {
        String output = "  develop def5678 [origin/develop] another message";
        List<BranchInfo> branches = parser.parse(output);

        assertEquals(1, branches.size());
        assertFalse(branches.get(0).current());
        assertEquals("develop", branches.get(0).name());
        assertEquals("origin/develop", branches.get(0).trackingBranch());
    }

    @Test
    void ahead_behind_파싱() {
        String output = "* main abc1234 [origin/main: ahead 2, behind 3] commit msg";
        List<BranchInfo> branches = parser.parse(output);

        assertEquals(1, branches.size());
        assertEquals(2, branches.get(0).ahead());
        assertEquals(3, branches.get(0).behind());
    }

    @Test
    void ahead만_있는_경우() {
        String output = "* main abc1234 [origin/main: ahead 5] commit msg";
        List<BranchInfo> branches = parser.parse(output);

        assertEquals(1, branches.size());
        assertEquals(5, branches.get(0).ahead());
        assertEquals(0, branches.get(0).behind());
    }

    @Test
    void behind만_있는_경우() {
        String output = "* main abc1234 [origin/main: behind 2] commit msg";
        List<BranchInfo> branches = parser.parse(output);

        assertEquals(1, branches.size());
        assertEquals(0, branches.get(0).ahead());
        assertEquals(2, branches.get(0).behind());
    }

    @Test
    void tracking_없는_로컬_브랜치() {
        String output = "  feature ghi9012 some message";
        List<BranchInfo> branches = parser.parse(output);

        assertEquals(1, branches.size());
        assertNull(branches.get(0).trackingBranch());
        assertEquals(0, branches.get(0).ahead());
        assertEquals(0, branches.get(0).behind());
    }

    @Test
    void 여러_브랜치_동시_파싱() {
        String output = String.join("\n",
                "* main     abc1234 [origin/main: ahead 1] latest commit",
                "  develop  def5678 [origin/develop] feature work",
                "  feature  ghi9012 wip"
        );
        List<BranchInfo> branches = parser.parse(output);

        assertEquals(3, branches.size());

        assertTrue(branches.get(0).current());
        assertEquals("main", branches.get(0).name());
        assertEquals(1, branches.get(0).ahead());

        assertFalse(branches.get(1).current());
        assertEquals("develop", branches.get(1).name());
        assertEquals("origin/develop", branches.get(1).trackingBranch());

        assertFalse(branches.get(2).current());
        assertEquals("feature", branches.get(2).name());
        assertNull(branches.get(2).trackingBranch());
    }

    @Test
    void gone_upstream() {
        // upstream이 삭제된 경우
        String output = "  stale abc1234 [origin/stale: gone] old commit";
        List<BranchInfo> branches = parser.parse(output);

        assertEquals(1, branches.size());
        assertEquals("origin/stale", branches.get(0).trackingBranch());
        // "gone"일 때 ahead/behind는 0
        assertEquals(0, branches.get(0).ahead());
        assertEquals(0, branches.get(0).behind());
    }

    // ========== rev-list ahead/behind 파싱 ==========

    @Test
    void parseAheadBehind_정상() {
        // "3\t5" → behind 3, ahead 5
        int[] result = parser.parseAheadBehind("3\t5");
        assertEquals(5, result[0]); // ahead
        assertEquals(3, result[1]); // behind
    }

    @Test
    void parseAheadBehind_동기화됨() {
        int[] result = parser.parseAheadBehind("0\t0");
        assertEquals(0, result[0]);
        assertEquals(0, result[1]);
    }

    @Test
    void parseAheadBehind_null_입력() {
        int[] result = parser.parseAheadBehind(null);
        assertEquals(0, result[0]);
        assertEquals(0, result[1]);
    }

    @Test
    void parseAheadBehind_빈_입력() {
        int[] result = parser.parseAheadBehind("");
        assertEquals(0, result[0]);
        assertEquals(0, result[1]);
    }

    @Test
    void parseAheadBehind_잘못된_형식() {
        int[] result = parser.parseAheadBehind("not a number");
        assertEquals(0, result[0]);
        assertEquals(0, result[1]);
    }
}
