package com.gitmini.model;

import java.util.List;

/**
 * 하나의 파일에 대한 diff 정보.
 *
 * @param oldPath 변경 전 파일 경로
 * @param newPath 변경 후 파일 경로 (rename이 아니면 oldPath와 동일)
 * @param hunks   변경 블록(hunk) 목록
 */
public record DiffEntry(
        String oldPath,
        String newPath,
        List<DiffHunk> hunks
) {
}
