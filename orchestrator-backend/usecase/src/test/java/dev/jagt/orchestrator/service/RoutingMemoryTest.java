package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

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
    void keepsTheRuleItRetiredWithTheDateItStoppedBeingTrue() throws IOException {
        when(paths.root()).thenReturn(root);
        Clock frozen = Clock.fixed(Instant.parse("2026-09-29T00:00:00Z"), ZoneOffset.UTC);
        RoutingMemory memory = new RoutingMemory(paths, 60, frozen);

        memory.remember("PAN items about quote import", "sc");
        memory.remember("PAN items about quote import", "api");

        assertThat(Files.readString(root.resolve("memory/routing.md")))
                .contains("# until 2026-09-29: PAN items about quote import -> sc");
    }

    @Test
    void dropsTheRuleNothingHasEverPlacedBeforeOneThatWorks() {
        when(paths.root()).thenReturn(root);
        RoutingMemory memory = new RoutingMemory(paths, 2);
        memory.remember("oldest but used", "api");
        memory.remember("oldest but used", "api");
        memory.remember("never used", "web");

        memory.remember("newest", "sc");

        assertThat(memory.rules()).containsExactly("oldest but used -> api", "newest -> sc");
    }

    @Test
    void dropsTheOldestOfTheRulesNothingHasPlaced() {
        when(paths.root()).thenReturn(root);
        RoutingMemory memory = new RoutingMemory(paths, 2);
        memory.remember("oldest", "api");
        memory.remember("middle", "web");

        memory.remember("newest", "sc");

        assertThat(memory.rules()).containsExactly("middle -> web", "newest -> sc");
    }

    @Test
    void countsTheSameRuleSaidAgainAsThatRulePlacingAnotherItem() throws IOException {
        when(paths.root()).thenReturn(root);
        RoutingMemory memory = new RoutingMemory(paths, 60);

        memory.remember("PAN items about quote import", "sc");
        memory.remember("PAN items about quote import", "sc");

        assertThat(Files.readString(root.resolve("memory/routing.md")))
                .contains("PAN items about quote import -> sc #1");
    }

    @Test
    void readsARuleAHumanWroteByHandWithNoCountOnIt() throws IOException {
        when(paths.root()).thenReturn(root);
        Files.createDirectories(root.resolve("memory"));
        Files.writeString(root.resolve("memory/routing.md"), "PAN items about quote import -> sc\n");

        assertThat(new RoutingMemory(paths, 60).rules())
                .containsExactly("PAN items about quote import -> sc");
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
