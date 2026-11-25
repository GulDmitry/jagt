package dev.jagt.orchestrator.startup.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.role.RoleDefinition;
import dev.jagt.orchestrator.config.source.ConfigSource;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.role.CheckerRole;
import dev.jagt.orchestrator.flow.role.Roles;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.KeptNumbers;
import dev.jagt.orchestrator.service.master.ActStops;
import dev.jagt.orchestrator.service.reach.ReachLedger;
import dev.jagt.orchestrator.service.role.InstalledRoles;
import dev.jagt.orchestrator.testing.FakeConfigInputs;
import dev.jagt.orchestrator.testing.FakeHost;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RoleCheckStartupTest {

  private final AgentRuntime agents = mock(AgentRuntime.class);
  private final ActStops actStops = mock(ActStops.class);
  private final Checkers checkers = new Checkers(new TaskChart(Composition.builtIn()));

  @Test
  void roleYmlWithStateWithoutAnExitIsRefusedAndJagtStarts(@TempDir Path root) throws Exception {
    Files.createDirectories(root.resolve("roles/acceptance"));
    Files.copy(
        Path.of("../../roles/acceptance/role.yml.dist"),
        root.resolve("roles/acceptance/role.yml.dist"));
    Files.createDirectories(root.resolve("roles/dead"));
    Files.writeString(
        root.resolve("roles/dead/role.yml"),
        """
        id: dead
        name: Dead
        model: sonnet
        trigger: [deployed]
        oncePer: deploy
        outcomes: []
        """);
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults().withRoot(root.toString()), FakeHost.machine());

    InstalledRoles roles = new InstalledRoles(paths);

    assertThat(roles.definitions()).extracting(RoleDefinition::id).containsExactly("acceptance");
    RoleCheck check =
        new RoleCheck(
            roles,
            new ConfigService(
                new ConfigSource(paths, new FakeConfigInputs()),
                agents,
                actStops,
                checkers,
                new KeptNumbers(new ReachLedger())));
    assertThat(check.problems()).isEmpty();
    assertThat(check.notes())
        .singleElement()
        .satisfies(note -> {
          assertThat(note.key()).isEqualTo("roles/dead");
          assertThat(note.text()).contains("no exit");
        });
  }

  @Test
  void acceptanceShippedAsDistComposesTheAcceptanceRegion(@TempDir Path root) throws Exception {
    Files.createDirectories(root.resolve("roles/acceptance"));
    Files.copy(
        Path.of("../../roles/acceptance/role.yml.dist"),
        root.resolve("roles/acceptance/role.yml.dist"));
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults().withRoot(root.toString()), FakeHost.machine());

    InstalledRoles roles = new InstalledRoles(paths);

    assertThat(roles.definitions()).extracting(RoleDefinition::id).containsExactly("acceptance");
    assertThat(roles.skipped()).isEmpty();
    assertThat(
            Roles.lint(
                new CheckerRole(
                    "acceptance", true, List.of("accept", "again", "decline"), List.of())))
        .isEmpty();
  }

  @Test
  void copyOfRoleYmlChangesOnlyTheKeysItWritesAndListItWritesReplacesTheShippedOne(
      @TempDir Path root) throws Exception {
    Files.createDirectories(root.resolve("roles/acceptance"));
    Files.copy(
        Path.of("../../roles/acceptance/role.yml.dist"),
        root.resolve("roles/acceptance/role.yml.dist"));
    Files.writeString(
        root.resolve("roles/acceptance/role.yml"),
        "model: opus\ntrigger: [deployed]\ntools:\n  git: false\n");
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults().withRoot(root.toString()), FakeHost.machine());

    RoleDefinition acceptance = new InstalledRoles(paths).find("acceptance").orElseThrow();

    assertThat(acceptance.model()).isEqualTo("opus");
    assertThat(acceptance.trigger().trigger()).containsExactly("deployed");
    assertThat(acceptance.tools().git()).isFalse();
    assertThat(acceptance.tools().shell()).contains("git log*");
    assertThat(acceptance.replies().outcomes()).contains("round");
  }

  @Test
  void copyOfRoleYmlThatBreaksTheRoleIsRefusedAndTheShippedFileStands(@TempDir Path root)
      throws Exception {
    Files.createDirectories(root.resolve("roles/acceptance"));
    Files.copy(
        Path.of("../../roles/acceptance/role.yml.dist"),
        root.resolve("roles/acceptance/role.yml.dist"));
    Files.writeString(root.resolve("roles/acceptance/role.yml"), "outcomes: []\n");
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults().withRoot(root.toString()), FakeHost.machine());

    InstalledRoles roles = new InstalledRoles(paths);

    assertThat(roles.find("acceptance"))
        .hasValueSatisfying(
            acceptance -> assertThat(acceptance.replies().outcomes()).contains("round"));
    assertThat(roles.skipped())
        .singleElement()
        .satisfies(problem ->
            assertThat(problem.text())
                .startsWith("role.yml: ")
                .endsWith("the shipped file stands"));
  }

  @Test
  void rolesKeyNamingFolderWithBriefAndNoRoleFileIsNoUnknownRole(@TempDir Path root)
      throws Exception {
    Files.createDirectories(root.resolve("roles/acceptance"));
    Files.copy(
        Path.of("../../roles/acceptance/role.yml.dist"),
        root.resolve("roles/acceptance/role.yml.dist"));
    Files.createDirectories(root.resolve("roles/triage"));
    Files.writeString(root.resolve("roles/triage/brief.md.dist"), "a brief\n");
    Files.writeString(
        root.resolve("jagt.yml"), "orchestrator:\n  roles:\n    triage:\n      enabled: true\n");
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withConfigFile(root.resolve("jagt.yml").toString()),
            FakeHost.machine());

    RoleCheck check =
        new RoleCheck(
            new InstalledRoles(paths),
            new ConfigService(
                new ConfigSource(paths, new FakeConfigInputs()),
                agents,
                actStops,
                checkers,
                new KeptNumbers(new ReachLedger())));

    assertThat(check.notes()).isEmpty();
  }

  @Test
  void rolesKeyNamingNoInstalledRoleIsNoteNamingTheKey(@TempDir Path root) throws Exception {
    Files.writeString(
        root.resolve("jagt.yml"),
        "orchestrator:\n  roles:\n    ACCEPTANCE:\n      enabled: true\n");
    Files.createDirectories(root.resolve("roles/acceptance"));
    Files.copy(
        Path.of("../../roles/acceptance/role.yml.dist"),
        root.resolve("roles/acceptance/role.yml.dist"));
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withConfigFile(root.resolve("jagt.yml").toString()),
            FakeHost.machine());

    ConfigService config =
        new ConfigService(
            new ConfigSource(paths, new FakeConfigInputs()),
            agents,
            actStops,
            checkers,
            new KeptNumbers(new ReachLedger()));

    RoleCheck check = new RoleCheck(new InstalledRoles(paths), config);

    assertThat(check.notes())
        .singleElement()
        .satisfies(note -> {
          assertThat(note.key()).isEqualTo("roles.ACCEPTANCE");
          assertThat(note.text()).contains("orchestrator.roles.ACCEPTANCE names no installed role");
        });
    assertThat(check.problems()).isEmpty();
  }
}
