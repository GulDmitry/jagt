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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Runs the scripted flows an acceptance read asked for and folds each result into its verdict.
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
                                ask.environment() + " is not an environment of " + settings.file()))
                .toList();
    AcceptanceVerdict merged = FlowRows.merge(verdict, ask, results);
    write(worktree.resolve(role.id() + "-report.md"), merged);
    return merged;
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
