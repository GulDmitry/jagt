package dev.jagt.orchestrator.service.review;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.service.ConfigFile.CodeReviewConfig;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.plan.Artifact;
import dev.jagt.orchestrator.task.turn.Turn;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Whether a task is carrying answers a human still has to see. Presence alone cannot say: the file
 * outlives the round it was written in. It will NOT remove the file — "posted, file not cleaned up"
 * and "never posted" are the same bytes on disk, and announcing nothing is recoverable where
 * unlinking is not.
 */
@Service
@RequiredArgsConstructor
public class ReviewDrafts {

  private final ConfigService configService;

  /**
   * Announced where a ship would still send it, and wherever no ship ever will, whatever the
   * status. {@code status} is the one the task is being MOVED to, which is not always the one it
   * still carries.
   */
  public static boolean pending(TaskState task, TaskStatus status, CodeReviewConfig config) {
    boolean shipPostsEveryDraft = config.shipPostsEveryDraft();
    if (shipPostsEveryDraft && !FlowRules.handsBack(status)) {
      return false;
    }
    Path file = draftsIn(task.worktreePath());
    return file != null && Files.isRegularFile(file) && !spent(file, task, shipPostsEveryDraft);
  }

  /** The same for a caller with no configuration in hand. */
  public boolean pending(TaskState task, TaskStatus status) {
    return pending(task, status, configService.load().codeReview());
  }

  /** When the replies file was last written; 0 where there is none or its time cannot be read. */
  public static long writtenAt(TaskState task) {
    Path file = draftsIn(task.worktreePath());
    try {
      return file != null && Files.isRegularFile(file)
          ? Files.getLastModifiedTime(file).toMillis()
          : 0;
    } catch (IOException e) {
      return 0;
    }
  }

  /**
   * Answered by a ship that has already happened: the drafts are older than the round now open.
   * Never true where the answers are not jagt's to spend.
   */
  public boolean spent(TaskState task) {
    Path file = draftsIn(task.worktreePath());
    return file != null && Files.isRegularFile(file) && spent(file, task, postsEveryDraft());
  }

  /** A timestamp that cannot be read leaves the file announced: whoever opens it loses nothing. */
  private static boolean spent(Path file, TaskState task, boolean shipPostsEveryDraft) {
    if (!shipPostsEveryDraft) {
      return false;
    }
    try {
      return Files.getLastModifiedTime(file).toMillis() < task.round().mrCreatedAt();
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Drafts the human reads, posts or ends: a block no ship posts, or a person's reply the Master
   * may not post unasked. The code reviewer's blocks are no draft.
   */
  public boolean heldForHuman(TaskState task) {
    return drafted(task) && readBlocks(task).blocks().stream().anyMatch(this::heldForHuman);
  }

  private boolean heldForHuman(ReplyBlocks.Block block) {
    CodeReviewConfig config = configService.load().codeReview();
    return switch (block.kind()) {
      case REVIEWER -> false;
      case UNKNOWN -> true;
      case BOT -> !postedByShip(block, config);
      case PERSON -> !postedByShip(block, config) || !config.repliesToPeopleUnaskedOrDefault();
    };
  }

  private static boolean postedByShip(ReplyBlocks.Block block, CodeReviewConfig config) {
    String author = block.author().toLowerCase(Locale.ROOT);
    return config.postReviewRepliesOrDefault()
        && (config.reviewReplyAuthorsOrEmpty().isEmpty()
            || config.reviewReplyAuthorsOrEmpty().stream()
                .anyMatch(wanted -> author.contains(wanted.toLowerCase(Locale.ROOT))));
  }

  /**
   * Replies written and not yet spent by a ship; a blank file, and a file of the code reviewer's
   * blocks alone, hold none.
   */
  public boolean drafted(TaskState task) {
    Path file = draftsIn(task.worktreePath());
    try {
      return file != null
          && Files.isRegularFile(file)
          && !spent(file, task, postsEveryDraft())
          && ReplyBlocks.parse(Files.readString(file)).blocks().stream()
              .anyMatch(block -> block.kind() != ReplyBlocks.Kind.REVIEWER);
    } catch (IOException e) {
      return true;
    }
  }

  private ReplyBlocks readBlocks(TaskState task) {
    try {
      return ReplyBlocks.parse(Files.readString(draftsIn(task.worktreePath())));
    } catch (IOException e) {
      return new ReplyBlocks(List.of(new ReplyBlocks.Block(ReplyBlocks.Kind.PERSON, "")));
    }
  }

  /**
   * What a ship would send of the drafts: their digest while {@link #drafted} holds, none
   * otherwise.
   */
  public Optional<String> unspentDigest(TaskState task) {
    if (!drafted(task)) {
      return Optional.empty();
    }
    try {
      return Optional.of(Turn.digestOf(Files.readString(draftsIn(task.worktreePath()))));
    } catch (IOException e) {
      return Optional.of("unreadable");
    }
  }

  private boolean postsEveryDraft() {
    return configService.load().codeReview().shipPostsEveryDraft();
  }

  private static @Nullable Path draftsIn(@Nullable String worktree) {
    return worktree == null || worktree.isBlank()
        ? null
        : Path.of(worktree).resolve(Artifact.REPLIES.fileName());
  }
}
