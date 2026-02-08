package com.gitmini.controller.component;

import com.gitmini.model.FileChange;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/**
 * Unstaged/Staged 파일 목록의 커스텀 셀.
 * <p>
 * 표시 형식: {@code [M] README.md} 또는 {@code [?] newfile.txt}
 * <br>
 * 변경 유형에 따라 아이콘 문자와 색상이 달라진다.
 * </p>
 */
public class FileChangeListCell extends ListCell<FileChange> {

    private final HBox root;
    private final Label typeLabel;
    private final Label pathLabel;
    private final Tooltip pathTooltip;

    public FileChangeListCell() {
        typeLabel = new Label();
        typeLabel.getStyleClass().add("file-type-icon");
        typeLabel.setMinWidth(24);
        typeLabel.setAlignment(Pos.CENTER);

        pathLabel = new Label();
        pathLabel.getStyleClass().add("file-path");
        HBox.setHgrow(pathLabel, Priority.ALWAYS);

        root = new HBox(6, typeLabel, pathLabel);
        root.setAlignment(Pos.CENTER_LEFT);
        root.getStyleClass().add("file-change-cell");

        pathTooltip = new Tooltip();
    }

    @Override
    protected void updateItem(FileChange file, boolean empty) {
        super.updateItem(file, empty);

        if (empty || file == null) {
            setGraphic(null);
            setText(null);
            setTooltip(null);
            return;
        }

        // 변경 유형 아이콘 + 색상
        String icon = typeIcon(file.type());
        typeLabel.setText(icon);

        // 기존 타입 클래스 모두 제거 후 현재 타입만 추가
        typeLabel.getStyleClass().removeAll(
                "type-modified", "type-added", "type-deleted",
                "type-renamed", "type-untracked");
        typeLabel.getStyleClass().add(typeStyleClass(file.type()));

        // 파일 경로: 전체 상대 경로 표시 (같은 이름의 파일 구분을 위해)
        pathLabel.setText(file.path());
        pathTooltip.setText(file.path());
        setTooltip(pathTooltip);
        setGraphic(root);
        setText(null);
    }

    private static String typeIcon(FileChange.ChangeType type) {
        return switch (type) {
            case MODIFIED -> "M";
            case ADDED -> "A";
            case DELETED -> "D";
            case RENAMED -> "R";
            case COPIED -> "C";
            case UNTRACKED -> "?";
        };
    }

    private static String typeStyleClass(FileChange.ChangeType type) {
        return switch (type) {
            case MODIFIED -> "type-modified";
            case ADDED -> "type-added";
            case DELETED -> "type-deleted";
            case RENAMED -> "type-renamed";
            case COPIED -> "type-renamed"; // copied는 renamed와 같은 색상
            case UNTRACKED -> "type-untracked";
        };
    }
}
