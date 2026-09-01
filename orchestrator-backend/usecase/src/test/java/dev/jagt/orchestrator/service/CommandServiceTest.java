package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.FlowEngine;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.flow.TaskAction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandServiceTest {

    private final FlowEngine flow = mock(FlowEngine.class);
    private final CommandService commands = new CommandService(flow);

    @Test
    void handsBackWhateverTheFlowSaidWithoutRewordingIt() {
        when(flow.run("ABC-1", TaskAction.SHIP)).thenReturn("ship ABC-1: approval relayed");

        assertThat(commands.execute("ABC-1", TaskAction.SHIP)).isEqualTo("ship ABC-1: approval relayed");
    }

    @Test
    void passesAnAliasDownUntouchedSoOnlyOnePlaceResolvesIt() {
        commands.execute("a1", TaskAction.FOCUS);

        verify(flow).run("a1", TaskAction.FOCUS);
    }

    @Test
    void letsARefusalReachTheCallerWithTheCodeItMustActOn() {
        when(flow.run("ABC-1", TaskAction.DEPLOY)).thenThrow(new Refusal(Refusal.Code.ACTION_NOT_AVAILABLE,
                "Deploy is not available for ABC-1 (it is IN_PROGRESS — agent working)"));

        assertThatThrownBy(() -> commands.execute("ABC-1", TaskAction.DEPLOY))
                .asInstanceOf(type(Refusal.class))
                .extracting(Refusal::code).isEqualTo(Refusal.Code.ACTION_NOT_AVAILABLE);
    }
}
