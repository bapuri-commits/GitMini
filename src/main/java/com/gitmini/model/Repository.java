package com.gitmini.model;

import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * 등록된 Git 레포지토리의 런타임 정보.
 * path와 name은 불변, 나머지는 git 상태에 따라 갱신된다.
 */
public class Repository {

    private final String name;
    private final String path;

    private String currentBranch;
    private int changedFileCount;
    private String lastCommitMessage;
    private LocalDateTime lastCommitDate;
    private int ahead;
    private int behind;

    public Repository(String path) {
        this.path = path;
        this.name = Path.of(path).getFileName().toString();
        this.currentBranch = "";
        this.changedFileCount = 0;
        this.lastCommitMessage = "";
        this.lastCommitDate = null;
        this.ahead = 0;
        this.behind = 0;
    }

    // --- Immutable fields ---

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    // --- Mutable fields ---

    public String getCurrentBranch() {
        return currentBranch;
    }

    public void setCurrentBranch(String currentBranch) {
        this.currentBranch = currentBranch;
    }

    public int getChangedFileCount() {
        return changedFileCount;
    }

    public void setChangedFileCount(int changedFileCount) {
        this.changedFileCount = changedFileCount;
    }

    public String getLastCommitMessage() {
        return lastCommitMessage;
    }

    public void setLastCommitMessage(String lastCommitMessage) {
        this.lastCommitMessage = lastCommitMessage;
    }

    public LocalDateTime getLastCommitDate() {
        return lastCommitDate;
    }

    public void setLastCommitDate(LocalDateTime lastCommitDate) {
        this.lastCommitDate = lastCommitDate;
    }

    public int getAhead() {
        return ahead;
    }

    public void setAhead(int ahead) {
        this.ahead = ahead;
    }

    public int getBehind() {
        return behind;
    }

    public void setBehind(int behind) {
        this.behind = behind;
    }

    /**
     * 레포 상태가 clean인지 여부 (변경 파일 없음).
     */
    public boolean isClean() {
        return changedFileCount == 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Repository that = (Repository) o;
        return path.equals(that.path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public String toString() {
        return String.format("Repository{name='%s', branch='%s', changes=%d}",
                name, currentBranch, changedFileCount);
    }
}
