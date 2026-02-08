package com.gitmini.git.parser;

import com.gitmini.model.DiffEntry;
import com.gitmini.model.DiffHunk;
import com.gitmini.model.DiffLine;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code git diff} 출력을 구조화된 모델로 파싱한다.
 * <p>
 * 파싱 결과는 파일별 {@link DiffEntry} → 블록별 {@link DiffHunk} → 줄별 {@link DiffLine}
 * 계층 구조로 반환된다.
 * </p>
 *
 * <h3>diff 출력 예시</h3>
 * <pre>
 * diff --git a/README.md b/README.md
 * index abc1234..def5678 100644
 * --- a/README.md
 * +++ b/README.md
 * @@ -1,3 +1,4 @@
 *  unchanged line
 * -removed line
 * +added line
 * +another new line
 *  unchanged line
 * </pre>
 */
public class DiffParser {

    /** diff --git a/FILE b/FILE 패턴. */
    private static final Pattern DIFF_HEADER = Pattern.compile(
            "^diff --git a/(.+) b/(.+)$"
    );

    /** @@ -OLD_START,OLD_COUNT +NEW_START,NEW_COUNT @@ ... 패턴. */
    private static final Pattern HUNK_HEADER = Pattern.compile(
            "^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*$"
    );

    /**
     * git diff 출력을 DiffEntry 리스트로 파싱한다.
     *
     * @param output git diff 출력 (null-safe)
     * @return 파일별 diff 정보 목록
     */
    public List<DiffEntry> parse(String output) {
        if (output == null || output.isBlank()) {
            return List.of();
        }

        List<DiffEntry> entries = new ArrayList<>();
        String[] lines = output.split("\n", -1); // trailing empty 보존

        String currentOldPath = null;
        String currentNewPath = null;
        List<DiffHunk> currentHunks = new ArrayList<>();
        List<DiffLine> currentLines = null;
        int hunkOldStart = 0, hunkOldCount = 0, hunkNewStart = 0, hunkNewCount = 0;

        for (String line : lines) {

            // === 새 파일 diff 시작 ===
            Matcher diffMatcher = DIFF_HEADER.matcher(line);
            if (diffMatcher.matches()) {
                // 이전 파일 저장
                saveEntry(entries, currentOldPath, currentNewPath, currentHunks,
                        currentLines, hunkOldStart, hunkOldCount, hunkNewStart, hunkNewCount);

                currentOldPath = diffMatcher.group(1);
                currentNewPath = diffMatcher.group(2);
                currentHunks = new ArrayList<>();
                currentLines = null;
                continue;
            }

            // === 새 hunk 시작 ===
            Matcher hunkMatcher = HUNK_HEADER.matcher(line);
            if (hunkMatcher.matches()) {
                // 이전 hunk 저장
                if (currentLines != null) {
                    currentHunks.add(new DiffHunk(
                            hunkOldStart, hunkOldCount, hunkNewStart, hunkNewCount,
                            List.copyOf(currentLines)));
                }

                hunkOldStart = Integer.parseInt(hunkMatcher.group(1));
                hunkOldCount = hunkMatcher.group(2) != null ? Integer.parseInt(hunkMatcher.group(2)) : 1;
                hunkNewStart = Integer.parseInt(hunkMatcher.group(3));
                hunkNewCount = hunkMatcher.group(4) != null ? Integer.parseInt(hunkMatcher.group(4)) : 1;
                currentLines = new ArrayList<>();
                continue;
            }

            // === hunk 밖의 헤더 줄 (index, ---, +++) → skip ===
            if (currentLines == null) continue;

            // === diff 줄 파싱 ===
            if (line.startsWith("+")) {
                currentLines.add(new DiffLine(DiffLine.Type.ADDED, line.substring(1)));
            } else if (line.startsWith("-")) {
                currentLines.add(new DiffLine(DiffLine.Type.REMOVED, line.substring(1)));
            } else if (line.startsWith(" ")) {
                currentLines.add(new DiffLine(DiffLine.Type.CONTEXT, line.substring(1)));
            }
            // "\ No newline at end of file" 등은 무시
        }

        // 마지막 파일 저장
        saveEntry(entries, currentOldPath, currentNewPath, currentHunks,
                currentLines, hunkOldStart, hunkOldCount, hunkNewStart, hunkNewCount);

        return entries;
    }

    private void saveEntry(List<DiffEntry> entries,
                           String oldPath, String newPath,
                           List<DiffHunk> hunks, List<DiffLine> lines,
                           int oldStart, int oldCount, int newStart, int newCount) {
        if (oldPath == null) return;

        // 마지막 hunk 저장
        if (lines != null) {
            hunks.add(new DiffHunk(oldStart, oldCount, newStart, newCount, List.copyOf(lines)));
        }

        entries.add(new DiffEntry(oldPath, newPath, List.copyOf(hunks)));
    }
}
