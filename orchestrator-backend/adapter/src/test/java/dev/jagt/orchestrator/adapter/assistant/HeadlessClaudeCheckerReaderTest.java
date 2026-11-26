package dev.jagt.orchestrator.adapter.assistant;

import static dev.jagt.orchestrator.adapter.assistant.TestCheckerRunBuilder.checkerRun;
import static dev.jagt.orchestrator.adapter.assistant.TestHeadlessClaudeBuilder.headlessClaude;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.jagt.orchestrator.adapter.FakeProcesses;
import dev.jagt.orchestrator.adapter.agent.ClaudeProperties;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.role.RoleDefinitions;
import dev.jagt.orchestrator.port.assistant.CheckerReader;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.service.gate.ReadScopes.ReadScope;
import dev.jagt.orchestrator.service.role.InstalledRoles;
import dev.jagt.orchestrator.service.usage.UsageTracker;
import dev.jagt.orchestrator.task.acceptance.AcceptanceDecline;
import dev.jagt.orchestrator.task.acceptance.AcceptanceVerdict;
import dev.jagt.orchestrator.testing.FakeHost;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HeadlessClaudeCheckerReaderTest {

  private final FakeProcesses runner =
      new FakeProcesses()
          .answering(
              new Processes.Result(
                  0, "{\"structured_output\":{\"failure\":\"\",\"rows\":[]}}", ""));
  private final ReadOnlyTools reads = mock(ReadOnlyTools.class);
  private final UsageTracker usage = mock(UsageTracker.class);
  private final HeadlessClaudeCheckerReader reader =
      new HeadlessClaudeCheckerReader(
          headlessClaude().build(runner, usage, reads),
          ClaudeProperties.defaults(),
          AssistantProperties.empty(),
          new InstalledRoles(
              new OrchestratorPaths(
                  OrchestratorProperties.defaults().withRoot("../.."), FakeHost.machine())));

  private final String answerSchema =
      RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist"))
          .replies()
          .answer()
          .json();
  private final String declineSchema =
      RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist"))
          .replies()
          .decline()
          .json();

  @TempDir Path worktree;

  @Test
  void holdsNoWriteToolAndCurlOnlyAsQuietGetSoNothingItReadsCanChangeTheEnvironmentOrTheTree() {

    reader.read(checkerRun().withWorktree(worktree).withSchema(answerSchema).build());

    List<String> argv = runner.command();
    assertThat(argv.get(argv.indexOf("--tools") + 1)).isEqualTo("Read,Grep,Glob,Bash,WebFetch");
    assertThat(argv.subList(argv.indexOf("--allowedTools"), argv.size()))
        .contains("WebFetch", "Bash(curl -sS:*)", "Bash(git log:*)")
        .noneMatch(tool ->
            tool.startsWith("Write")
                || (tool.startsWith("Bash(curl") && !tool.equals("Bash(curl -sS:*)")));
    assertThat(argv.subList(argv.indexOf("--disallowedTools"), argv.indexOf("--allowedTools")))
        .contains("Write", "Edit", "Bash(git * --output*)");
  }

  @Test
  void letsWebFetchReachOnlyTheEnvironmentsTheRunNames() {

    reader.read(
        checkerRun()
            .withWorktree(worktree)
            .withSchema(answerSchema)
            .withReach(
                new CheckerReader.Reach(
                    List.of("https://dev.example.test", "https://dev.example.test/version"),
                    List.of(),
                    Optional.empty()))
            .build());

    verify(reads)
        .fence(
            any(),
            eq(
                new ReadScope(
                    List.of(worktree),
                    List.of("StructuredOutput"),
                    true,
                    List.of(),
                    List.of("https://dev.example.test", "https://dev.example.test/version"))));
  }

  @Test
  void letsTheReadReachTheFlowsRepositoryBesideItsWorktree(@TempDir Path flows) {

    reader.read(
        checkerRun()
            .withWorktree(worktree)
            .withSchema(answerSchema)
            .withReach(new CheckerReader.Reach(List.of(), List.of(flows), Optional.empty()))
            .build());

    verify(reads)
        .fence(
            any(),
            eq(
                new ReadScope(
                    List.of(worktree, flows),
                    List.of("StructuredOutput"),
                    true,
                    List.of(),
                    List.of())));
  }

  @Test
  void writesAcceptanceReportFromTheRowsTheModelAnswered() throws IOException {
    runner.answering(
        new Processes.Result(
            0,
            "{\"structured_output\":{\"failure\":\"\",\"rows\":[{\"criterion\":\"page opens\","
                + "\"status\":\"met\",\"evidence\":\"GET answered 200\"}]}}",
            ""));

    var answer = reader.read(checkerRun().withWorktree(worktree).withSchema(answerSchema).build());

    assertThat(Files.readString(worktree.resolve("acceptance-report.md")))
        .contains("abc123", "page opens", "GET answered 200");
    assertThat(answer.facts().orElseThrow().rows())
        .extracting(row -> row.criterion())
        .containsExactly("page opens");
  }

  @Test
  void readsTheDisputeAcceptanceFilledAgainstSessionsDecline() {
    runner.answering(
        new Processes.Result(
            0,
            "{\"structured_output\":{\"failure\":\"\",\"dispute\":\"GET /list is unsorted\"}}",
            ""));

    AcceptanceDecline decline =
        reader
            .reconsider(
                checkerRun()
                    .withPrompt("decide")
                    .withWorktree(worktree)
                    .withSchema(declineSchema)
                    .build())
            .facts()
            .orElseThrow();

    assertThat(decline).isEqualTo(new AcceptanceDecline("abc123", "", "GET /list is unsorted"));
  }

  @Test
  void passesTheSharedPromptAndTheModelAndPinsNoMcpServerWhereNoneIsListed() {

    reader.read(
        new CheckerReader.Run(
            "be brief",
            "check ABC-42",
            worktree,
            "abc123",
            "opus",
            new CheckerReader.Reach(List.of(), List.of(), Optional.empty()),
            RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist"))
                .replies()
                .answer()
                .json(),
            "acceptance"));

    List<String> command = runner.command();
    assertThat(command)
        .containsSequence("--append-system-prompt", "be brief")
        .containsSequence("--model", "opus")
        .containsSequence("--strict-mcp-config", "--mcp-config", "{\"mcpServers\":{}}");
  }

  @Test
  void givesBrowserServerTheRoleNamesItsReadsNavigationAndLooksAndNoClick(@TempDir Path root)
      throws IOException {
    Files.createDirectories(root.resolve("roles/acceptance"));
    Files.writeString(
        root.resolve("roles/acceptance/role.yml"),
        """
        id: acceptance
        name: acceptance
        model: m
        trigger: [deployed]
        oncePer: deploy
        outcomes: [round]
        tools:
          mcp: [mcp__browser]
        answer:
          - required: failure
        """);
    InstalledRoles installed =
        new InstalledRoles(
            new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString()), FakeHost.machine()));
    HeadlessClaudeCheckerReader browsing =
        new HeadlessClaudeCheckerReader(
            headlessClaude().build(runner, usage, reads),
            ClaudeProperties.defaults(),
            AssistantProperties.empty(),
            installed);

    browsing.read(checkerRun().withWorktree(worktree).build());

    List<String> argv = runner.command();
    assertThat(argv).doesNotContain("--strict-mcp-config");
    assertThat(argv.subList(argv.indexOf("--allowedTools"), argv.size()))
        .contains(
            "mcp__browser__list*",
            "mcp__browser__get*",
            "mcp__browser__navigate_page",
            "mcp__browser__take_snapshot",
            "mcp__browser__take_screenshot");
    assertThat(argv.subList(argv.indexOf("--disallowedTools"), argv.indexOf("--allowedTools")))
        .contains("mcp__*__click*", "mcp__*__fill*", "mcp__*__evaluate*")
        .doesNotContain("mcp__*__navigate*");
  }

  @Test
  void passesNeitherSharedPromptNorModelWhereTheRunNamesNone() {

    reader.read(checkerRun().withWorktree(worktree).withSchema(answerSchema).build());

    List<String> command = runner.command();
    assertThat(command).doesNotContain("--append-system-prompt", "--model");
  }

  @Test
  void refusesRunOfRoleNoInstalledDefinitionNames() {
    var run = checkerRun().withWorktree(worktree).withRole("nope").build();

    assertThatThrownBy(() -> reader.read(run)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void carriesTheFlowsWithTheirCriteriaAndTheEnvironmentTheCheckNamed() {
    runner.answering(
        new Processes.Result(
            0,
            "{\"structured_output\":{\"failure\":\"\",\"rows\":[],"
                + "\"flows\":[{\"id\":\"AC-1\",\"criterion\":\"C1\"},"
                + "{\"id\":\"AC-2\",\"criterion\":\"\"}],\"environment\":\"https://alpha.test\"}}",
            ""));

    var verdict =
        reader
            .read(checkerRun().withWorktree(worktree).withSchema(answerSchema).build())
            .facts()
            .orElseThrow();

    assertThat(verdict.flows().named())
        .containsExactly(
            new AcceptanceVerdict.Flows.Flow("AC-1", "C1"),
            new AcceptanceVerdict.Flows.Flow("AC-2", ""));
    assertThat(verdict.flows().environment()).isEqualTo("https://alpha.test");
  }

  @Test
  void failsTheVerdictNamingTheCauseWhenTheCheckAnsweredNoEnvelope() {
    runner.answering(new Processes.Result(1, "", "boom"));

    var verdict =
        reader
            .read(checkerRun().withWorktree(worktree).withSchema(answerSchema).build())
            .facts()
            .orElseThrow();

    assertThat(verdict.failure()).startsWith("the check failed");
  }

  @Test
  void failsTheDeclineNamingTheCauseWhenTheCheckAnsweredNoEnvelope() {
    runner.answering(new Processes.Result(1, "", "boom"));

    AcceptanceDecline decline =
        reader
            .reconsider(
                checkerRun()
                    .withPrompt("decide")
                    .withWorktree(worktree)
                    .withSchema(declineSchema)
                    .build())
            .facts()
            .orElseThrow();

    assertThat(decline.failure()).startsWith("the check failed");
  }

  @Test
  void withWorkspaceTheRunWritesOnlyThereUnderDontAsk(@TempDir Path workspace) {

    reader.read(
        checkerRun()
            .withWorktree(worktree)
            .withSchema(answerSchema)
            .withReach(
                new CheckerReader.Reach(
                    List.of(),
                    List.of(workspace),
                    Optional.of(new CheckerReader.Workspace(workspace, List.of()))))
            .build());

    List<String> argv = runner.command();
    assertThat(argv.get(argv.indexOf("--permission-mode") + 1)).isEqualTo("dontAsk");
    assertThat(argv.subList(argv.indexOf("--allowedTools"), argv.size()))
        .contains("Write(/" + workspace + "/**)", "Edit(/" + workspace + "/**)")
        .doesNotContain("Write", "Edit");
    assertThat(argv.subList(argv.indexOf("--disallowedTools"), argv.indexOf("--allowedTools")))
        .contains("NotebookEdit")
        .doesNotContain("Write", "Edit");
  }

  @Test
  void theRunStartsInTheWorkspaceWhenOneIsHeld(@TempDir Path workspace) {

    reader.read(
        checkerRun()
            .withWorktree(worktree)
            .withSchema(answerSchema)
            .withReach(
                new CheckerReader.Reach(
                    List.of(),
                    List.of(workspace),
                    Optional.of(new CheckerReader.Workspace(workspace, List.of()))))
            .build());

    assertThat(runner.directories()).containsExactly(workspace);
  }
}
