package dev.jagt.orchestrator;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class RoleModules {

  private static final String ROOT = SourceRoots.BASE_PACKAGE + ".";

  public static final List<String> ARBITER =
      Stream.of(
              "service.master.",
              "flow.Master",
              "flow.role.",
              "task.master.",
              "task.LegacyMaster",
              "job.master.",
              "job.tracker.TicketPrefetchJob",
              "job.verdict.Master",
              "command.master.",
              "config.Master",
              "startup.role.Master",
              "surface.mcp.refusal.MasterToken",
              "eval.Master")
          .map(prefix -> ROOT + prefix)
          .toList();

  public static final Map<String, List<String>> CHECKERS =
      Map.of(
          "acceptance",
          Stream.of(
                  "eval.Acceptance",
                  "startup.role.Acceptance",
                  "task.acceptance.",
                  "task.LegacyQa",
                  "task.LegacyRoles",
                  "job.checker.",
                  "service.scenario.",
                  "service.reach.CheckerReach",
                  "port.assistant.CheckerReader",
                  "protocol.read.RowStatusRead",
                  "command.ScenarioCommand",
                  "capability.ScenariosCapability",
                  "job.session.ScenarioEndingJob")
              .map(prefix -> ROOT + prefix)
              .toList(),
          "triage",
          List.of(ROOT + "job.triage."),
          "code-reviewer",
          Stream.of(
                  "job.codereview.",
                  "service.CodeReviewSettings",
                  "port.codehost.review.",
                  "startup.role.CodeReviewer")
              .map(prefix -> ROOT + prefix)
              .toList());

  public static final List<String> STOP_KIND =
      Stream.of(
              "task.stop.StopTriage",
              "task.stop.TaskStop",
              "flow.TaskReducer",
              "flow.view.TaskView",
              "service.stop.TaskStops")
          .map(prefix -> ROOT + prefix)
          .toList();

  public static final List<String> CHECKER_KIND =
      Stream.of(
              "flow.Checkers",
              "flow.CheckerRegion",
              "flow.Composition",
              "flow.capability.Facts",
              "flow.capability.TaskAction",
              "task.turn.ActionOrigin",
              "task.turn.Step",
              "task.usage.AssistantCallKind",
              "task.review.LoopBreaker",
              "service.OriginContext",
              "config.role.CaseBook",
              "startup.role.CaseCheck",
              "protocol.plan.PlanCriteria")
          .map(prefix -> ROOT + prefix)
          .toList();

  // The master's stand-in keeps its persisted keys, yml keys and file names under the role's own
  // id.
  public static final List<String> STAND_IN_KIND =
      Stream.of(
              "task.TaskState",
              "task.session.Work",
              "task.StoredTask",
              "task.FinishedTask",
              "task.plan.Artifact",
              "port.host.Layout",
              "protocol.debug.RecordTail",
              "service.ConfigService",
              "service.ConfigFile",
              "config.source.ConfigSource",
              "flow.Move",
              "flow.Turns",
              "flow.TaskChart",
              "flow.Phases",
              "flow.TaskMove",
              "flow.TaskEffect",
              "flow.session.StopHold",
              "service.CaseDoor",
              "service.project.ProjectSetup",
              "service.gate.ToolGate",
              "job.conflict.ConflictFinish")
          .map(prefix -> ROOT + prefix)
          .toList();

  private RoleModules() {}
}
