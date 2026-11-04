package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.RoleDefinition;
import dev.jagt.orchestrator.flow.CheckerRole;
import dev.jagt.orchestrator.flow.Roles;
import dev.jagt.orchestrator.service.InstalledRoles;
import dev.jagt.orchestrator.service.ActStops;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.port.AgentRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RoleStartupTest {

    @Test
    void aRoleYmlWithAStateWithoutAnExitIsRefusedAndJagtStarts(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("roles/qa"));
        Files.copy(Path.of("../../roles/qa/role.yml.dist"), root.resolve("roles/qa/role.yml.dist"));
        Files.createDirectories(root.resolve("roles/dead"));
        Files.writeString(root.resolve("roles/dead/role.yml"), "id: dead\nname: Dead\nmodel: sonnet\n"
                + "trigger: [deployed]\noncePer: deploy\noutcomes: []\n");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));

        InstalledRoles roles = new InstalledRoles(paths);

        assertThat(roles.definitions()).extracting(RoleDefinition::id).containsExactly("qa");
        assertThat(check(roles, paths).problems()).isEmpty();
        assertThat(check(roles, paths).notes()).singleElement().satisfies(note -> {
            assertThat(note.key()).isEqualTo("roles/dead");
            assertThat(note.text()).contains("no exit");
        });
    }

    @Test
    void qaShippedAsDistComposesTheQaRegion(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("roles/qa"));
        Files.copy(Path.of("../../roles/qa/role.yml.dist"), root.resolve("roles/qa/role.yml.dist"));
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));

        InstalledRoles roles = new InstalledRoles(paths);

        assertThat(roles.definitions()).extracting(RoleDefinition::id).containsExactly("qa");
        assertThat(roles.skipped()).isEmpty();
        assertThat(Roles.lint(new CheckerRole("qa", true, List.of("accept", "again", "decline")))).isEmpty();
    }

    @Test
    void aCopyOfRoleYmlChangesOnlyTheKeysItWritesAndAListItWritesReplacesTheShippedOne(@TempDir Path root)
            throws Exception {
        Files.createDirectories(root.resolve("roles/qa"));
        Files.copy(Path.of("../../roles/qa/role.yml.dist"), root.resolve("roles/qa/role.yml.dist"));
        Files.writeString(root.resolve("roles/qa/role.yml"), "model: opus\ntrigger: [deployed]\ntools:\n  git: false\n");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));

        RoleDefinition qa = new InstalledRoles(paths).find("qa").orElseThrow();

        assertThat(qa.model()).isEqualTo("opus");
        assertThat(qa.trigger()).containsExactly("deployed");
        assertThat(qa.tools().git()).isFalse();
        assertThat(qa.tools().cli()).contains("Read");
        assertThat(qa.outcomes()).contains("round");
    }

    @Test
    void aCopyOfRoleYmlThatBreaksTheRoleIsRefusedAndTheShippedFileStands(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("roles/qa"));
        Files.copy(Path.of("../../roles/qa/role.yml.dist"), root.resolve("roles/qa/role.yml.dist"));
        Files.writeString(root.resolve("roles/qa/role.yml"), "outcomes: []\n");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));

        InstalledRoles roles = new InstalledRoles(paths);

        assertThat(roles.find("qa")).hasValueSatisfying(qa -> assertThat(qa.outcomes()).contains("round"));
        assertThat(roles.skipped()).singleElement().satisfies(problem ->
                assertThat(problem.text()).startsWith("role.yml: ").endsWith("the shipped file stands"));
    }

    @Test
    void aDisabledQaWithNoQaRoleInstalledNeverFailsTheStartAndTheNoteSaysWhy(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
        InstalledRoles roles = new InstalledRoles(paths);

        RoleCheck check = check(roles, paths);

        assertThat(check.problems()).isEmpty();
        assertThat(check.notes()).singleElement().satisfies(note -> {
            assertThat(note.key()).isEqualTo("roles/qa");
            assertThat(note.text()).contains("no roles/qa/role.yml");
        });
    }

    @Test
    void anEnabledQaWithNoQaRoleInstalledRefusesTheStartNamingTheReason(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  roles:\n    qa:\n      enabled: true\n");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(root.resolve("jagt.yml").toString()));
        InstalledRoles roles = new InstalledRoles(paths);

        assertThat(check(roles, paths).problems()).singleElement().satisfies(problem -> {
            assertThat(problem.key()).isEqualTo("roles/qa");
            assertThat(problem.text()).contains("no roles/qa/role.yml");
        });
    }

    @Test
    void aRolesKeyNamingNoInstalledRoleIsANoteNamingTheKey(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("roles/qa"));
        Files.copy(Path.of("../../roles/qa/role.yml.dist"), root.resolve("roles/qa/role.yml.dist"));
        Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  roles:\n    QA:\n      enabled: true\n");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(root.resolve("jagt.yml").toString()));

        RoleCheck check = check(new InstalledRoles(paths), paths);

        assertThat(check.notes()).singleElement().satisfies(note -> {
            assertThat(note.key()).isEqualTo("roles.QA");
            assertThat(note.text()).contains("orchestrator.roles.QA names no installed role");
        });
        assertThat(check.problems()).isEmpty();
    }

    private static RoleCheck check(InstalledRoles roles, OrchestratorPaths paths) {
        return new RoleCheck(roles, new ConfigService(paths, mock(AgentRuntime.class), mock(ActStops.class)), paths);
    }
}
