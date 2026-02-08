package com.gitmini.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 메인 화면 컨트롤러.
 * 사이드바 (레포 목록) + 메인 콘텐츠 영역 + 상태바를 관리한다.
 */
public class MainController {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    // --- FXML 바인딩 ---

    @FXML
    private SplitPane mainSplitPane;

    @FXML
    private VBox sidebar;

    @FXML
    private ListView<String> repoListView;

    @FXML
    private VBox mainContent;

    @FXML
    private Label welcomeLabel;

    @FXML
    private HBox statusBar;

    @FXML
    private Label statusLabel;

    @FXML
    private Label branchLabel;

    // --- 초기화 ---

    @FXML
    public void initialize() {
        log.info("MainController 초기화");

        // Phase 1: 기본 초기화만 수행
        // Phase 3에서 레포 목록 로드, 이벤트 구독 등 추가 예정
    }

    // --- 사이드바 액션 ---

    @FXML
    private void onAddRepo() {
        log.info("레포 추가 버튼 클릭");
        // TODO: Phase 3 — DirectoryChooser로 폴더 선택
        statusLabel.setText("레포 추가 — Phase 3에서 구현 예정");
    }

    @FXML
    private void onCloneRepo() {
        log.info("Clone 버튼 클릭");
        // TODO: Phase 4 — Clone 다이얼로그
        statusLabel.setText("Clone — Phase 4에서 구현 예정");
    }
}
