package dev.jagt.orchestrator.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleDefinitionsTest {

    @Test
    void theQaCompiledFromItsRoleYmlIsTheQaDeclaredInCode() {
        RoleDefinition compiled = RoleDefinitions.fromFile(Path.of("../../roles/qa/role.yml.dist"));

        assertThat(compiled.id()).isEqualTo("qa");
        assertThat(compiled.trigger()).containsExactly("deployed", "pipelines-green", "environment-named");
        assertThat(compiled.oncePer()).isEqualTo("deploy");
        assertThat(compiled.outcomes()).containsExactly("round", "question", "wait", "dispute", "stop");
        assertThat(compiled.tools().cli()).containsExactly("Read", "Grep", "Glob", "Bash", "WebFetch");
        assertThat(compiled.tools().refuse()).contains("Write", "Bash(git push:*)");
        assertThat(compiled.tools().git()).isTrue();
        assertThat(compiled.tools().fetch()).isTrue();
    }

    @Test
    void refusesATriggerWordTheVocabularyDoesNotHold() {
        assertThatThrownBy(() -> RoleDefinitions.compile("id: x\nname: X\nmodel: m\ntrigger: [whenever]\n"
                + "oncePer: deploy\noutcomes: [round]\n")).isInstanceOf(RoleInvalid.class).hasMessageContaining("whenever");
    }

    @Test
    void aCheckerTriggerWithoutDeployedIsRefused() {
        assertThatThrownBy(() -> RoleDefinitions.compile("id: x\nname: X\nmodel: m\ntrigger: [pipelines-green]\n"
                + "oncePer: deploy\noutcomes: [round]\n")).isInstanceOf(RoleInvalid.class).hasMessageContaining("deployed");
    }

    @Test
    void refusesARoleWhoseReadHasNoOutcome() {
        assertThatThrownBy(() -> RoleDefinitions.compile("id: x\nname: X\nmodel: m\ntrigger: [deployed]\n"
                + "oncePer: deploy\noutcomes: []\n")).isInstanceOf(RoleInvalid.class).hasMessageContaining("no exit");
    }
}
