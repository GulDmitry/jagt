package dev.jagt.orchestrator.job;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.RoleDefinitions;
import dev.jagt.orchestrator.config.RoleFiles;
import dev.jagt.orchestrator.config.RoleOptions;
import dev.jagt.orchestrator.port.CheckerReader;
import dev.jagt.orchestrator.task.AcceptanceVerdict;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AcceptancePromptsTest {

  @Test
  void aRunMayFetchEachEnvironmentUrlAndItsLandedProbeAndNothingElse(@TempDir Path root) {
    RoleOptions acceptance =
        RoleOptions.defaults()
            .withEnvironments(
                List.of(
                    new RoleOptions.Environment(
                        "https://dev.example.test", "https://dev.example.test/version"),
                    new RoleOptions.Environment("https://stage.example.test", "")));
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));

    CheckerReader.Run run =
        new CheckerPrompts(
                RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
                new RoleFiles(paths, "acceptance"))
            .accept("ABC-42", "/wt", "abc123", acceptance, AcceptanceVerdict.ACCEPT);

    assertThat(run.reach().urls())
        .containsExactly(
            "https://dev.example.test",
            "https://dev.example.test/version",
            "https://stage.example.test");
  }

  @Test
  void theRunIsToldEachEnvironmentAndWhatProvesItsChangeUp(@TempDir Path root) {
    RoleOptions acceptance =
        RoleOptions.defaults()
            .withEnvironments(
                List.of(
                    new RoleOptions.Environment(
                        "https://dev.example.test", "https://dev.example.test/version"),
                    new RoleOptions.Environment("https://stage.example.test", "")));
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));

    CheckerReader.Run run =
        new CheckerPrompts(
                RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
                new RoleFiles(paths, "acceptance"))
            .accept("ABC-42", "/wt", "abc123", acceptance, AcceptanceVerdict.ACCEPT);

    assertThat(run.prompt())
        .contains(
            "- https://dev.example.test (the change is up when"
                + " https://dev.example.test/version answers)\n- https://stage.example.test");
    assertThat(run.prompt()).doesNotContain("https://stage.example.test (");
  }

  @Test
  void aRunMayReadTheFlowsRepositoryItIsToldToName(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));

    CheckerReader.Run run =
        new CheckerPrompts(
                RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
                new RoleFiles(paths, "acceptance"))
            .accept(
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
  void theRunIsToldTheDeployWasPushedAndNeverThatItLanded(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
    CheckerPrompts prompts =
        new CheckerPrompts(
            RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
            new RoleFiles(paths, "acceptance"));

    CheckerReader.Run accepted =
        prompts.accept(
            "ABC-42",
            "/wt",
            "abc123",
            RoleOptions.defaults(),
            AcceptanceVerdict.ACCEPT);
    CheckerReader.Run declined =
        prompts.decline(
            "ABC-42",
            "/wt",
            "abc123",
            RoleOptions.defaults(),
            "- C1 — not met: x",
            "why");

    assertThat(accepted.prompt())
        .contains("ABC-42's deploy was pushed at abc123")
        .doesNotContain("is deployed at");
    assertThat(declined.prompt())
        .contains("ABC-42's deploy was pushed at abc123")
        .doesNotContain("is deployed at");
  }

  @Test
  void theRunIsToldWhereTheFlowsLiveWhenADriverIsSet(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
    RoleOptions acceptance =
        RoleOptions.defaults()
            .withFlows(RoleOptions.Flows.defaults().withDriver("playwright").withPath("/flows"));

    CheckerReader.Run run =
        new CheckerPrompts(
                RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
                new RoleFiles(paths, "acceptance"))
            .accept("ABC-42", "/wt", "abc123", acceptance, AcceptanceVerdict.ACCEPT);

    assertThat(run.prompt()).contains("Flows: /flows. In `flows` name the ids");
  }

  @Test
  void theRunIsToldOfNoFlowsWhileTheDriverIsNone(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
    RoleOptions acceptance =
        RoleOptions.defaults().withFlows(RoleOptions.Flows.defaults().withPath("/flows"));

    CheckerReader.Run run =
        new CheckerPrompts(
                RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
                new RoleFiles(paths, "acceptance"))
            .accept("ABC-42", "/wt", "abc123", acceptance, AcceptanceVerdict.ACCEPT);

    assertThat(run.prompt()).doesNotContain("Flows:");
  }

  @Test
  void aWorkspaceIsCreatedAndHoldsTheFlowsCommandOncePerEnvironment(@TempDir Path root) {
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
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));

    CheckerReader.Run run =
        new CheckerPrompts(
                RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
                new RoleFiles(paths, "acceptance"))
            .accept("ABC-42", "/wt", "abc123", acceptance, AcceptanceVerdict.ACCEPT);

    assertThat(workspace).isDirectory();
    assertThat(run.reach().workspace().orElseThrow().commands())
        .containsExactly(
            "BASE_URL=https://dev.example.test npx test",
            "BASE_URL=https://stage.example.test npx test");
    assertThat(run.reach().paths()).contains(workspace);
  }
}
