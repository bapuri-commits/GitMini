package com.gitmini.model;

import java.util.List;

/**
 * diff의 하나의 변경 블록 (@@ ... @@ 영역).
 *
 * @param oldStart 변경 전 시작 줄 번호
 * @param oldCount 변경 전 줄 수
 * @param newStart 변경 후 시작 줄 번호
 * @param newCount 변경 후 줄 수
 * @param lines    블록 내 줄 목록 (컨텍스트, 추가, 삭제)
 */
public record DiffHunk(
        int oldStart,
        int oldCount,
        int newStart,
        int newCount,
        List<DiffLine> lines
) {
}
