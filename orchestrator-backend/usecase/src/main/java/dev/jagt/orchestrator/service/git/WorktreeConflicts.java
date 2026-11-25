package dev.jagt.orchestrator.service.git;

import dev.jagt.orchestrator.task.deploy.Conflict;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** What a conflicted worktree still holds: its unmerged paths and where its resolution stands. */
@Component
@RequiredArgsConstructor
class WorktreeConflicts {

  private final GitCommands git;

  String unmergedPaths(Path worktree) {
    return git.run(worktree, List.of("git", "diff", "--name-only", "--diff-filter=U"))
        .expectSuccess("git unmerged paths in " + worktree)
        .stdout()
        .trim();
  }

  Conflict.Progress progress(Path worktree) {
    String status =
        git.run(worktree, List.of("git", "status", "--porcelain"))
            .expectSuccess("git status in " + worktree)
            .stdout();
    String diff =
        git.run(worktree, List.of("git", "diff")).expectSuccess("git diff in " + worktree).stdout();
    List<String> paths = status.lines().filter(line -> line.length() > 2).toList();
    int unmerged = (int) paths.stream().filter(WorktreeConflicts::unmerged).count();
    int staged =
        (int)
            paths.stream()
                .filter(line -> !unmerged(line) && "MADRC".indexOf(line.charAt(0)) >= 0)
                .count();
    return new Conflict.Progress(unmerged, staged, sha256(status + "\0" + diff));
  }

  private static boolean unmerged(String statusLine) {
    String code = statusLine.substring(0, 2);
    return code.indexOf('U') >= 0 || code.equals("AA") || code.equals("DD");
  }

  private static String sha256(String text) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException missing) {
      throw new IllegalStateException(missing);
    }
  }
}
