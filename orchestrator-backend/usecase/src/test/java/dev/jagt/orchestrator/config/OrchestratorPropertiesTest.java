package dev.jagt.orchestrator.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrchestratorPropertiesTest {

    @Test
    void keepsTheCommandExactlyAsItWasConfigured() {
        List<String> command = OrchestratorProperties.defaults()
                .withEditorDiffCommand(List.of("sh", "diff")).editorDiffCommand();

        assertThat(command).containsExactly("sh", "diff");
    }
}
