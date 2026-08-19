package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.port.TaskCapability;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilitiesTest {

    private record Declared(TaskAction action, int priority, String says) implements TaskCapability {

        @Override
        public Outcome run(String taskId) {
            return Outcome.ok(says);
        }
    }

    @Test
    void theHigherRankingClaimantIsTheOneAnActionReaches() {
        Capabilities capabilities = new Capabilities(List.of(
                new Declared(TaskAction.DEPLOY, 0, "the built-in deploy"),
                new Declared(TaskAction.DEPLOY, 10, "the install's own deploy")));

        assertThat(capabilities.of(TaskAction.DEPLOY).orElseThrow().run("ABC-1").message())
                .isEqualTo("the install's own deploy");
    }

    @Test
    void picksTheHigherRankingClaimantWhicheverOrderTheInstallDeclaredThemIn() {
        Capabilities capabilities = new Capabilities(List.of(
                new Declared(TaskAction.SHIP, 10, "the install's own ship"),
                new Declared(TaskAction.SHIP, 0, "the built-in ship")));

        assertThat(capabilities.of(TaskAction.SHIP).orElseThrow().run("ABC-1").message())
                .isEqualTo("the install's own ship");
    }

    @Test
    void anActionNobodyClaimsResolvesToNothingRatherThanToSomethingElse() {
        Capabilities capabilities = new Capabilities(List.of(new Declared(TaskAction.FOCUS, 0, "focused")));

        assertThat(capabilities.of(TaskAction.REVERT)).isEmpty();
    }
}
