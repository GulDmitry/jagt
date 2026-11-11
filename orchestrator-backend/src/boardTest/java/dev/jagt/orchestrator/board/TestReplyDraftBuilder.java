package dev.jagt.orchestrator.board;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.With;

@With
public record TestReplyDraftBuilder(Path root, String key, String text) {

  public static TestReplyDraftBuilder replyDraft(Path root) {
    return new TestReplyDraftBuilder(root, "ABC-1", "## thread 1\nFIXED - Renamed it.\n");
  }

  public Path build() throws IOException {
    Path worktree = Files.createDirectories(root.resolve(key + "-alpha"));
    Files.writeString(worktree.resolve("review_replies.md"), text);
    return worktree;
  }
}
