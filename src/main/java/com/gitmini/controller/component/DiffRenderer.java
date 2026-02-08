package com.gitmini.controller.component;

import com.gitmini.model.DiffEntry;
import com.gitmini.model.DiffHunk;
import com.gitmini.model.DiffLine;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * DiffEntry 목록을 VBox에 styled Label로 렌더링하는 유틸리티.
 * <p>
 * CSS 클래스:
 * <ul>
 *   <li>{@code .diff-line-added} — 추가된 줄 (초록 배경)</li>
 *   <li>{@code .diff-line-removed} — 삭제된 줄 (빨강 배경)</li>
 *   <li>{@code .diff-line-context} — 변경 없는 줄</li>
 *   <li>{@code .diff-line-hunk} — @@ 블록 헤더 (파랑 배경)</li>
 * </ul>
 * </p>
 */
public final class DiffRenderer {

    private DiffRenderer() {} // 유틸리티 클래스

    /**
     * DiffEntry 목록을 VBox에 렌더링한다.
     * 기존 children을 모두 제거하고 새로 추가한다.
     *
     * @param container  대상 VBox (diffContent)
     * @param entries    렌더링할 DiffEntry 목록
     */
    public static void render(VBox container, List<DiffEntry> entries) {
        container.getChildren().clear();

        if (entries == null || entries.isEmpty()) {
            Label empty = new Label("변경 내용이 없습니다.");
            empty.getStyleClass().add("diff-line-context");
            container.getChildren().add(empty);
            return;
        }

        for (DiffEntry entry : entries) {
            renderEntry(container, entry);
        }
    }

    private static void renderEntry(VBox container, DiffEntry entry) {
        for (DiffHunk hunk : entry.hunks()) {
            // Hunk 헤더: @@ -oldStart,oldCount +newStart,newCount @@
            String hunkHeader = String.format("@@ -%d,%d +%d,%d @@",
                    hunk.oldStart(), hunk.oldCount(),
                    hunk.newStart(), hunk.newCount());
            Label hunkLabel = createLine(hunkHeader, "diff-line-hunk");
            container.getChildren().add(hunkLabel);

            // Hunk 내 각 줄
            for (DiffLine line : hunk.lines()) {
                String prefix;
                String styleClass;
                switch (line.type()) {
                    case ADDED -> {
                        prefix = "+ ";
                        styleClass = "diff-line-added";
                    }
                    case REMOVED -> {
                        prefix = "- ";
                        styleClass = "diff-line-removed";
                    }
                    default -> {
                        prefix = "  ";
                        styleClass = "diff-line-context";
                    }
                }
                Label lineLabel = createLine(prefix + line.content(), styleClass);
                container.getChildren().add(lineLabel);
            }
        }
    }

    private static Label createLine(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setWrapText(false);
        // monospace 폰트는 .diff-content CSS에서 상속
        return label;
    }
}
