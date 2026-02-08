package com.gitmini.git.parser;

import com.gitmini.model.CommitInfo;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code git log} 출력을 파싱한다.
 * <p>
 * null 문자({@code %x00})를 구분자로 사용하여, 커밋 메시지 내 특수 문자 문제를 방지한다.
 * </p>
 *
 * <h3>사용할 git log format</h3>
 * <pre>
 * git log --format="%H%x00%an%x00%s%x00%aI"
 * </pre>
 * <p>각 줄 형식: {@code FULL_HASH\0AUTHOR_NAME\0SUBJECT\0ISO_DATE}</p>
 */
public class LogParser {

    /**
     * git log에 사용할 format 문자열.
     * %H = full hash, %an = author name, %s = subject, %aI = author date (ISO 8601)
     */
    public static final String LOG_FORMAT = "%H%x00%an%x00%s%x00%aI";

    private static final String DELIMITER = "\0";

    /**
     * git log 출력을 CommitInfo 리스트로 파싱한다.
     *
     * @param output git log --format=LOG_FORMAT 출력 (null-safe)
     * @return 커밋 정보 목록 (최신 커밋이 첫 번째)
     */
    public List<CommitInfo> parse(String output) {
        if (output == null || output.isBlank()) {
            return List.of();
        }

        List<CommitInfo> commits = new ArrayList<>();
        String[] lines = output.split("\n");

        for (String line : lines) {
            if (line.isBlank()) continue;

            String[] parts = line.split(DELIMITER, 4);
            if (parts.length < 4) continue;

            String hash = parts[0].trim();
            String author = parts[1].trim();
            String message = parts[2].trim();
            String dateStr = parts[3].trim();

            LocalDateTime date = parseDate(dateStr);

            commits.add(new CommitInfo(hash, author, message, date));
        }

        return commits;
    }

    /**
     * ISO 8601 날짜 문자열을 LocalDateTime으로 파싱한다.
     * <p>
     * 지원 형식:
     * <ul>
     *   <li>ISO_OFFSET_DATE_TIME: "2024-01-15T10:30:00+09:00"</li>
     *   <li>ISO_LOCAL_DATE_TIME: "2024-01-15T10:30:00"</li>
     * </ul>
     * </p>
     *
     * @return 파싱된 날짜, 실패 시 null
     */
    private LocalDateTime parseDate(String dateStr) {
        try {
            // ISO 8601 with offset: "2024-01-15T10:30:00+09:00"
            OffsetDateTime odt = OffsetDateTime.parse(dateStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            return odt.toLocalDateTime();
        } catch (DateTimeParseException e) {
            try {
                // ISO 8601 without offset: "2024-01-15T10:30:00"
                return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }
}
