package com.gitmini.model;

/**
 * 변경된 파일 하나의 정보.
 *
 * @param path   레포 내 상대 경로
 * @param type   변경 유형
 * @param staged 스테이징 영역에 있는지 여부
 */
public record FileChange(String path, ChangeType type, boolean staged) {

    public enum ChangeType {
        MODIFIED,
        ADDED,
        DELETED,
        RENAMED,
        COPIED,
        UNTRACKED
    }
}
