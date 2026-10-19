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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

        assertThat(new RoutingMemory(paths, Clock.systemUTC()).rules()).isEmpty();
    }

    @Test
    void letsTheNewestRuleForAKeyReplaceTheOneItChanged() {
        when(paths.root()).thenReturn(root);
        RoutingMemory memory = new RoutingMemory(paths, Clock.systemUTC());

        memory.remember("ABC items about quote import", "sc");
        memory.remember("ABC items about quote import", "api");

        assertThat(memory.rules()).containsExactly("ABC items about quote import -> api");
    }

    @Test
    void writesNothingWhenTheRuleAlreadySaysThat() {
        when(paths.root()).thenReturn(root);
        RoutingMemory memory = new RoutingMemory(paths, Clock.systemUTC());
        memory.remember("ABC items about quote import", "sc");

        assertThat(memory.remember("ABC items about quote import", "sc")).isFalse();
    }

    @Test
    void keepsTheRuleItRetiredWithTheDateItStoppedBeingTrue() throws IOException {
        when(paths.root()).thenReturn(root);
        Clock frozen = Clock.fixed(Instant.parse("2026-09-29T00:00:00Z"), ZoneOffset.UTC);
        RoutingMemory memory = new RoutingMemory(paths, frozen);

        memory.remember("ABC items about quote import", "sc");
        memory.remember("ABC items about quote import", "api");

        assertThat(Files.readString(root.resolve("memory/routing.md")))
                .contains("# until 2026-09-29: ABC items about quote import -> sc");
    }

    @Test
    void dropsTheRuleNothingHasEverPlacedBeforeOneThatWorks() throws IOException {
        when(paths.root()).thenReturn(root);
        Files.createDirectories(root.resolve("memory"));
        Files.writeString(root.resolve("memory/routing.md"), "oldest but used -> api #2\nnever used -> web\n"
                + IntStream.rangeClosed(1, 58).mapToObj(n -> "used rule " + n + " -> api #1\n")
                        .collect(Collectors.joining()));
        RoutingMemory memory = new RoutingMemory(paths, Clock.systemUTC());

        memory.remember("newest", "sc");

        assertThat(memory.rules()).contains("oldest but used -> api", "newest -> sc").doesNotContain("never used -> web");
    }

    @Test
    void dropsTheOldestOfTheRulesNothingHasPlaced() throws IOException {
        when(paths.root()).thenReturn(root);
        Files.createDirectories(root.resolve("memory"));
        Files.writeString(root.resolve("memory/routing.md"), "oldest -> api\nmiddle -> web\n"
                + IntStream.rangeClosed(1, 58).mapToObj(n -> "used rule " + n + " -> api #1\n")
                        .collect(Collectors.joining()));
        RoutingMemory memory = new RoutingMemory(paths, Clock.systemUTC());

        memory.remember("newest", "sc");

        assertThat(memory.rules()).contains("middle -> web", "newest -> sc").doesNotContain("oldest -> api");
    }

    @Test
    void countsTheSameRuleSaidAgainAsThatRulePlacingAnotherItem() throws IOException {
        when(paths.root()).thenReturn(root);
        RoutingMemory memory = new RoutingMemory(paths, Clock.systemUTC());

        memory.remember("ABC items about quote import", "sc");
        memory.remember("ABC items about quote import", "sc");

        assertThat(Files.readString(root.resolve("memory/routing.md")))
                .contains("ABC items about quote import -> sc #1");
    }

    @Test
    void readsARuleAHumanWroteByHandWithNoCountOnIt() throws IOException {
        when(paths.root()).thenReturn(root);
        Files.createDirectories(root.resolve("memory"));
        Files.writeString(root.resolve("memory/routing.md"), "ABC items about quote import -> sc\n");

        assertThat(new RoutingMemory(paths, Clock.systemUTC()).rules())
                .containsExactly("ABC items about quote import -> sc");
    }

    @Test
    void readsPastTheNotesAHumanLeftBetweenTheRules() throws IOException {
        when(paths.root()).thenReturn(root);
        Files.createDirectories(root.resolve("memory"));
        Files.writeString(root.resolve("memory/routing.md"),
                "# what places what\n\nABC items about quote import -> sc\n");

        assertThat(new RoutingMemory(paths, Clock.systemUTC()).rules())
                .containsExactly("ABC items about quote import -> sc");
    }

    @Test
    void mergesASecondWordingIntoTheRuleItRepeatsKeepingBothCounts() throws IOException {
        when(paths.root()).thenReturn(root);
        Clock frozen = Clock.fixed(Instant.parse("2026-10-07T00:00:00Z"), ZoneOffset.UTC);
        RoutingMemory memory = new RoutingMemory(paths, frozen);
        memory.remember("quote import", "api");
        memory.remember("quote import", "api");
        memory.remember("importing quotes", "api");
        memory.remember("importing quotes", "api");

        memory.merge("quote import -> api", "importing quotes -> api");

        assertThat(Files.readString(root.resolve("memory/routing.md"))).contains("quote import -> api #2")
                .contains("# until 2026-10-07: importing quotes -> api, merged into: quote import")
                .doesNotContain("importing quotes -> api #");
    }

    @Test
    void keepsTwoRulesThatPlaceInDifferentProjects() {
        when(paths.root()).thenReturn(root);
        RoutingMemory memory = new RoutingMemory(paths, Clock.systemUTC());
        memory.remember("quote import", "api");
        memory.remember("importing quotes", "web");

        boolean merged = memory.merge("quote import -> api", "importing quotes -> web");

        assertThat(merged).isFalse();
    }
}
