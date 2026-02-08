package com.gitmini.git.parser;

import com.gitmini.model.FileChange;
import com.gitmini.model.FileChange.ChangeType;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code git status --porcelain} 출력을 파싱한다.
 * <p>
 * porcelain v1 포맷:
 * <pre>
 *  M README.md          (working tree: modified)
 * M  src/Main.java      (index: modified)
 * MM both.txt           (index + working tree 둘 다 modified)
 * A  new.txt            (index: added)
 * D  deleted.txt        (index: deleted)
 * R  old.txt -> new.txt (index: renamed)
 * ?? untracked.txt      (untracked)
 * </pre>
 * <ul>
 *   <li>첫 번째 문자(X): staging area(index) 상태</li>
 *   <li>두 번째 문자(Y): working tree 상태</li>
 *   <li>세 번째 문자: 항상 공백</li>
 *   <li>나머지: 파일 경로 (rename 시 "old -> new")</li>
 * </ul>
 * </p>
 */
public class StatusParser {

    /**
     * git status --porcelain 출력을 FileChange 리스트로 파싱한다.
     *
     * @param output git status --porcelain 출력 (null-safe)
     * @return 변경된 파일 목록. staged와 unstaged가 분리되어 들어간다.
     */
    public List<FileChange> parse(String output) {
        if (output == null || output.isBlank()) {
            return List.of();
        }

        List<FileChange> changes = new ArrayList<>();
        String[] lines = output.split("\n");

        for (String line : lines) {
            if (line.length() < 4) continue; // 최소: "XY P" (4자)

            char indexStatus = line.charAt(0);    // staging area (X)
            char workTreeStatus = line.charAt(1); // working tree (Y)
            String path = line.substring(3);      // "XY " 이후

            // Rename: "R  old.txt -> new.txt" → new.txt만 사용
            String displayPath = path;
            if (path.contains(" -> ")) {
                displayPath = path.substring(path.indexOf(" -> ") + 4);
            }

            // Staged 변경 (index column에 변경이 있는 경우)
            if (indexStatus != ' ' && indexStatus != '?') {
                changes.add(new FileChange(displayPath, mapChangeType(indexStatus), true));
            }

            // Unstaged 변경 (work tree column에 변경이 있는 경우)
            if (workTreeStatus != ' ') {
                if (workTreeStatus == '?') {
                    // '?' 는 untracked — index와 work tree 둘 다 '?'
                    changes.add(new FileChange(displayPath, ChangeType.UNTRACKED, false));
                } else {
                    changes.add(new FileChange(displayPath, mapChangeType(workTreeStatus), false));
                }
            }
        }

        return changes;
    }

    private ChangeType mapChangeType(char code) {
        return switch (code) {
            case 'M' -> ChangeType.MODIFIED;
            case 'A' -> ChangeType.ADDED;
            case 'D' -> ChangeType.DELETED;
            case 'R' -> ChangeType.RENAMED;
            case 'C' -> ChangeType.COPIED;
            case '?' -> ChangeType.UNTRACKED;
            default -> ChangeType.MODIFIED; // 알 수 없는 코드는 MODIFIED로 폴백
        };
    }
}
