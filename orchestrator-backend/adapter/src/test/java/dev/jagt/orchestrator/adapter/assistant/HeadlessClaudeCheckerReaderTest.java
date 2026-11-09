package dev.jagt.orchestrator.adapter.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.adapter.agent.ClaudeProperties;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.config.RoleDefinitions;
import dev.jagt.orchestrator.port.CheckerReader;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.service.ReadScopes.ReadScope;
import dev.jagt.orchestrator.service.UsageTracker;
import dev.jagt.orchestrator.task.AcceptanceDecline;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class HeadlessClaudeCheckerReaderTest {

  private final ProcessRunner runner = mock(ProcessRunner.class);
  private final ReadOnlyTools reads = mock(ReadOnlyTools.class);
  private final UsageTracker usage = mock(UsageTracker.class);
  private final HeadlessClaudeCheckerReader reader =
      new HeadlessClaudeCheckerReader(
          new HeadlessClaude(
              new AssistantProcess(
                  runner,
                  new AssistantBreaker(
                      AssistantProperties.empty(),
                      Clock.systemUTC(),
                      new dev.jagt.orchestrator.service.ReachLedger())),
              ClaudeProperties.defaults(),
              AssistantProperties.empty(),
              usage,
              reads),
          ClaudeProperties.defaults(),
          AssistantProperties.empty(),
          List.of(RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist"))));

  @TempDir Path worktree;

  @Test
  void holdsNoWriteToolAndCurlOnlyAsAQuietGetSoNothingItReadsCanChangeTheEnvironmentOrTheTree() {
    when(runner.run(any(Path.class), any(Duration.class), any()))
        .thenReturn(
            new Processes.Result(0, "{\"structured_output\":{\"failure\":\"\",\"rows\":[]}}", ""));

    reader.read(
        new CheckerReader.Run(
            "",
            "check ABC-42",
            worktree,
            "abc123",
            "",
            new CheckerReader.Reach(List.of(), List.of(), Optional.empty()),
            RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist"))
                .replies()
                .answer()
                .json(),
            "acceptance"));

    ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
    verify(runner).run(any(Path.class), any(Duration.class), command.capture());
    List<String> argv = command.getValue();
    assertThat(argv.get(argv.indexOf("--tools") + 1)).isEqualTo("Read,Grep,Glob,Bash,WebFetch");
    assertThat(argv.subList(argv.indexOf("--allowedTools"), argv.size()))
        .contains("WebFetch", "Bash(curl -sS:*)", "Bash(git log:*)")
        .noneMatch(
            tool ->
                tool.startsWith("Write")
                    || tool.startsWith("Bash(curl") && !tool.equals("Bash(curl -sS:*)"));
    assertThat(argv.subList(argv.indexOf("--disallowedTools"), argv.indexOf("--allowedTools")))
        .contains("Write", "Edit", "Bash(git * --output*)");
  }

  @Test
  void letsAWebFetchReachOnlyTheEnvironmentsTheRunNames() {
    when(runner.run(any(Path.class), any(Duration.class), any()))
        .thenReturn(
            new Processes.Result(0, "{\"structured_output\":{\"failure\":\"\",\"rows\":[]}}", ""));

    reader.read(
        new CheckerReader.Run(
            "",
            "check ABC-42",
            worktree,
            "abc123",
            "",
            new CheckerReader.Reach(
                List.of("https://dev.example.test", "https://dev.example.test/version"),
                List.of(),
                Optional.empty()),
            RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist"))
                .replies()
                .answer()
                .json(),
            "acceptance"));

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
    when(runner.run(any(Path.class), any(Duration.class), any()))
        .thenReturn(
            new Processes.Result(0, "{\"structured_output\":{\"failure\":\"\",\"rows\":[]}}", ""));

    reader.read(
        new CheckerReader.Run(
            "",
            "check ABC-42",
            worktree,
            "abc123",
            "",
            new CheckerReader.Reach(List.of(), List.of(flows), Optional.empty()),
            RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist"))
                .replies()
                .answer()
                .json(),
            "acceptance"));

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
    when(runner.run(any(Path.class), any(Duration.class), any()))
        .thenReturn(
            new Processes.Result(
                0,
                "{\"structured_output\":{\"failure\":\"\",\"rows\":[{\"criterion\":\"page opens\","
                    + "\"status\":\"met\",\"evidence\":\"GET answered 200\"}]}}",
                ""));

    var answer =
        reader.read(
            new CheckerReader.Run(
                "",
                "check ABC-42",
                worktree,
                "abc123",
                "",
                new CheckerReader.Reach(List.of(), List.of(), Optional.empty()),
                RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist"))
                    .replies()
                    .answer()
                    .json(),
                "acceptance"));

    assertThat(Files.readString(worktree.resolve("acceptance-report.md")))
        .contains("abc123", "page opens", "GET answered 200");
    assertThat(answer.facts().orElseThrow().rows())
        .extracting(row -> row.criterion())
        .containsExactly("page opens");
  }

  @Test
  void readsTheDisputeAcceptanceFilledAgainstASessionsDecline() {
    when(runner.run(any(Path.class), any(Duration.class), any()))
        .thenReturn(
            new Processes.Result(
                0,
                "{\"structured_output\":{\"failure\":\"\",\"dispute\":\"GET /list is unsorted\"}}",
                ""));

    AcceptanceDecline decline =
        reader
            .reconsider(
                new CheckerReader.Run(
                    "",
                    "decide",
                    worktree,
                    "abc123",
                    "",
                    new CheckerReader.Reach(List.of(), List.of(), Optional.empty()),
                    RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist"))
                        .replies()
                        .decline()
                        .json(),
                    "acceptance"))
            .facts()
            .orElseThrow();

    assertThat(decline).isEqualTo(new AcceptanceDecline("abc123", "", "GET /list is unsorted"));
  }

  @Test
  void passesTheSharedPromptAndTheModelAndPinsNoMcpServerWhereNoneIsListed() {
    when(runner.run(any(Path.class), any(Duration.class), any()))
        .thenReturn(
            new Processes.Result(0, "{\"structured_output\":{\"failure\":\"\",\"rows\":[]}}", ""));

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

    ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
    verify(runner).run(any(Path.class), any(Duration.class), command.capture());
    assertThat(command.getValue())
        .containsSequence("--append-system-prompt", "be brief")
        .containsSequence("--model", "opus")
        .containsSequence("--strict-mcp-config", "--mcp-config", "{\"mcpServers\":{}}");
  }

  @Test
  void givesABrowserServerTheRoleNamesItsReadsNavigationAndLooksAndNoClick() {
    when(runner.run(any(Path.class), any(Duration.class), any()))
        .thenReturn(
            new Processes.Result(0, "{\"structured_output\":{\"failure\":\"\",\"rows\":[]}}", ""));
    HeadlessClaudeCheckerReader browsing =
        new HeadlessClaudeCheckerReader(
            new HeadlessClaude(
                new AssistantProcess(
                    runner,
                    new AssistantBreaker(
                        AssistantProperties.empty(),
                        Clock.systemUTC(),
                        new dev.jagt.orchestrator.service.ReachLedger())),
                ClaudeProperties.defaults(),
                AssistantProperties.empty(),
                usage,
                reads),
            ClaudeProperties.defaults(),
            AssistantProperties.empty(),
            List.of(
                RoleDefinitions.compile(
                    "id: acceptance\nname: acceptance\nmodel: m\ntrigger: [deployed]\n"
                        + "oncePer: deploy\noutcomes: [round]\ntools:\n  mcp: [mcp__browser]\n"
                        + "answer:\n  - required: failure\n")));

    browsing.read(
        new CheckerReader.Run(
            "",
            "check ABC-42",
            worktree,
            "abc123",
            "",
            new CheckerReader.Reach(List.of(), List.of(), Optional.empty()),
            "{}",
            "acceptance"));

    ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
    verify(runner).run(any(Path.class), any(Duration.class), command.capture());
    List<String> argv = command.getValue();
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
  void passesNeitherASharedPromptNorAModelWhereTheRunNamesNone() {
    when(runner.run(any(Path.class), any(Duration.class), any()))
        .thenReturn(
            new Processes.Result(0, "{\"structured_output\":{\"failure\":\"\",\"rows\":[]}}", ""));

    reader.read(
        new CheckerReader.Run(
            "",
            "check ABC-42",
            worktree,
            "abc123",
            "",
            new CheckerReader.Reach(List.of(), List.of(), Optional.empty()),
            RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist"))
                .replies()
                .answer()
                .json(),
            "acceptance"));

    ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
    verify(runner).run(any(Path.class), any(Duration.class), command.capture());
    assertThat(command.getValue()).doesNotContain("--append-system-prompt", "--model");
  }

  @Test
  void refusesARunOfARoleNoInstalledDefinitionNames() {
    var run =
        new CheckerReader.Run(
            "",
            "check ABC-42",
            worktree,
            "abc123",
            "",
            new CheckerReader.Reach(List.of(), List.of(), Optional.empty()),
            "{}",
            "nope");

    assertThatThrownBy(() -> reader.read(run)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void carriesTheFlowIdsAndTheEnvironmentTheCheckNamed() {
    when(runner.run(any(Path.class), any(Duration.class), any()))
        .thenReturn(
            new Processes.Result(
                0,
                "{\"structured_output\":{\"failure\":\"\",\"rows\":[],"
                    + "\"flows\":[\"AC-1\",\"AC-2\"],\"environment\":\"https://alpha.test\"}}",
                ""));

    var verdict =
        reader
            .read(
                new CheckerReader.Run(
                    "",
                    "check ABC-42",
                    worktree,
                    "abc123",
                    "",
                    new CheckerReader.Reach(List.of(), List.of(), Optional.empty()),
                    RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist"))
                        .replies()
                        .answer()
                        .json(),
                    "acceptance"))
            .facts()
            .orElseThrow();

    assertThat(verdict.flows().ids()).containsExactly("AC-1", "AC-2");
    assertThat(verdict.flows().environment()).isEqualTo("https://alpha.test");
  }

  @Test
  void failsTheVerdictNamingTheCauseWhenTheCheckAnsweredNoEnvelope() {
    when(runner.run(any(Path.class), any(Duration.class), any()))
        .thenReturn(new Processes.Result(1, "", "boom"));

    var verdict =
        reader
            .read(
                new CheckerReader.Run(
                    "",
                    "check ABC-42",
                    worktree,
                    "abc123",
                    "",
                    new CheckerReader.Reach(List.of(), List.of(), Optional.empty()),
                    RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist"))
                        .replies()
                        .answer()
                        .json(),
                    "acceptance"))
            .facts()
            .orElseThrow();

    assertThat(verdict.failure()).startsWith("the check failed");
  }

  @Test
  void failsTheDeclineNamingTheCauseWhenTheCheckAnsweredNoEnvelope() {
    when(runner.run(any(Path.class), any(Duration.class), any()))
        .thenReturn(new Processes.Result(1, "", "boom"));

    AcceptanceDecline decline =
        reader
            .reconsider(
                new CheckerReader.Run(
                    "",
                    "decide",
                    worktree,
                    "abc123",
                    "",
                    new CheckerReader.Reach(List.of(), List.of(), Optional.empty()),
                    RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist"))
                        .replies()
                        .decline()
                        .json(),
                    "acceptance"))
            .facts()
            .orElseThrow();

    assertThat(decline.failure()).startsWith("the check failed");
  }

  @Test
  void withAWorkspaceTheRunWritesOnlyThereUnderDontAsk(@TempDir Path workspace) {
    when(runner.run(any(Path.class), any(Duration.class), any()))
        .thenReturn(
            new Processes.Result(0, "{\"structured_output\":{\"failure\":\"\",\"rows\":[]}}", ""));

    reader.read(
        new CheckerReader.Run(
            "",
            "check ABC-42",
            worktree,
            "abc123",
            "",
            new CheckerReader.Reach(
                List.of(),
                List.of(workspace),
                Optional.of(new CheckerReader.Workspace(workspace, List.of()))),
            RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist"))
                .replies()
                .answer()
                .json(),
            "acceptance"));

    ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
    verify(runner).run(any(Path.class), any(Duration.class), command.capture());
    List<String> argv = command.getValue();
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
    when(runner.run(any(Path.class), any(Duration.class), any()))
        .thenReturn(
            new Processes.Result(0, "{\"structured_output\":{\"failure\":\"\",\"rows\":[]}}", ""));

    reader.read(
        new CheckerReader.Run(
            "",
            "check ABC-42",
            worktree,
            "abc123",
            "",
            new CheckerReader.Reach(
                List.of(),
                List.of(workspace),
                Optional.of(new CheckerReader.Workspace(workspace, List.of()))),
            RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist"))
                .replies()
                .answer()
                .json(),
            "acceptance"));

    verify(runner).run(eq(workspace), any(Duration.class), any());
  }
}
