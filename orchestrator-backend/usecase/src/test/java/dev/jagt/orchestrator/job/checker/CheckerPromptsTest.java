package dev.jagt.orchestrator.job.checker;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.role.RoleDefinitions;
import dev.jagt.orchestrator.config.role.RoleFiles;
import dev.jagt.orchestrator.config.role.RoleOptions;
import dev.jagt.orchestrator.port.assistant.CheckerReader;
import dev.jagt.orchestrator.task.acceptance.AcceptanceVerdict;
import dev.jagt.orchestrator.testing.FakeHost;
import java.nio.file.Path;
import java.util.List;
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
                "acceptance"));
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
}
