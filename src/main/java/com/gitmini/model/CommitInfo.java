package com.gitmini.model;

import java.time.LocalDateTime;

/**
 * 커밋 하나의 정보.
 *
 * @param hash    커밋 해시 (short 또는 full)
 * @param author  작성자
 * @param message 커밋 메시지 (첫 줄)
 * @param date    커밋 일시
 */
public record CommitInfo(
        String hash,
        String author,
        String message,
        LocalDateTime date
) {
}
