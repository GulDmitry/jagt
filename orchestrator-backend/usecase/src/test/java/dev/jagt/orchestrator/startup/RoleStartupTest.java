package dev.jagt.orchestrator.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.RoleDefinition;
import dev.jagt.orchestrator.flow.CheckerRole;
import dev.jagt.orchestrator.flow.Roles;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.service.ActStops;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.InstalledRoles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RoleStartupTest {

  @Test
  void aRoleYmlWithAStateWithoutAnExitIsRefusedAndJagtStarts(@TempDir Path root) throws Exception {
    Files.createDirectories(root.resolve("roles/acceptance"));
    Files.copy(
        Path.of("../../roles/acceptance/role.yml.dist"),
        root.resolve("roles/acceptance/role.yml.dist"));
    Files.createDirectories(root.resolve("roles/dead"));
    Files.writeString(
        root.resolve("roles/dead/role.yml"),
        "id: dead\nname: Dead\nmodel: sonnet\n"
            + "trigger: [deployed]\noncePer: deploy\noutcomes: []\n");
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));

    InstalledRoles roles = new InstalledRoles(paths);

    assertThat(roles.definitions()).extracting(RoleDefinition::id).containsExactly("acceptance");
    assertThat(check(roles, paths).problems()).isEmpty();
    assertThat(check(roles, paths).notes())
        .singleElement()
        .satisfies(
            note -> {
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
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));

    InstalledRoles roles = new InstalledRoles(paths);

    assertThat(roles.definitions()).extracting(RoleDefinition::id).containsExactly("acceptance");
    assertThat(roles.skipped()).isEmpty();
    assertThat(
            Roles.lint(new CheckerRole("acceptance", true, List.of("accept", "again", "decline"))))
        .isEmpty();
  }

  @Test
  void aCopyOfRoleYmlChangesOnlyTheKeysItWritesAndAListItWritesReplacesTheShippedOne(
      @TempDir Path root) throws Exception {
    Files.createDirectories(root.resolve("roles/acceptance"));
    Files.copy(
        Path.of("../../roles/acceptance/role.yml.dist"),
        root.resolve("roles/acceptance/role.yml.dist"));
    Files.writeString(
        root.resolve("roles/acceptance/role.yml"),
        "model: opus\ntrigger: [deployed]\ntools:\n  git: false\n");
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));

    RoleDefinition acceptance = new InstalledRoles(paths).find("acceptance").orElseThrow();

    assertThat(acceptance.model()).isEqualTo("opus");
    assertThat(acceptance.trigger().trigger()).containsExactly("deployed");
    assertThat(acceptance.tools().git()).isFalse();
    assertThat(acceptance.tools().shell()).contains("git log*");
    assertThat(acceptance.replies().outcomes()).contains("round");
  }

  @Test
  void aCopyOfRoleYmlThatBreaksTheRoleIsRefusedAndTheShippedFileStands(@TempDir Path root)
      throws Exception {
    Files.createDirectories(root.resolve("roles/acceptance"));
    Files.copy(
        Path.of("../../roles/acceptance/role.yml.dist"),
        root.resolve("roles/acceptance/role.yml.dist"));
    Files.writeString(root.resolve("roles/acceptance/role.yml"), "outcomes: []\n");
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));

    InstalledRoles roles = new InstalledRoles(paths);

    assertThat(roles.find("acceptance"))
        .hasValueSatisfying(
            acceptance -> assertThat(acceptance.replies().outcomes()).contains("round"));
    assertThat(roles.skipped())
        .singleElement()
        .satisfies(
            problem ->
                assertThat(problem.text())
                    .startsWith("role.yml: ")
                    .endsWith("the shipped file stands"));
  }

  @Test
  void aDisabledAcceptanceWithNoAcceptanceRoleInstalledNeverFailsTheStartAndTheNoteSaysWhy(
      @TempDir Path root) throws Exception {
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
    InstalledRoles roles = new InstalledRoles(paths);

    RoleCheck check = check(roles, paths);

    assertThat(check.problems()).isEmpty();
    assertThat(check.notes())
        .singleElement()
        .satisfies(
            note -> {
              assertThat(note.key()).isEqualTo("roles/acceptance");
              assertThat(note.text()).contains("no roles/acceptance/role.yml");
            });
  }

  @Test
  void anEnabledAcceptanceWithNoAcceptanceRoleInstalledRefusesTheStartNamingTheReason(
      @TempDir Path root) throws Exception {
    Files.writeString(
        root.resolve("jagt.yml"),
        "orchestrator:\n  roles:\n    acceptance:\n      enabled: true\n");
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withConfigFile(root.resolve("jagt.yml").toString()));
    InstalledRoles roles = new InstalledRoles(paths);

    assertThat(check(roles, paths).problems())
        .singleElement()
        .satisfies(
            problem -> {
              assertThat(problem.key()).isEqualTo("roles/acceptance");
              assertThat(problem.text()).contains("no roles/acceptance/role.yml");
            });
  }

  @Test
  void aRolesKeyNamingAFolderWithABriefAndNoRoleFileIsNoUnknownRole(@TempDir Path root)
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
                .withConfigFile(root.resolve("jagt.yml").toString()));

    RoleCheck check = check(new InstalledRoles(paths), paths);

    assertThat(check.notes()).isEmpty();
  }

  @Test
  void aRolesKeyNamingNoInstalledRoleIsANoteNamingTheKey(@TempDir Path root) throws Exception {
    Files.createDirectories(root.resolve("roles/acceptance"));
    Files.copy(
        Path.of("../../roles/acceptance/role.yml.dist"),
        root.resolve("roles/acceptance/role.yml.dist"));
    Files.writeString(
        root.resolve("jagt.yml"),
        "orchestrator:\n  roles:\n    ACCEPTANCE:\n      enabled: true\n");
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withConfigFile(root.resolve("jagt.yml").toString()));

    RoleCheck check = check(new InstalledRoles(paths), paths);

    assertThat(check.notes())
        .singleElement()
        .satisfies(
            note -> {
              assertThat(note.key()).isEqualTo("roles.ACCEPTANCE");
              assertThat(note.text())
                  .contains("orchestrator.roles.ACCEPTANCE names no installed role");
            });
    assertThat(check.problems()).isEmpty();
  }

  private static RoleCheck check(InstalledRoles roles, OrchestratorPaths paths) {
    return new RoleCheck(
        roles, new ConfigService(paths, mock(AgentRuntime.class), mock(ActStops.class)), paths);
  }
}
