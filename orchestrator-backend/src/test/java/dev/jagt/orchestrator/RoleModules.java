package dev.jagt.orchestrator;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

final class RoleModules {

  private static final String ROOT = SourceRoots.BASE_PACKAGE + ".";

  static final List<String> ARBITER =
      Stream.of(
              "service.master.",
              "flow.Master",
              "flow.role.",
              "task.master.",
              "task.LegacyMaster",
              "job.master.",
              "job.verdict.Master",
              "command.master.",
              "config.Master",
              "startup.role.Master",
              "surface.mcp.refusal.MasterToken",
              "eval.Master")
          .map(prefix -> ROOT + prefix)
          .toList();

  static final Map<String, List<String>> CHECKERS =
      Map.of(
          "acceptance",
          Stream.of("eval.Acceptance", "startup.role.Acceptance", "task.acceptance.")
              .map(prefix -> ROOT + prefix)
              .toList());

  private RoleModules() {}
}
