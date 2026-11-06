package dev.jagt.orchestrator.adapter.assistant;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenCodeScopeTest {

    @Test
    void letsTheGateFetchTheEnvironmentsWhereTheRoleFetches() {
        OpenCodeScope scope = OpenCodeScope.of(List.of(), List.of(), List.of(), true, false, List.of(Path.of("/wt")),
                List.of("https://dev.example.test"));

        assertThat(scope.fenced().fetchable()).containsExactly("https://dev.example.test");
    }

    @Test
    void letsTheGateFetchNothingWhereTheRoleDoesNotFetch() {
        OpenCodeScope scope = OpenCodeScope.of(List.of(), List.of(), List.of(), false, true, List.of(Path.of("/wt")),
                List.of("https://dev.example.test"));

        assertThat(scope.fenced().fetchable()).isEmpty();
        assertThat(scope.fenced().git()).isTrue();
        assertThat(scope.fenced().roots()).containsExactly(Path.of("/wt"));
    }
}
