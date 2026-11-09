package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Why jagt stopped a task for the human, and when; a human's lift clears it, or a new request
 * replacing the one it was counted on, or, where its trigger ends on {@code HOST_READ}, a read of
 * the checks that shows the host well. {@code request} holds that request's URLs, blank-separated;
 * null where none was counted on. {@code triage} is the read of this stop alone, null until made.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskStop(
    Trigger trigger,
    long at,
    String evidence,
    @Nullable String request,
    @Nullable StopTriage triage) {

  public TaskStop(Trigger trigger, long at, String evidence) {
    this(trigger, at, evidence, null, null);
  }

  public TaskStop withTriage(StopTriage read) {
    return new TaskStop(trigger, at, evidence, request, read);
  }

  /**
   * {@code reason} is the stop in five words or fewer; {@code next} is what a human reads after the
   * evidence.
   */
  @RequiredArgsConstructor
  public enum Trigger {
    STALLED(
        "rounds bring nothing new",
        "Give the session what the rounds lacked; if nothing was missing, report a jagt defect",
        Ends.LIFT,
        true),
    SHIPS(
        "ships with no new note",
        "Check a new note or commit waits to ship; if one does, report a jagt defect",
        Ends.LIFT,
        true),
    MISS(
        "no case for this step",
        "Set off by jagt or the Master: report a jagt defect; by a session: add a case if one"
            + " should send it",
        Ends.LIFT,
        false),
    ROUNDS(
        "same round sent too often",
        "Answer the session's question, or give it what it lacked",
        Ends.LIFT,
        false),
    PACE(
        "too many unattended moves in an hour",
        "Read the last hour's moves: say what each one changed",
        Ends.LIFT,
        false),
    CHECKS(
        "checks cannot be read",
        "Check that the code host's MCP can list the pipelines",
        Ends.HOST_READ,
        true),
    UNDELIVERED(
        "a step never landed",
        "Check that the session's window is running",
        Ends.SESSION_ALIVE,
        false),
    UNCHANGED_FAILURE(
        "same failure again",
        "Change the tree, or fix the check itself: its access or a file it needs",
        Ends.LIFT,
        true),
    INFRA(
        "checks failed on infrastructure",
        "Re-run the job or fix the runner, then lift",
        Ends.HOST_READ,
        true),
    WAIT(
        "a dependency stayed down",
        "Bring up what the session named, then lift",
        Ends.SESSION_REPORT,
        false),
    @JsonAlias("QA_FAILED")
    ACCEPTANCE_FAILED(
        "the acceptance check failed",
        "Fix what the check needs, then lift: the same check runs once more",
        Ends.LIFT,
        false),
    DEPLOY_UNREAD(
        "deploy pipeline cannot be read",
        "Check that the code host's CLI can list the deploy commit's pipelines",
        Ends.DEPLOY_READ,
        false),
    PAUSE("you paused it", "Look at what you paused it for", Ends.PAUSE, false);

    /**
     * What ends a stop: a human's lift, a read of the checks that shows the outside condition gone,
     * the session's window running again, a report of the session that is no wait, a read of the
     * deploy's pipeline, or, for a human's pause, only the human's lift.
     */
    public enum Ends {
      LIFT,
      HOST_READ,
      SESSION_ALIVE,
      SESSION_REPORT,
      DEPLOY_READ,
      PAUSE
    }

    private final String reason;
    private final String next;
    private final Ends ends;
    private final boolean countedOnRequest;

    public Ends ends() {
      return ends;
    }

    public String reason() {
      return reason;
    }

    public String next() {
      return next;
    }
  }

  /** The stop counted on {@code requests}, when its trigger counts on a request at all. */
  public TaskStop countedOn(List<String> requests) {
    return trigger.countedOnRequest && !requests.isEmpty()
        ? new TaskStop(trigger, at, evidence, String.join(" ", requests), triage)
        : this;
  }

  /** True where the request this stop was counted on is no longer among {@code requests}. */
  public boolean voidedBy(List<String> requests) {
    return request != null && !requests.containsAll(Arrays.asList(request.split(" ")));
  }

  /**
   * True where this stop was counted on {@code requested}, or on no request, or {@code requested}
   * is blank.
   */
  public boolean countedOnOrNone(String requested) {
    return request == null
        || requested.isBlank()
        || Arrays.asList(request.split(" ")).contains(requested);
  }

  /** Names this stop alone: the same trigger set again later is another. */
  public String key() {
    return trigger + "@" + at;
  }

  public String reading() {
    return evidence + ". " + trigger.next + ", then lift or close";
  }
}
