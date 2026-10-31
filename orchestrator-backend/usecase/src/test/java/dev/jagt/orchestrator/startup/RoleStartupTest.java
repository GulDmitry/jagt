package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.RoleDefinition;
import dev.jagt.orchestrator.flow.Roles;
import dev.jagt.orchestrator.service.InstalledRoles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(new RoleCheck(roles).problems()).isEmpty();
        assertThat(new RoleCheck(roles).notes()).singleElement().satisfies(note -> {
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
        assertThat(Roles.lint("qa")).isEmpty();
    }
}
