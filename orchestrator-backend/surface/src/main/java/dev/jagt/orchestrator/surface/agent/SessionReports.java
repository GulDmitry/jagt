package dev.jagt.orchestrator.surface.agent;

import dev.jagt.orchestrator.flow.SessionSign;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.service.SessionProbe;
import dev.jagt.orchestrator.service.TaskFacts;
import dev.jagt.orchestrator.task.TaskState;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Everything one report from a session's own harness does: the sign of life, the verdict on it,
 * what the session has spent, and the one line handed back to a session that has just lost its
 * context.
 */
@Service
@RequiredArgsConstructor
public class SessionReports {

  private final SessionSigns signs;
  private final AgentRuntime runtime;
  private final TurnEnds turnEnds;

  /**
   * Everything a harness hands a hook that jagt can use, which is not the state. {@code sessionLog}
   * is the file the session appends to, otherwise derived; {@code startedBy} why it started, in the
   * CLI's own word; {@code said} what the CLI told the human; {@code task} the task it runs for;
   * {@code endedBy} why a session ended; {@code failedOn} the error a turn ended on; {@code
   * toolName} the tool a permission prompt asks for. Each null where nothing was named.
   */
  public record Report(
      @Nullable Path sessionLog,
      @Nullable String startedBy,
      @Nullable String said,
      @Nullable TaskState task,
      @Nullable String endedBy,
      @Nullable String failedOn,
      @Nullable String toolName) {

    /** Everything absent: a harness may hand a hook nothing jagt can use, and most of them do. */
    public static Report defaults() {
      return new Report(null, null, null, null, null, null, null);
    }

    public Report withSessionLog(@Nullable Path sessionLog) {
      return new Report(sessionLog, startedBy, said, task, endedBy, failedOn, toolName);
    }

    public Report withStartedBy(@Nullable String startedBy) {
      return new Report(sessionLog, startedBy, said, task, endedBy, failedOn, toolName);
    }

    public Report withSaid(@Nullable String said) {
      return new Report(sessionLog, startedBy, said, task, endedBy, failedOn, toolName);
    }

    public Report withTask(@Nullable TaskState task) {
      return new Report(sessionLog, startedBy, said, task, endedBy, failedOn, toolName);
    }

    public Report withEndedBy(@Nullable String endedBy) {
      return new Report(sessionLog, startedBy, said, task, endedBy, failedOn, toolName);
    }

    public Report withFailedOn(@Nullable String failedOn) {
      return new Report(sessionLog, startedBy, said, task, endedBy, failedOn, toolName);
    }

    public Report withToolName(@Nullable String toolName) {
      return new Report(sessionLog, startedBy, said, task, endedBy, failedOn, toolName);
    }
  }

  /** The brief for a session that has just been compacted, or empty for every other report. */
  public String record(String taskId, SessionProbe.State state, Report report) {
    signs.record(taskId, report.sessionLog(), signOf(state, report));
    return brief(taskId, report.startedBy(), report.task());
  }

  /** Records the session as idle and asking for permission, naming the tool where the CLI did. */
  public void permissionAsked(String taskId, Report report) {
    signs.record(taskId, report.sessionLog(), signOf(SessionProbe.State.IDLE, report));
    String tool = report.toolName();
    signs.asked(
        taskId,
        new SessionSign.Asked(
            tool == null ? "permission" : "permission to use " + tool,
            SessionSign.Asked.Channel.HOOK));
  }

  /**
   * Records the turn as failed on the error the CLI named, and says whether that error is a usage
   * limit.
   */
  public boolean turnFailed(String taskId, Report report) {
    signs.record(taskId, report.sessionLog(), new SessionSign.TurnFailed(report.failedOn()));
    String limit = runtime.rateLimitError();
    return report.failedOn() != null
        && limit != null
        && !limit.isBlank()
        && limit.equalsIgnoreCase(report.failedOn().strip());
  }

  public String turnEnded(
      String taskId, Report report, boolean sentOn, boolean pausedOnBackgroundWork) {
    record(
        taskId,
        pausedOnBackgroundWork ? SessionProbe.State.WORKING : SessionProbe.State.IDLE,
        report);
    return turnEnds.answer(taskId, report.task(), sentOn, pausedOnBackgroundWork);
  }

  private boolean continuing(SessionProbe.State state, @Nullable String endedBy) {
    return state == SessionProbe.State.GONE
        && endedBy != null
        && runtime.continuingEnds().stream()
            .anyMatch(reason -> reason.equalsIgnoreCase(endedBy.strip()));
  }

  private SessionSign signOf(SessionProbe.State state, Report report) {
    return switch (state) {
      case WORKING -> new SessionSign.WorkStarted();
      case GONE ->
          continuing(state, report.endedBy())
              ? new SessionSign.Restarted(report.endedBy())
              : new SessionSign.Ended(report.endedBy());
      default ->
          new SessionSign.TurnEnded(
              report.sessionLog() == null ? null : report.sessionLog().toString());
    };
  }

  /**
   * A compaction drops the brief and the task's facts silently: the rules are named, the facts
   * restated.
   */
  private String brief(String taskId, @Nullable String startedBy, @Nullable TaskState task) {
    String compacted = runtime.compactedStart();
    if (compacted == null
        || compacted.isBlank()
        || startedBy == null
        || !compacted.equalsIgnoreCase(startedBy.strip())) {
      return "";
    }
    return "You are jagt's sub-agent for "
        + taskId
        + TaskFacts.of(task)
        + ". The task is its branch, "
        + taskId
        + ". In this worktree task_request.md is what the task was opened with and task_context.md"
        + " the instruction standing now: re-read both, and the repository's own agent"
        + " instructions, before doing anything else.\n";
  }
}
