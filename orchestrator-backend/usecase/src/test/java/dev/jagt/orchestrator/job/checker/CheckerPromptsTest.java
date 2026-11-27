package dev.jagt.orchestrator.job.checker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.role.RoleDefinitions;
import dev.jagt.orchestrator.config.role.RoleFiles;
import dev.jagt.orchestrator.config.role.RoleOptions;
import dev.jagt.orchestrator.port.assistant.CheckerReader;
import dev.jagt.orchestrator.service.assistant.TicketTexts;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.acceptance.AcceptanceVerdict;
import dev.jagt.orchestrator.testing.FakeClock;
import dev.jagt.orchestrator.testing.FakeHost;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckerPromptsTest {

  @TempDir Path root;
  private final RoleOptions devAndStage =
      RoleOptions.defaults()
          .withEnvironments(
              List.of(
                  new RoleOptions.Environment(
                      "https://dev.example.test", "https://dev.example.test/version"),
                  new RoleOptions.Environment("https://stage.example.test", "")));
  private final TicketTexts tickets = mock(TicketTexts.class);
  private final FakeClock clock = new FakeClock();
  private CheckerPrompts prompts;

  @BeforeEach
  void promptsOverTheAcceptanceRole() {
    prompts =
        new CheckerPrompts(
            RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
            new RoleFiles(
                new OrchestratorPaths(
                    OrchestratorProperties.defaults().withRoot(root.toString()),
                    FakeHost.machine()),
                "acceptance"),
            tickets,
            clock);
  }

  @Test
  void theBriefCarriesTheTicketTextJagtReadAtIntake() {
    when(tickets.of("ABC-42")).thenReturn(Optional.of("Sort the list by name."));

    CheckerReader.Run run =
        prompts.accept("ABC-42", "/wt", "abc123", devAndStage, AcceptanceVerdict.ACCEPT);

    assertThat(run.prompt()).contains("<ticket>\nSort the list by name.\n</ticket>");
  }

  @Test
  void theBriefCarriesNoTicketSectionWhereNoTextWasRead() {
    when(tickets.of("ABC-42")).thenReturn(Optional.empty());

    CheckerReader.Run run =
        prompts.accept("ABC-42", "/wt", "abc123", devAndStage, AcceptanceVerdict.ACCEPT);

    assertThat(run.prompt()).doesNotContain("<ticket>");
  }

  @Test
  void theTicketIsReadOnceMoreAndNamedMissingWhereNoTextCameOfIt() {
    when(tickets.of("ABC-42")).thenReturn(Optional.empty());
    TaskState withTicket =
        TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED)
            .ticketUrl("https://t.example/ABC-42")
            .build();

    Optional<String> missing = prompts.missingInput("ABC-42", withTicket);

    assertThat(missing).contains("ticket");
    verify(tickets).read(eq("ABC-42"), eq("https://t.example/ABC-42"), anyLong());
  }

  @Test
  void theTicketReadOnceMoreIsEnoughWhereItKeptTheText() {
    when(tickets.of("ABC-42"))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of("Sort the list by name."));
    TaskState withTicket =
        TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED)
            .ticketUrl("https://t.example/ABC-42")
            .build();

    Optional<String> missing = prompts.missingInput("ABC-42", withTicket);

    assertThat(missing).isEmpty();
  }

  @Test
  void theTicketIsNotReadAgainWithinTenMinutesOfAnUnusableRead() {
    when(tickets.of("ABC-42")).thenReturn(Optional.empty());
    when(tickets.readSince("ABC-42", clock.millis() - 600_000L)).thenReturn(true);
    TaskState withTicket =
        TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED)
            .ticketUrl("https://t.example/ABC-42")
            .build();

    Optional<String> missing = prompts.missingInput("ABC-42", withTicket);

    assertThat(missing).contains("ticket");
    verify(tickets, never()).read(any(), any(), anyLong());
  }

  @Test
  void noTicketIsReadForTaskWithoutOne() {
    TaskState task = TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build();

    Optional<String> missing = prompts.missingInput("ABC-42", task);

    assertThat(missing).isEmpty();
    verify(tickets, never()).read(any(), any(), anyLong());
  }

  @Test
  void theDeployIsNamedMissingWhereTheTaskHasNoWorktree() {
    TaskState task = TaskState.builder("proj", null, TaskStatus.DEPLOYED).build();

    Optional<String> missing = prompts.missingInput("ABC-42", task);

    assertThat(missing).contains("deploy");
  }

  @Test
  void runMayFetchEachEnvironmentUrlAndItsLandedProbeAndNothingElse() {
    CheckerReader.Run run =
        prompts.accept("ABC-42", "/wt", "abc123", devAndStage, AcceptanceVerdict.ACCEPT);

    assertThat(run.reach().urls())
        .containsExactly(
            "https://dev.example.test",
            "https://dev.example.test/version",
            "https://stage.example.test");
  }

  @Test
  void theRunIsToldEachEnvironmentAndWhatProvesItsChangeUp() {
    CheckerReader.Run run =
        prompts.accept("ABC-42", "/wt", "abc123", devAndStage, AcceptanceVerdict.ACCEPT);

    assertThat(run.prompt())
        .contains(
            "- https://dev.example.test (the change is up when"
                + " https://dev.example.test/version answers)\n- https://stage.example.test");
    assertThat(run.prompt()).doesNotContain("https://stage.example.test (");
  }

  @Test
  void runMayReadTheFlowsRepositoryItIsToldToName() {

    CheckerReader.Run run =
        prompts.accept(
            "ABC-42",
            "/wt",
            "abc123",
            RoleOptions.defaults()
                .withFlows(
                    RoleOptions.Flows.defaults().withDriver("playwright").withPath("/flows/repo")),
            AcceptanceVerdict.ACCEPT);

    assertThat(run.reach().paths()).containsExactly(Path.of("/flows/repo"));
  }

  @Test
  void theRunIsToldTheDeployWasPushedAndNeverThatItLanded() {
    CheckerReader.Run accepted =
        prompts.accept("ABC-42", "/wt", "abc123", RoleOptions.defaults(), AcceptanceVerdict.ACCEPT);
    CheckerReader.Run declined =
        prompts.decline(
            "ABC-42", "/wt", "abc123", RoleOptions.defaults(), "- C1 — not met: x", "why");

    assertThat(accepted.prompt())
        .contains("ABC-42's deploy was pushed at abc123")
        .doesNotContain("is deployed at");
    assertThat(declined.prompt())
        .contains("ABC-42's deploy was pushed at abc123")
        .doesNotContain("is deployed at");
  }

  @Test
  void theRunIsToldWhereTheFlowsLiveWhenDriverIsSet() {
    RoleOptions acceptance =
        RoleOptions.defaults()
            .withFlows(RoleOptions.Flows.defaults().withDriver("playwright").withPath("/flows"));

    CheckerReader.Run run =
        prompts.accept("ABC-42", "/wt", "abc123", acceptance, AcceptanceVerdict.ACCEPT);

    assertThat(run.prompt())
        .contains(
            "Flows: /flows. In `flows` give each flow's `id`",
            "its `criterion`, written exactly as in the `criterion` of the row it checks");
  }

  @Test
  void runWithNoWorkspaceIsToldJagtRunsTheFlowsItNames() {
    RoleOptions acceptance =
        RoleOptions.defaults()
            .withFlows(RoleOptions.Flows.defaults().withDriver("playwright").withPath("/flows"));

    CheckerReader.Run run =
        prompts.accept("ABC-42", "/wt", "abc123", acceptance, AcceptanceVerdict.ACCEPT);

    assertThat(run.prompt())
        .contains("jagt runs each flow you name on that URL after your answer")
        .contains("you run none");
  }

  @Test
  void theRunIsToldOfNoFlowsWhileTheDriverIsNone() {
    RoleOptions acceptance =
        RoleOptions.defaults().withFlows(RoleOptions.Flows.defaults().withPath("/flows"));

    CheckerReader.Run run =
        prompts.accept("ABC-42", "/wt", "abc123", acceptance, AcceptanceVerdict.ACCEPT);

    assertThat(run.prompt()).doesNotContain("Flows:");
  }

  @Test
  void workspaceIsCreatedAndHoldsTheFlowsCommandOncePerEnvironment() {
    Path workspace = root.resolve("ws");
    RoleOptions acceptance =
        RoleOptions.defaults()
            .withEnvironments(
                List.of(
                    new RoleOptions.Environment("https://dev.example.test", ""),
                    new RoleOptions.Environment("https://stage.example.test", "")))
            .withFlows(
                RoleOptions.Flows.defaults()
                    .withDriver("playwright")
                    .withCommand(List.of("npx", "test")))
            .withWorkspace(workspace.toString());

    CheckerReader.Run run =
        prompts.accept("ABC-42", "/wt", "abc123", acceptance, AcceptanceVerdict.ACCEPT);

    assertThat(workspace).isDirectory();
    assertThat(run.reach().workspace().orElseThrow().commands())
        .containsExactly(
            "BASE_URL=https://dev.example.test npx test",
            "BASE_URL=https://stage.example.test npx test");
    assertThat(run.reach().paths()).contains(workspace);
  }

  @Test
  void workspaceIsNamedWithTheFlowsCommandAsTheReadMustRunIt() {
    RoleOptions acceptance =
        RoleOptions.defaults()
            .withEnvironments(List.of(new RoleOptions.Environment("https://dev.example.test", "")))
            .withFlows(RoleOptions.Flows.defaults().withCommand(List.of("npx", "test")))
            .withWorkspace(root.resolve("ws").toString());

    CheckerReader.Run run =
        prompts.accept("ABC-42", "/wt", "abc123", acceptance, AcceptanceVerdict.ACCEPT);

    assertThat(run.prompt())
        .contains(
            "Workspace: " + root.resolve("ws") + ", your working directory.",
            "`BASE_URL=<environment> npx test <file>`",
            "In `flows` name each flow you wrote or ran with its `id` and the `criterion`");
  }

  @Test
  void theGitReadIsPointedAtTheWorktreeNotTheWorkspace() {
    RoleOptions acceptance = RoleOptions.defaults().withWorkspace(root.resolve("ws").toString());

    CheckerReader.Run run =
        prompts.accept("ABC-42", "/wt", "abc123", acceptance, AcceptanceVerdict.ACCEPT);

    assertThat(run.prompt())
        .contains(
            "read git there with `git -C /wt show --no-ext-diff --no-textconv abc123`, never in the"
                + " workspace");
  }

  @Test
  void noWorkspaceLineWhereNoWorkspaceIsHeld() {

    CheckerReader.Run run =
        prompts.accept("ABC-42", "/wt", "abc123", RoleOptions.defaults(), AcceptanceVerdict.ACCEPT);

    assertThat(run.prompt()).doesNotContain("Workspace:");
  }

  @Test
  void runOpensTheIndexTheRoleFolderMapsWithTheShippedBrief() throws Exception {
    Path shipped = root.resolve("roles/acceptance/brief.md.dist");
    Files.createDirectories(shipped.getParent());
    Files.copy(Path.of("../../roles/acceptance/brief.md.dist"), shipped);
    Files.writeString(
        root.resolve("roles/acceptance/role.yml"),
        "workspace:\n  path: ws\n  index: wiki/index.md\n");
    RoleOptions acceptance = RoleOptions.read(root.resolve("roles/acceptance"));

    CheckerReader.Run run =
        prompts.accept("ABC-42", "/wt", "abc123", acceptance, AcceptanceVerdict.ACCEPT);

    assertThat(run.shared())
        .contains("Read its `wiki/index.md` first")
        .doesNotContain("{workspace.");
  }
}
