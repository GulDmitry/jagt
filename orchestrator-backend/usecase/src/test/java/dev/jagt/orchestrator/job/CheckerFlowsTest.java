package dev.jagt.orchestrator.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.RoleDefinitions;
import dev.jagt.orchestrator.port.AcceptanceRunner;
import dev.jagt.orchestrator.port.AcceptanceRunner.FlowResult;
import dev.jagt.orchestrator.port.AcceptanceRunner.Verdict;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.RoleSettings;
import dev.jagt.orchestrator.task.AcceptanceEnding;
import dev.jagt.orchestrator.task.AcceptanceVerdict;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckerFlowsTest {

  private static final String STAGE = "https://stage.example.com";

  @TempDir static Path root;

  private final AcceptanceRunner runner = mock(AcceptanceRunner.class);
  private final ConfigService config = mock(ConfigService.class);
  private final CheckerFlows flows =
      new CheckerFlows(
          runner,
          RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
          new RoleSettings(
              RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
              config,
              new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()))));

  private void stageListed() throws IOException {
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withRoles(Map.of("acceptance", new ConfigFile.RoleConfig(true, null))));
    Files.createDirectories(root.resolve("roles/acceptance"));
    Files.writeString(
        root.resolve("roles/acceptance/role.yml"), "environments: [{url: \"" + STAGE + "\"}]\n");
  }

  @Test
  void runsNothingAndAddsNoRowWhereTheReadNamedNoFlow(@TempDir Path worktree) {
    AcceptanceVerdict read =
        new AcceptanceVerdict("abc", "", List.of(), "", "", AcceptanceVerdict.Flows.none());

    AcceptanceVerdict result = flows.run(read, worktree, AcceptanceVerdict.Flows.none());

    assertThat(result).isSameAs(read);
    verify(runner, never()).run(any(), any(), any());
    assertThat(worktree.resolve("acceptance-report.md")).doesNotExist();
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
                    .withEvidence(List.of(Path.of("acceptance/report.json")))));
    AcceptanceVerdict read =
        new AcceptanceVerdict(
            "abc",
            "",
            List.of(
                new AcceptanceVerdict.Row("page opens", AcceptanceVerdict.Status.MET, "GET 200")),
            "",
            "",
            AcceptanceVerdict.Flows.none());

    AcceptanceVerdict result =
        flows.run(read, worktree, new AcceptanceVerdict.Flows(List.of(new AcceptanceVerdict.Flows.Flow("AC-1", "")), STAGE));

    assertThat(result.rows())
        .containsExactly(
            new AcceptanceVerdict.Row("page opens", AcceptanceVerdict.Status.MET, "GET 200"),
            new AcceptanceVerdict.Row(
                "AC-1", AcceptanceVerdict.Status.NOT_MET, "button missing acceptance/report.json"));
    assertThat(Files.readString(worktree.resolve("acceptance-report.md")))
        .contains(
            "| page opens | MET | GET 200 |",
            "| AC-1 | NOT_MET | button missing acceptance/report.json |");
  }

  @Test
  void aPassedFlowIsAMetRow(@TempDir Path worktree) throws IOException {
    stageListed();
    when(runner.run(worktree, List.of("AC-1"), STAGE))
        .thenReturn(List.of(FlowResult.defaults().withFlow("AC-1").withVerdict(Verdict.PASSED)));

    AcceptanceVerdict result =
        flows.run(
            new AcceptanceVerdict("abc", "", List.of(), "", "", AcceptanceVerdict.Flows.none()),
            worktree,
            new AcceptanceVerdict.Flows(List.of(new AcceptanceVerdict.Flows.Flow("AC-1", "")), STAGE));

    assertThat(result.rows())
        .containsExactly(new AcceptanceVerdict.Row("AC-1", AcceptanceVerdict.Status.MET, ""));
  }

  @Test
  void aPassedFlowMakesItsCriterionsUnprovableRowMetAndDropsTheQuestion(@TempDir Path worktree)
      throws IOException {
    stageListed();
    when(runner.run(worktree, List.of("open-page"), STAGE))
        .thenReturn(
            List.of(FlowResult.defaults().withFlow("open-page").withVerdict(Verdict.PASSED)));
    AcceptanceVerdict read =
        new AcceptanceVerdict(
            "abc",
            "",
            List.of(
                new AcceptanceVerdict.Row(
                    "C1", AcceptanceVerdict.Status.UNPROVABLE, "behind a login")),
            "How do I sign in?",
            "",
            AcceptanceVerdict.Flows.none());

    AcceptanceVerdict result =
        flows.run(
            read,
            worktree,
            new AcceptanceVerdict.Flows(
                List.of(new AcceptanceVerdict.Flows.Flow("open-page", "C1")), STAGE));

    assertThat(result.rows())
        .containsExactly(
            new AcceptanceVerdict.Row(
                "C1", AcceptanceVerdict.Status.MET, "flow open-page passed"));
    assertThat(result.question()).isEmpty();
    assertThat(result.ending()).isEqualTo(AcceptanceEnding.PASSED);
  }

  @Test
  void aFailedFlowMakesItsCriterionsRowNotMet(@TempDir Path worktree) throws IOException {
    stageListed();
    when(runner.run(worktree, List.of("open-page"), STAGE))
        .thenReturn(
            List.of(
                FlowResult.defaults()
                    .withFlow("open-page")
                    .withVerdict(Verdict.FAILED)
                    .withReason("no button")));
    AcceptanceVerdict read =
        new AcceptanceVerdict(
            "abc",
            "",
            List.of(
                new AcceptanceVerdict.Row("C1", AcceptanceVerdict.Status.MET, "the page loads")),
            "",
            "",
            AcceptanceVerdict.Flows.none());

    AcceptanceVerdict result =
        flows.run(
            read,
            worktree,
            new AcceptanceVerdict.Flows(
                List.of(new AcceptanceVerdict.Flows.Flow("open-page", "C1")), STAGE));

    assertThat(result.rows())
        .containsExactly(
            new AcceptanceVerdict.Row(
                "C1", AcceptanceVerdict.Status.NOT_MET, "flow open-page failed: no button"));
  }

  @Test
  void anUnprovableFlowLeavesTheReadsRowAndAddsItsReason(@TempDir Path worktree)
      throws IOException {
    stageListed();
    when(runner.run(worktree, List.of("open-page"), STAGE))
        .thenReturn(
            List.of(
                FlowResult.defaults()
                    .withFlow("open-page")
                    .withVerdict(Verdict.UNPROVABLE)
                    .withReason("no JSON report")));
    AcceptanceVerdict read =
        new AcceptanceVerdict(
            "abc",
            "",
            List.of(
                new AcceptanceVerdict.Row(
                    "C1", AcceptanceVerdict.Status.UNPROVABLE, "behind a login")),
            "How do I sign in?",
            "",
            AcceptanceVerdict.Flows.none());

    AcceptanceVerdict result =
        flows.run(
            read,
            worktree,
            new AcceptanceVerdict.Flows(
                List.of(new AcceptanceVerdict.Flows.Flow("open-page", "C1")), STAGE));

    assertThat(result.rows())
        .containsExactly(
            new AcceptanceVerdict.Row(
                "C1",
                AcceptanceVerdict.Status.UNPROVABLE,
                "behind a login | flow open-page unprovable: no JSON report"));
    assertThat(result.question()).isEqualTo("How do I sign in?");
  }

  @Test
  void eachOfTwoFlowsDecidesOnlyTheRowOfTheCriterionItNames(@TempDir Path worktree)
      throws IOException {
    stageListed();
    when(runner.run(worktree, List.of("page-a", "page-b"), STAGE))
        .thenReturn(
            List.of(
                FlowResult.defaults().withFlow("page-a").withVerdict(Verdict.PASSED),
                FlowResult.defaults().withFlow("page-b").withVerdict(Verdict.FAILED)));

    AcceptanceVerdict result =
        flows.run(
            new AcceptanceVerdict(
                "abc",
                "",
                List.of(
                    new AcceptanceVerdict.Row("C1", AcceptanceVerdict.Status.UNPROVABLE, "login"),
                    new AcceptanceVerdict.Row("C2", AcceptanceVerdict.Status.UNPROVABLE, "login")),
                "",
                "",
                AcceptanceVerdict.Flows.none()),
            worktree,
            new AcceptanceVerdict.Flows(
                List.of(
                    new AcceptanceVerdict.Flows.Flow("page-a", "C1"),
                    new AcceptanceVerdict.Flows.Flow("page-b", "C2")),
                STAGE));

    assertThat(result.rows())
        .extracting(AcceptanceVerdict.Row::status)
        .containsExactly(AcceptanceVerdict.Status.MET, AcceptanceVerdict.Status.NOT_MET);
  }

  @Test
  void aFlowNamingACriterionNoRowHasKeepsItsOwnRow(@TempDir Path worktree) throws IOException {
    stageListed();
    when(runner.run(worktree, List.of("open-page"), STAGE))
        .thenReturn(
            List.of(FlowResult.defaults().withFlow("open-page").withVerdict(Verdict.PASSED)));

    AcceptanceVerdict result =
        flows.run(
            new AcceptanceVerdict(
                "abc",
                "",
                List.of(new AcceptanceVerdict.Row("C1", AcceptanceVerdict.Status.MET, "ok")),
                "",
                "",
                AcceptanceVerdict.Flows.none()),
            worktree,
            new AcceptanceVerdict.Flows(
                List.of(new AcceptanceVerdict.Flows.Flow("open-page", "C9")), STAGE));

    assertThat(result.rows())
        .containsExactly(
            new AcceptanceVerdict.Row("C1", AcceptanceVerdict.Status.MET, "ok"),
            new AcceptanceVerdict.Row("open-page", AcceptanceVerdict.Status.MET, ""));
  }

  @Test
  void anEnvironmentNotListedIsRefusedBeforeAnyRun(@TempDir Path worktree) throws IOException {
    stageListed();

    AcceptanceVerdict result =
        flows.run(
            new AcceptanceVerdict("abc", "", List.of(), "", "", AcceptanceVerdict.Flows.none()),
            worktree,
            new AcceptanceVerdict.Flows(List.of(new AcceptanceVerdict.Flows.Flow("AC-1", "")), "https://prod.example.com"));

    assertThat(result.rows())
        .extracting(AcceptanceVerdict.Row::status)
        .containsExactly(AcceptanceVerdict.Status.UNPROVABLE);
    verify(runner, never()).run(any(), any(), any());
  }
}
