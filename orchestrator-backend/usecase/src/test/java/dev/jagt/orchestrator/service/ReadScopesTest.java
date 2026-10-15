package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.service.ReadScopes.ReadScope;
import org.junit.jupiter.api.Test;

import java.util.List;

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
    void keepsNoCallOfARunAlreadyOver() {
        ReadScopes scopes = new ReadScopes();

        scopes.hear("gone", "mcp__acme__get_issue", "{}");

        assertThat(scopes.heard("gone")).isEmpty();
    }
}
