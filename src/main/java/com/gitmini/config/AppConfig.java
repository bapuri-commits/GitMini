package com.gitmini.config;

import java.util.ArrayList;
import java.util.List;

/**
 * 앱 설정 모델.
 * Gson으로 직렬화/역직렬화되며, %APPDATA%/GitMini/config.json에 저장된다.
 * 기본 생성자는 모든 필드를 기본값으로 초기화한다.
 */
public class AppConfig {

    private List<String> repoPaths;
    private String theme;
    private int autoFetchIntervalMinutes;
    private double windowWidth;
    private double windowHeight;
    private double windowX;
    private double windowY;
    private String defaultClonePath;
    private String externalTerminal;

    /**
     * 기본값으로 초기화. Gson 역직렬화 시에도 사용된다.
     */
    public AppConfig() {
        this.repoPaths = new ArrayList<>();
        this.theme = "primer-dark";
        this.autoFetchIntervalMinutes = 5;
        this.windowWidth = 1200;
        this.windowHeight = 800;
        this.windowX = -1;  // -1 = 화면 중앙
        this.windowY = -1;
        this.defaultClonePath = "";
        this.externalTerminal = "cmd";
    }

    /**
     * Gson 역직렬화 후 null 필드를 기본값으로 복구한다.
     * Gson은 setter를 거치지 않고 필드를 직접 설정하므로,
     * JSON에 null이 들어 있으면 필드가 null이 될 수 있다.
     */
    public void validate() {
        if (repoPaths == null) repoPaths = new ArrayList<>();
        if (theme == null) theme = "primer-dark";
        if (defaultClonePath == null) defaultClonePath = "";
        if (externalTerminal == null) externalTerminal = "cmd";
        if (autoFetchIntervalMinutes <= 0) autoFetchIntervalMinutes = 5;
        if (windowWidth <= 0 || Double.isNaN(windowWidth)) windowWidth = 1200;
        if (windowHeight <= 0 || Double.isNaN(windowHeight)) windowHeight = 800;
        if (Double.isNaN(windowX)) windowX = -1;
        if (Double.isNaN(windowY)) windowY = -1;
    }

    // --- Getters & Setters ---

    public List<String> getRepoPaths() {
        return repoPaths;
    }

    public void setRepoPaths(List<String> repoPaths) {
        this.repoPaths = repoPaths != null ? repoPaths : new ArrayList<>();
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public int getAutoFetchIntervalMinutes() {
        return autoFetchIntervalMinutes;
    }

    public void setAutoFetchIntervalMinutes(int autoFetchIntervalMinutes) {
        this.autoFetchIntervalMinutes = autoFetchIntervalMinutes;
    }

    public double getWindowWidth() {
        return windowWidth;
    }

    public void setWindowWidth(double windowWidth) {
        this.windowWidth = windowWidth;
    }

    public double getWindowHeight() {
        return windowHeight;
    }

    public void setWindowHeight(double windowHeight) {
        this.windowHeight = windowHeight;
    }

    public double getWindowX() {
        return windowX;
    }

    public void setWindowX(double windowX) {
        this.windowX = windowX;
    }

    public double getWindowY() {
        return windowY;
    }

    public void setWindowY(double windowY) {
        this.windowY = windowY;
    }

    public String getDefaultClonePath() {
        return defaultClonePath;
    }

    public void setDefaultClonePath(String defaultClonePath) {
        this.defaultClonePath = defaultClonePath;
    }

    public String getExternalTerminal() {
        return externalTerminal;
    }

    public void setExternalTerminal(String externalTerminal) {
        this.externalTerminal = externalTerminal;
    }
}
