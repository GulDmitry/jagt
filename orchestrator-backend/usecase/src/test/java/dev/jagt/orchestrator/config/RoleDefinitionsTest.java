package dev.jagt.orchestrator.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleDefinitionsTest {

    @Test
    void theQaCompiledFromItsRoleYmlIsTheQaDeclaredInCode() {
        RoleDefinition compiled = RoleDefinitions.fromFile(Path.of("../../roles/qa/role.yml.dist"));

        assertThat(compiled.id()).isEqualTo(RoleDefinition.QA.id());
        assertThat(compiled.name()).isEqualTo(RoleDefinition.QA.name());
        assertThat(compiled.model()).isEqualTo(RoleDefinition.QA.model());
        assertThat(compiled.trigger()).isEqualTo(RoleDefinition.QA.trigger());
        assertThat(compiled.oncePer()).isEqualTo(RoleDefinition.QA.oncePer());
        assertThat(compiled.outcomes()).isEqualTo(RoleDefinition.QA.outcomes());
        assertThat(compiled.tools()).isEqualTo(RoleDefinition.QA.tools());
        assertThat(compiled.answer().json()).isEqualTo(RoleDefinition.QA.answer().json());
        assertThat(compiled.decline().json()).isEqualTo(RoleDefinition.QA.decline().json());
    }

    @Test
    void refusesATriggerWordTheVocabularyDoesNotHold() {
        assertThatThrownBy(() -> RoleDefinitions.compile("id: x\nname: X\nmodel: m\ntrigger: [whenever]\n"
                + "oncePer: deploy\noutcomes: [round]\n")).isInstanceOf(RoleInvalid.class).hasMessageContaining("whenever");
    }

    @Test
    void refusesARoleWhoseReadHasNoOutcome() {
        assertThatThrownBy(() -> RoleDefinitions.compile("id: x\nname: X\nmodel: m\ntrigger: [deployed]\n"
                + "oncePer: deploy\noutcomes: []\n")).isInstanceOf(RoleInvalid.class).hasMessageContaining("no exit");
    }
}
