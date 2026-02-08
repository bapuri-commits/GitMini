package com.gitmini.controller.component;

import com.gitmini.model.Repository;
import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * 사이드바 레포 목록의 커스텀 셀.
 * <p>
 * 표시 형식:
 * <pre>
 *   Algorithm_Drill
 *   main ✓           ↑0 ↓0
 * </pre>
 * 또는 변경이 있을 때:
 * <pre>
 *   GitMini
 *   main ⚠3          ↑1 ↓0
 * </pre>
 * </p>
 * 우클릭 시 컨텍스트 메뉴(레포 제거)를 표시한다.
 */
public class RepoListCell extends ListCell<Repository> {

    private final HBox root;
    private final Label nameLabel;
    private final Label branchLabel;
    private final Label statusLabel;
    private final Label aheadBehindLabel;
    private final Tooltip pathTooltip;
    private final ContextMenu contextMenu;

    public RepoListCell(Consumer<Repository> onRemove) {
        // 레이아웃 구성
        nameLabel = new Label();
        nameLabel.getStyleClass().add("repo-cell-name");

        branchLabel = new Label();
        branchLabel.getStyleClass().add("repo-cell-branch");

        statusLabel = new Label();
        statusLabel.getStyleClass().add("repo-cell-status");

        aheadBehindLabel = new Label();
        aheadBehindLabel.getStyleClass().add("repo-cell-ahead-behind");

        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bottomRow = new HBox(4, branchLabel, statusLabel, spacer, aheadBehindLabel);
        bottomRow.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(2, nameLabel, bottomRow);
        root = new HBox(content);
        HBox.setHgrow(content, Priority.ALWAYS);
        root.getStyleClass().add("repo-cell");

        // 경로 툴팁
        pathTooltip = new Tooltip();

        // 우클릭 시 선택 변경 방지: 우클릭은 컨텍스트 메뉴용이지, 레포 전환 의도가 아님.
        // 선택이 변경되면 refreshRepoDetail이 실행되어 repoListView.refresh()가
        // 셀을 재구성하면서 컨텍스트 메뉴 액션이 무효화되는 문제를 방지.
        addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                event.consume();
            }
        });

        // 컨텍스트 메뉴
        MenuItem removeItem = new MenuItem("레포 제거");
        removeItem.setOnAction(e -> {
            Repository repo = getItem();
            if (repo != null && onRemove != null) {
                onRemove.accept(repo);
            }
        });
        contextMenu = new ContextMenu(removeItem);
    }

    @Override
    protected void updateItem(Repository repo, boolean empty) {
        super.updateItem(repo, empty);

        if (empty || repo == null) {
            setGraphic(null);
            setText(null);
            setContextMenu(null);
            setTooltip(null);
            return;
        }

        nameLabel.setText(repo.getName());
        // 브랜치 줄에 원격 없으면 "main (원격 없음)" 형태로 표시
        String branchText = repo.getCurrentBranch();
        if (!repo.isHasRemote() && (branchText != null && !branchText.isEmpty())) {
            branchText = branchText + " (원격 없음)";
        }
        branchLabel.setText(branchText);

        // 상태 아이콘 — 양쪽 클래스를 모두 제거 후 하나만 추가 (셀 재사용 시 누적 방지)
        statusLabel.getStyleClass().removeAll("status-clean", "status-dirty");
        if (repo.isClean()) {
            statusLabel.setText("✓");
            statusLabel.getStyleClass().add("status-clean");
        } else {
            statusLabel.setText("⚠" + repo.getChangedFileCount());
            statusLabel.getStyleClass().add("status-dirty");
        }

        // ahead/behind (원격 없을 땐 브랜치 줄에 이미 "원격 없음" 표시하므로 여기는 비움)
        if (!repo.isHasRemote()) {
            aheadBehindLabel.setText("");
        } else {
            int ahead = repo.getAhead();
            int behind = repo.getBehind();
            if (ahead > 0 || behind > 0) {
                StringBuilder ab = new StringBuilder();
                if (ahead > 0) ab.append("↑").append(ahead);
                if (ahead > 0 && behind > 0) ab.append(" ");
                if (behind > 0) ab.append("↓").append(behind);
                aheadBehindLabel.setText(ab.toString());
            } else {
                aheadBehindLabel.setText("");
            }
        }

        pathTooltip.setText(repo.getPath());
        setTooltip(pathTooltip);
        setGraphic(root);
        setText(null);
        setContextMenu(contextMenu);
    }
}
