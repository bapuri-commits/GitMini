package com.gitmini.git.parser;

import com.gitmini.model.BranchInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Git 브랜치 관련 출력을 파싱한다.
 *
 * <h3>{@code git branch -vv --no-color} 파싱</h3>
 * <pre>
 * * main     abc1234 [origin/main: ahead 2, behind 1] commit message
 *   develop  def5678 [origin/develop] another message
 *   feature  ghi9012 some message
 * </pre>
 *
 * <h3>{@code git rev-list --count --left-right @{u}...HEAD} 파싱</h3>
 * <pre>
 * 3    5    (behind 3, ahead 5)
 * </pre>
 */
public class BranchParser {

    private static final Logger log = LoggerFactory.getLogger(BranchParser.class);

    /**
     * git branch -vv 출력의 한 줄 패턴.
     * Group 1: "*" 또는 " " (현재 브랜치 표시)
     * Group 2: 브랜치 이름
     * Group 3: 커밋 해시
     * Group 4: 트래킹 정보 (optional, 대괄호 내부)
     * Group 5: 커밋 메시지
     */
    private static final Pattern BRANCH_LINE = Pattern.compile(
            "^([* ]) (\\S+)\\s+(\\w+)\\s*(?:\\[([^\\]]+)])?\\s*(.*)$"
    );

    /**
     * ahead/behind 숫자 추출 패턴.
     */
    private static final Pattern AHEAD_PATTERN = Pattern.compile("ahead (\\d+)");
    private static final Pattern BEHIND_PATTERN = Pattern.compile("behind (\\d+)");

    /**
     * {@code git branch -vv --no-color} 출력을 파싱한다.
     *
     * @param output git branch -vv 출력 (null-safe)
     * @return 브랜치 정보 목록
     */
    public List<BranchInfo> parse(String output) {
        if (output == null || output.isBlank()) {
            return List.of();
        }

        List<BranchInfo> branches = new ArrayList<>();
        String[] lines = output.split("\n");

        for (String line : lines) {
            if (line.isBlank()) continue;

            Matcher m = BRANCH_LINE.matcher(line);
            if (!m.matches()) {
                log.debug("매칭되지 않는 branch 라인 무시: '{}'", line);
                continue;
            }

            boolean current = "*".equals(m.group(1));
            String name = m.group(2);
            String trackingInfo = m.group(4); // null일 수 있음

            String trackingBranch = null;
            int ahead = 0;
            int behind = 0;

            if (trackingInfo != null) {
                // "origin/main: ahead 2, behind 1"
                // "origin/main"
                // "origin/main: gone"
                int colonIdx = trackingInfo.indexOf(':');
                if (colonIdx >= 0) {
                    trackingBranch = trackingInfo.substring(0, colonIdx).trim();
                    String abInfo = trackingInfo.substring(colonIdx + 1).trim();

                    Matcher aheadMatcher = AHEAD_PATTERN.matcher(abInfo);
                    if (aheadMatcher.find()) {
                        ahead = Integer.parseInt(aheadMatcher.group(1));
                    }
                    Matcher behindMatcher = BEHIND_PATTERN.matcher(abInfo);
                    if (behindMatcher.find()) {
                        behind = Integer.parseInt(behindMatcher.group(1));
                    }
                } else {
                    trackingBranch = trackingInfo.trim();
                }
            }

            branches.add(new BranchInfo(name, trackingBranch, ahead, behind, current));
        }

        return branches;
    }

    /**
     * {@code git rev-list --count --left-right @{u}...HEAD} 출력을 파싱한다.
     * <p>
     * 출력 형식: "3\t5" → behind 3, ahead 5
     * (left = behind, right = ahead)
     * </p>
     *
     * @param output rev-list 출력 (null-safe)
     * @return int[]{ahead, behind}
     */
    public int[] parseAheadBehind(String output) {
        if (output == null || output.isBlank()) {
            return new int[]{0, 0};
        }

        String[] parts = output.trim().split("\\s+");
        if (parts.length >= 2) {
            try {
                int behind = Integer.parseInt(parts[0]); // left = upstream commits
                int ahead = Integer.parseInt(parts[1]);   // right = local commits
                return new int[]{ahead, behind};
            } catch (NumberFormatException e) {
                return new int[]{0, 0};
            }
        }
        return new int[]{0, 0};
    }
}
