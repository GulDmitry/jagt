package dev.jagt.orchestrator.job.checker;

import dev.jagt.orchestrator.config.role.RoleOptions;
import dev.jagt.orchestrator.flow.CheckerPhase;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.capability.Watch;
import dev.jagt.orchestrator.flow.session.StopKind;
import dev.jagt.orchestrator.job.TaskJob;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.service.role.RoleSettings;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.acceptance.AcceptanceDecline;
import dev.jagt.orchestrator.task.acceptance.AcceptanceEnding;
import dev.jagt.orchestrator.task.acceptance.AcceptanceVerdict;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.turn.AgentReport;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Reads the session's no-change reply to an acceptance round back to acceptance: it accepts, or
 * disputes. Once a round and a first dispute are behind, a reply is read once more; a second
 * dispute goes to the human, and nothing is read after it.
 */
@Service
@RequiredArgsConstructor
public class CheckerDeclineJob implements TaskJob {

  private final RoleSettings settings;
  private final CheckerPrompts prompts;
  private final CheckerReads reads;
  private final CheckerRecords records;
  private final CheckerOutcomes outcomes;

  @Override
  public String id() {
    return settings.role().id() + "-decline";
  }

  @Override
  public Serves serves() {
    return Serves.TASKS;
  }

  @Override
  public String describe() {
    return "read the session's answer to an acceptance round that changed nothing back to"
        + " acceptance";
  }

  @Override
  public Duration every() {
    return Duration.ofMinutes(1);
  }

  @Override
  public Watch watch() {
    return Watch.defaults()
        .withStatuses(FlowRules::checkedWhereItLanded)
        .withStops(StopKind.NONE)
        .withReports(report -> report == AgentReport.NO_CHANGES);
  }

  @Override
  public void run(String taskId, TaskState task) {
    RoleOptions acceptance = settings.load();
    String role = settings.role().id();
    CheckerPhase reading = CheckerPhase.of(task, role);
    Optional<CheckerRecords.Entry> asked =
        records.latest(taskId).filter(record -> answered(task, role, record));
    if (asked.isEmpty()
        || !acceptance.enabled()
        || task.worktreePath() == null
        || (task.turnOrNone().open() && reading != CheckerPhase.READING)) {
      return;
    }
    String sha = asked.get().sha();
    String rows = task.roleOrNone(role).roundRows();
    String step =
        reading == CheckerPhase.READING ? AcceptanceVerdict.AGAIN : AcceptanceVerdict.DECLINE;
    Answer<AcceptanceDecline> answer =
        reads.decline(
            taskId,
            step,
            asked.get().digest(),
            prompts.decline(
                taskId,
                task.worktreePath(),
                sha,
                acceptance,
                rows,
                String.valueOf(task.message())));
    AcceptanceDecline decline =
        answer.facts().orElse(AcceptanceDecline.failed(sha, answer.failure()));
    AcceptanceEnding ending =
        outcomes.declined(taskId, decline, asked.get().ending() == AcceptanceEnding.DISPUTED);
    records.put(taskId, asked.get().digest(), sha, ending);
  }

  /** A round or a first dispute of this deploy stands, and the session replied after it was put. */
  private static boolean answered(TaskState task, String role, CheckerRecords.Entry record) {
    Deployment deployment = task.deployment();
    return deployment != null
        && record.digest().equals(deployment.askedDigest())
        && task.roundOpen(role)
        && (record.ending() == AcceptanceEnding.NOT_MET
            || record.ending() == AcceptanceEnding.DISPUTED)
        && task.session().reportedAt() > record.at();
  }
}
