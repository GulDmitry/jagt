package dev.jagt.orchestrator.job.codereview.request;

import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.review.ReplyBlocks;
import dev.jagt.orchestrator.service.worktree.WorktreeFiles;
import dev.jagt.orchestrator.task.plan.Artifact;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * What the session answered to a note of the code reviewer on its own request: the verdict word of
 * the block of {@code review_replies.md} whose heading links the note. A note no block links, and a
 * request no task holds, have none.
 */
@Component
@RequiredArgsConstructor
public class ReviewResolutions {

  private static final Pattern LINK =
      Pattern.compile("#(?:note_|discussion_r|issuecomment-|r)?(\\d+)\\b");
  private static final Pattern VERDICT = Pattern.compile("(?m)^(FIXED|NO CHANGE|QUESTION)\\b");

  private final StateService stateService;

  /** The verdict per note number, as {@code fixed}, {@code no-change} or {@code question}. */
  public Map<String, String> answered(LedgerRow row) {
    return replies(row).map(ReviewResolutions::parse).orElse(Map.of());
  }

  static Map<String, String> parse(String text) {
    Map<String, String> answers = new LinkedHashMap<>();
    Matcher blocks = ReplyBlocks.BLOCK.matcher(text);
    while (blocks.find()) {
      Matcher link = LINK.matcher(blocks.group(1));
      Matcher verdict = VERDICT.matcher(blocks.group(2));
      if (link.find() && verdict.find()) {
        answers.putIfAbsent(
            link.group(1), verdict.group(1).toLowerCase(Locale.ROOT).replace(' ', '-'));
      }
    }
    return answers;
  }

  /** The note id as the host's own number, without a driver's prefix. */
  public static String number(String noteId) {
    return noteId.replaceFirst("^\\D+-", "");
  }

  private Optional<String> replies(LedgerRow row) {
    if (row.taskId().isBlank()) {
      return Optional.empty();
    }
    return stateService
        .task(row.taskId())
        .flatMap(task ->
            task.repos().stream()
                .filter(repo -> row.project().equals(repo.project()))
                .map(TaskRepo::worktreePath)
                .filter(worktree -> worktree != null && !worktree.isBlank())
                .findFirst())
        .flatMap(
            worktree -> WorktreeFiles.read(Path.of(worktree).resolve(Artifact.REPLIES.fileName())));
  }
}
