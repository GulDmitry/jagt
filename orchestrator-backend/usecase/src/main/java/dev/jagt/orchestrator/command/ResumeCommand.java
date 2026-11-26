package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.service.TaskLauncher;
import dev.jagt.orchestrator.service.launch.PendingStarts;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ResumeCommand implements GlobalCommand {

  private static final String USAGE = "usage: resume <request-url> | <start-key> [discard]";

  private final TaskLauncher launcher;
  private final PendingStarts starts;

  @Override
  public String id() {
    return "resume";
  }

  @Override
  public int rank() {
    return 80;
  }

  @Override
  public String hint() {
    return "reopened request: take over its branch and its commits, and link it — no second"
        + " request; or a start a restart cut short: run it again, or discard it";
  }

  @Override
  public Optional<String> whyNotTheArbiter() {
    return Optional.of("a task opens from a command of yours or the tracker, never from a model");
  }

  @Override
  public Optional<String> modelHint() {
    return Optional.of(
        "take over an EXISTING review request / merge request — its branch and its commits — when"
            + " the human gives such a URL (the `ticket` field carries the URL)");
  }

  @Override
  public String runProposed(String target, String reason) {
    if (!target.startsWith("http")) {
      return "Understood as `"
          + id()
          + "` but no review-request URL was named ("
          + reason
          + ") — say it explicitly: `resume <request-url>`.";
    }
    return "understood as `" + id() + " " + target + "` — " + launcher.resume(target).message();
  }

  @Override
  public List<String> usage() {
    return List.of("resume <request-url>", "resume <start-key>", "resume <start-key> discard");
  }

  @Override
  public String run(String tail) {
    List<String> tokens =
        Arrays.stream(tail.split("\\s+")).filter(token -> !token.isBlank()).toList();
    boolean request = tokens.stream().anyMatch(token -> token.startsWith("http"));
    return request ? resumeRequest(tokens) : cutShortStart(tokens);
  }

  /**
   * The request names its own branches, so anything typed beside its URL could only contradict it.
   */
  private String resumeRequest(List<String> tokens) {
    if (tokens.size() > 1) {
      throw new IllegalArgumentException(
          "usage: resume <request-url> — the request carries its own branches;"
              + " to start a NEW task on a new branch use `do <ticket>`");
    }
    return DoCommand.created(launcher.resume(tokens.getFirst()));
  }

  private String cutShortStart(List<String> tokens) {
    boolean discard = tokens.size() == 2 && tokens.getLast().equals("discard");
    if (tokens.size() != 1 && !discard) {
      throw new IllegalArgumentException(USAGE);
    }
    String key = tokens.getFirst();
    if (starts.find(key).isEmpty()) {
      throw new IllegalArgumentException(USAGE);
    }
    if (discard) {
      starts.end(key);
      return "start " + key + " discarded";
    }
    return DoCommand.created(launcher.resumeStart(key));
  }
}
