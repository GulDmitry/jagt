package dev.jagt.orchestrator.adapter.tmux;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TmuxSessionHostTest {

    @ParameterizedTest
    @ValueSource(strings = {"jagt:agents", "jagt.agents"})
    void refusesASessionNameTmuxWouldReadAsAWindowOrAPane(String name) {
        assertThat(TmuxSessionHost.reserved(name)).contains("contains ':' or '.', which tmux reserves");
    }
}
