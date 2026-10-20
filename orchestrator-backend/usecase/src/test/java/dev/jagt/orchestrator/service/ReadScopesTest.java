package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.service.ReadScopes.ReadScope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ReadScopesTest {

    @Test
    void cutsACallWhoseArgumentsRunPastTwoHundredCharacters() {
        ReadScopes scopes = new ReadScopes();
        scopes.open("f1", new ReadScope(List.of(), List.of(), false));

        scopes.hear("f1", "mcp__acme__search", "x".repeat(300));

        assertThat(scopes.heard("f1")).singleElement().asString().hasSize(201).endsWith("x…");
    }

    @Test
    void keepsACallWhoseArgumentsAreExactlyTwoHundredCharacters() {
        ReadScopes scopes = new ReadScopes();
        scopes.open("f1", new ReadScope(List.of(), List.of(), false));

        scopes.hear("f1", "t", "x".repeat(198));

        assertThat(scopes.heard("f1")).singleElement().asString().hasSize(200).doesNotEndWith("…");
    }

    @Test
    void keepsAtMostFiftyCallsOfARun() {
        ReadScopes scopes = new ReadScopes();
        scopes.open("f1", new ReadScope(List.of(), List.of(), false));

        IntStream.range(0, 51).forEach(call -> scopes.hear("f1", "tool", call));

        assertThat(scopes.heard("f1")).hasSize(50).doesNotContain("tool 50");
    }

    @Test
    void handsOutTheScopeOpenedUnderAFenceAndCountsTheAsk() {
        ReadScopes scopes = new ReadScopes();
        ReadScope scope = new ReadScope(List.of(), List.of("Read"), false);
        scopes.open("f1", scope);

        assertThat(scopes.ask("f1")).contains(scope);
        assertThat(scopes.close("f1")).isTrue();
    }

    @Test
    void reportsAFenceNobodyAskedUnderAsOneWhoseHookNeverRan() {
        ReadScopes scopes = new ReadScopes();
        scopes.open("f1", new ReadScope(List.of(), List.of("Read"), false));

        assertThat(scopes.close("f1")).isFalse();
    }

    @Test
    void handsOutNoScopeForAFenceThatWasNeverOpened() {
        ReadScopes scopes = new ReadScopes();

        assertThat(scopes.ask("unknown")).isEmpty();
        assertThat(scopes.ask(null)).isEmpty();
    }

    @Test
    void forgetsTheCallsOfAClosedRun() {
        ReadScopes scopes = new ReadScopes();
        scopes.open("f1", new ReadScope(List.of(), List.of(), false));
        scopes.hear("f1", "tool", "{}");

        scopes.close("f1");

        assertThat(scopes.heard("f1")).isEmpty();
    }

    @Test
    void keepsNoCallOfARunAlreadyOver() {
        ReadScopes scopes = new ReadScopes();

        scopes.hear("gone", "mcp__acme__get_issue", "{}");

        assertThat(scopes.heard("gone")).isEmpty();
    }
}
