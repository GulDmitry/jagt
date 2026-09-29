package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoutingMemoryTest {

    @TempDir
    Path root;

    private final OrchestratorPaths paths = mock(OrchestratorPaths.class);

    @Test
    void holdsNoRuleWhereNobodyHasWrittenOne() {
        when(paths.root()).thenReturn(root);

        assertThat(new RoutingMemory(paths, 60).rules()).isEmpty();
    }

    @Test
    void letsTheNewestRuleForAKeyReplaceTheOneItChanged() {
        when(paths.root()).thenReturn(root);
        RoutingMemory memory = new RoutingMemory(paths, 60);

        memory.remember("PAN items about quote import", "sc");
        memory.remember("PAN items about quote import", "api");

        assertThat(memory.rules()).containsExactly("PAN items about quote import -> api");
    }

    @Test
    void writesNothingWhenTheRuleAlreadySaysThat() {
        when(paths.root()).thenReturn(root);
        RoutingMemory memory = new RoutingMemory(paths, 60);
        memory.remember("PAN items about quote import", "sc");

        assertThat(memory.remember("PAN items about quote import", "sc")).isFalse();
    }

    @Test
    void dropsTheOldestRuleOncePastItsCeiling() {
        when(paths.root()).thenReturn(root);
        RoutingMemory memory = new RoutingMemory(paths, 2);

        memory.remember("oldest", "api");
        memory.remember("middle", "web");
        memory.remember("newest", "sc");

        assertThat(memory.rules()).containsExactly("middle -> web", "newest -> sc");
    }

    @Test
    void readsPastTheNotesAHumanLeftBetweenTheRules() throws IOException {
        when(paths.root()).thenReturn(root);
        Files.createDirectories(root.resolve("memory"));
        Files.writeString(root.resolve("memory/routing.md"),
                "# what places what\n\nPAN items about quote import -> sc\n");

        assertThat(new RoutingMemory(paths, 60).rules())
                .containsExactly("PAN items about quote import -> sc");
    }
}
