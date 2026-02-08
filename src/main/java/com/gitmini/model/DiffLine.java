package com.gitmini.model;

/**
 * diff 내 한 줄의 정보.
 *
 * @param type    줄 유형 (컨텍스트, 추가, 삭제)
 * @param content 줄 내용 (접두사 +/-/공백 제외)
 */
public record DiffLine(
        Type type,
        String content
) {
    public enum Type {
        /** 변경 없는 컨텍스트 줄. */
        CONTEXT,
        /** 추가된 줄 (+). */
        ADDED,
        /** 삭제된 줄 (-). */
        REMOVED
    }
}
