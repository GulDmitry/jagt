package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.RoleDefinition;
import dev.jagt.orchestrator.port.AcceptanceRunner;
import dev.jagt.orchestrator.port.AcceptanceRunner.FlowResult;
import dev.jagt.orchestrator.service.RoleSettings;
import dev.jagt.orchestrator.task.AcceptanceVerdict;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Runs the scripted flows an acceptance read asked for and folds each result into its verdict as a
 * row.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CheckerFlows {

  private final AcceptanceRunner runner;
  private final RoleDefinition role;
  private final RoleSettings settings;

  public AcceptanceVerdict run(
      AcceptanceVerdict verdict, Path worktree, AcceptanceVerdict.Flows ask) {
    if (ask.ids().isEmpty()) {
      return verdict;
    }
    List<FlowResult> results =
        settings.load().allows(ask.environment())
            ? runner.run(worktree, ask.ids(), ask.environment())
            : ask.ids().stream()
                .map(
                    flow ->
                        FlowResult.defaults()
                            .withFlow(flow)
                            .withReason(
                                ask.environment() + " is not in roles.acceptance.environments"))
                .toList();
    AcceptanceVerdict merged = verdict.plus(results.stream().map(CheckerFlows::row).toList());
    write(worktree.resolve(role.id() + "-report.md"), merged);
    return merged;
  }

  private static AcceptanceVerdict.Row row(FlowResult result) {
    AcceptanceVerdict.Status status =
        switch (result.verdict()) {
          case PASSED -> AcceptanceVerdict.Status.MET;
          case FAILED -> AcceptanceVerdict.Status.NOT_MET;
          case UNPROVABLE -> AcceptanceVerdict.Status.UNPROVABLE;
        };
    String paths = result.evidence().stream().map(Path::toString).collect(Collectors.joining(", "));
    return new AcceptanceVerdict.Row(
        result.flow(), status, (result.reason() + " " + paths).strip());
  }

  private void write(Path file, AcceptanceVerdict verdict) {
    try {
      Files.writeString(file, verdict.report(role.id()));
    } catch (IOException unwritten) {
      log.atWarn()
          .setMessage("acceptance report not written")
          .addKeyValue("file", file)
          .addKeyValue("cause", unwritten.toString())
          .log();
    }
  }
}
