package dev.jagt.orchestrator.job;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.RoleDefinitions;
import dev.jagt.orchestrator.config.RoleFiles;
import dev.jagt.orchestrator.port.CheckerReader;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.FlowsConfig;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.QaEnvironment;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.RoleConfig;
import dev.jagt.orchestrator.task.QaVerdict;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QaPromptsTest {

  @Test
  void aRunMayFetchEachEnvironmentUrlAndItsLandedProbeAndNothingElse(@TempDir Path root) {
    RoleConfig qa =
        new RoleConfig(
            true,
            null,
            List.of(
                new QaEnvironment("https://dev.example.test", "https://dev.example.test/version"),
                new QaEnvironment("https://stage.example.test", null)),
            null);
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));

    CheckerReader.Run run =
        new CheckerPrompts(
                RoleDefinitions.fromFile(Path.of("../../roles/qa/role.yml.dist")),
                new RoleFiles(paths, "qa"))
            .accept("ABC-42", "/wt", "abc123", qa, QaVerdict.ACCEPT);

    assertThat(run.environments())
        .containsExactly(
            "https://dev.example.test",
            "https://dev.example.test/version",
            "https://stage.example.test");
  }

  @Test
  void theRunIsToldTheDeployWasPushedAndNeverThatItLanded(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
    CheckerPrompts prompts =
        new CheckerPrompts(
            RoleDefinitions.fromFile(Path.of("../../roles/qa/role.yml.dist")),
            new RoleFiles(paths, "qa"));

    CheckerReader.Run accepted =
        prompts.accept(
            "ABC-42", "/wt", "abc123", new RoleConfig(true, null, List.of(), null), QaVerdict.ACCEPT);
    CheckerReader.Run declined =
        prompts.decline(
            "ABC-42",
            "/wt",
            "abc123",
            new RoleConfig(true, null, List.of(), null),
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
    RoleConfig qa =
        new RoleConfig(true, null, List.of(), new FlowsConfig("playwright", "/flows"));

    CheckerReader.Run run =
        new CheckerPrompts(
                RoleDefinitions.fromFile(Path.of("../../roles/qa/role.yml.dist")),
                new RoleFiles(paths, "qa"))
            .accept("ABC-42", "/wt", "abc123", qa, QaVerdict.ACCEPT);

    assertThat(run.prompt()).contains("Flows: /flows. In `flows` name the ids");
  }

  @Test
  void theRunIsToldOfNoFlowsWhileTheDriverIsNone(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
    RoleConfig qa = new RoleConfig(true, null, List.of(), new FlowsConfig("none", "/flows"));

    CheckerReader.Run run =
        new CheckerPrompts(
                RoleDefinitions.fromFile(Path.of("../../roles/qa/role.yml.dist")),
                new RoleFiles(paths, "qa"))
            .accept("ABC-42", "/wt", "abc123", qa, QaVerdict.ACCEPT);

    assertThat(run.prompt()).doesNotContain("Flows:");
  }
}
