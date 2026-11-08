package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.RoleDefinition;
import dev.jagt.orchestrator.port.AcceptanceRunner;
import dev.jagt.orchestrator.port.AcceptanceRunner.FlowResult;
import dev.jagt.orchestrator.service.RoleSettings;
import dev.jagt.orchestrator.task.QaVerdict;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Runs the scripted flows a QA read asked for and folds each result into its verdict as a row. */
@Slf4j
@Component
@RequiredArgsConstructor
public class CheckerFlows {

  private final AcceptanceRunner runner;
  private final RoleDefinition role;
  private final RoleSettings settings;

  public QaVerdict run(QaVerdict verdict, Path worktree, QaVerdict.Flows ask) {
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
                            .withReason(ask.environment() + " is not in roles.qa.environments"))
                .toList();
    QaVerdict merged = verdict.plus(results.stream().map(CheckerFlows::row).toList());
    write(worktree.resolve(role.id() + "-report.md"), merged);
    return merged;
  }

  private static QaVerdict.Row row(FlowResult result) {
    QaVerdict.Status status =
        switch (result.verdict()) {
          case PASSED -> QaVerdict.Status.MET;
          case FAILED -> QaVerdict.Status.NOT_MET;
          case UNPROVABLE -> QaVerdict.Status.UNPROVABLE;
        };
    String paths = result.evidence().stream().map(Path::toString).collect(Collectors.joining(", "));
    return new QaVerdict.Row(
        result.flow(), status, (result.reason() + " " + paths).strip());
  }

  private void write(Path file, QaVerdict verdict) {
    try {
      Files.writeString(file, verdict.report(role.id()));
    } catch (IOException unwritten) {
      log.atWarn()
          .setMessage("qa report not written")
          .addKeyValue("file", file)
          .addKeyValue("cause", unwritten.toString())
          .log();
    }
  }
}
