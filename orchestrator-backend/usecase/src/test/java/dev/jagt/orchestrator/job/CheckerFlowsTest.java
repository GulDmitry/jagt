package dev.jagt.orchestrator.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.RoleDefinitions;
import dev.jagt.orchestrator.port.AcceptanceRunner;
import dev.jagt.orchestrator.port.AcceptanceRunner.FlowResult;
import dev.jagt.orchestrator.port.AcceptanceRunner.Verdict;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.RoleSettings;
import dev.jagt.orchestrator.task.QaVerdict;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckerFlowsTest {

  private static final String STAGE = "https://stage.example.com";

  private final AcceptanceRunner runner = mock(AcceptanceRunner.class);
  private final ConfigService config = mock(ConfigService.class);
  private final CheckerFlows flows =
      new CheckerFlows(
          runner,
          RoleDefinitions.fromFile(Path.of("../../roles/qa/role.yml.dist")),
          new RoleSettings(RoleDefinitions.fromFile(Path.of("../../roles/qa/role.yml.dist")), config));

  private void stageListed() {
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withQa(
                    new ConfigFile.QaConfig(
                        true, null, List.of(new ConfigFile.QaEnvironment(STAGE, null)))));
  }

  @Test
  void runsNothingAndAddsNoRowWhereTheReadNamedNoFlow(@TempDir Path worktree) {
    QaVerdict read = new QaVerdict("abc", "", List.of(), "", "", QaVerdict.Flows.none());

    QaVerdict result = flows.run(read, worktree, QaVerdict.Flows.none());

    assertThat(result).isSameAs(read);
    verify(runner, never()).run(any(), any(), any());
    assertThat(worktree.resolve("qa-report.md")).doesNotExist();
  }

  @Test
  void aFailedFlowIsANotMetRowInTheVerdictAndTheReport(@TempDir Path worktree) throws IOException {
    stageListed();
    when(runner.run(worktree, List.of("AC-1"), STAGE))
        .thenReturn(
            List.of(
                FlowResult.defaults()
                    .withFlow("AC-1")
                    .withVerdict(Verdict.FAILED)
                    .withReason("button missing")
                    .withEvidence(List.of(Path.of("qa/report.json")))));
    QaVerdict read =
        new QaVerdict(
            "abc",
            "",
            List.of(new QaVerdict.Row("page opens", QaVerdict.Status.MET, "GET 200")),
            "",
            "",
            QaVerdict.Flows.none());

    QaVerdict result = flows.run(read, worktree, new QaVerdict.Flows(List.of("AC-1"), STAGE));

    assertThat(result.rows())
        .containsExactly(
            new QaVerdict.Row("page opens", QaVerdict.Status.MET, "GET 200"),
            new QaVerdict.Row("AC-1", QaVerdict.Status.NOT_MET, "button missing qa/report.json"));
    assertThat(Files.readString(worktree.resolve("qa-report.md")))
        .contains("| page opens | MET | GET 200 |", "| AC-1 | NOT_MET | button missing qa/report.json |");
  }

  @Test
  void aPassedFlowIsAMetRow(@TempDir Path worktree) {
    stageListed();
    when(runner.run(worktree, List.of("AC-1"), STAGE))
        .thenReturn(
            List.of(FlowResult.defaults().withFlow("AC-1").withVerdict(Verdict.PASSED)));

    QaVerdict result =
        flows.run(
            new QaVerdict("abc", "", List.of(), "", "", QaVerdict.Flows.none()),
            worktree,
            new QaVerdict.Flows(List.of("AC-1"), STAGE));

    assertThat(result.rows()).containsExactly(new QaVerdict.Row("AC-1", QaVerdict.Status.MET, ""));
  }

  @Test
  void anEnvironmentNotListedIsRefusedBeforeAnyRun(@TempDir Path worktree) {
    stageListed();

    QaVerdict result =
        flows.run(
            new QaVerdict("abc", "", List.of(), "", "", QaVerdict.Flows.none()),
            worktree,
            new QaVerdict.Flows(List.of("AC-1"), "https://prod.example.com"));

    assertThat(result.rows())
        .extracting(QaVerdict.Row::status)
        .containsExactly(QaVerdict.Status.UNPROVABLE);
    verify(runner, never()).run(any(), any(), any());
  }
}
