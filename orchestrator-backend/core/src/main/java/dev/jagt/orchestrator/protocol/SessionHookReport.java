package dev.jagt.orchestrator.protocol;

import static dev.jagt.orchestrator.protocol.Wire.absent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * What a session's own harness says about it. The one message jagt does not shape: a hook posts
 * whatever the CLI handed it, and the hook swallows the answer, so a violation here is not a
 * correction anybody will read. jagt therefore DROPS what it cannot believe and records the rest.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SessionHookReport(
    @Nullable String transcriptPath,
    @Nullable String source,
    @Nullable String message,
    @JsonUnwrapped Ending ending,
    @JsonUnwrapped Turn turn)
    implements Message {

  public record Ending(
      @Nullable Boolean continuing, @Nullable String error, @Nullable String reason) {

    public Ending {
      error = absent(error);
      reason = absent(reason);
    }
  }

  public record Turn(
      @Nullable String prompt, @Nullable String toolName, List<Object> backgroundTasks) {

    public Turn {
      prompt = absent(prompt);
      toolName = absent(toolName);
      backgroundTasks = backgroundTasks == null ? List.of() : backgroundTasks;
    }
  }

  public SessionHookReport {
    transcriptPath = absent(transcriptPath);
    source = absent(source);
    message = absent(message);
    ending = ending == null ? new Ending(null, null, null) : ending;
    turn = turn == null ? new Turn(null, null, List.of()) : turn;
  }

  public SessionHookReport(
      @Nullable String transcriptPath, @Nullable String source, @Nullable String message) {
    this(
        transcriptPath,
        source,
        message,
        new Ending(null, null, null),
        new Turn(null, null, List.of()));
  }

  /** Nothing was posted at all, which is a state the address alone still reports. */
  public static SessionHookReport none() {
    return new SessionHookReport(null, null, null);
  }

  /** The turn ending was itself sent on by a refusal, so refusing again could never resolve. */
  public boolean sentOn() {
    return Boolean.TRUE.equals(ending.continuing());
  }

  /** What the human typed, which only a prompt hook carries; blank where it is not one. */
  public String typed() {
    return turn.prompt() == null ? "" : turn.prompt();
  }

  /** Work still in flight wakes the session back up: a pause, not a stop. */
  public boolean pausedOnBackgroundWork() {
    return !turn.backgroundTasks().isEmpty();
  }

  /**
   * A relative path is the one thing jagt must not believe: it would resolve against the
   * orchestrator's own directory and name a log some other process writes.
   */
  @Override
  public List<Violation> violations(MessageContext context) {
    return transcriptPath != null && !Path.of(transcriptPath).isAbsolute()
        ? List.of(
            new Violation(
                "transcriptPath",
                "an absolute path; '"
                    + transcriptPath
                    + "' would resolve against jagt's own directory"))
        : List.of();
  }

  /** Where this session writes its log, or empty where the harness named none jagt can follow. */
  public Optional<Path> sessionLog() {
    return violations(MessageContext.NONE).isEmpty() && transcriptPath != null
        ? Optional.of(Path.of(transcriptPath))
        : Optional.empty();
  }
}
