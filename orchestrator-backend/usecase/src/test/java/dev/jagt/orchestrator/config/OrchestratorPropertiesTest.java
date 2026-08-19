package dev.jagt.orchestrator.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrchestratorPropertiesTest {

    /**
     * Where a binary is FOUND is the edge's business, so the record must not quietly rewrite what was configured:
     * a value that came back resolved here would make the configuration depend on the machine.
     */
    @Test
    void keepsTheCommandExactlyAsItWasConfigured() {
        List<String> command = OrchestratorProperties.defaults()
                .withEditorDiffCommand(List.of("sh", "diff")).editorDiffCommand();

        assertThat(command).containsExactly("sh", "diff");
    }
}
