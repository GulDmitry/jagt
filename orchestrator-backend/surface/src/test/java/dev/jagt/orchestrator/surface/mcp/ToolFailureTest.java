package dev.jagt.orchestrator.surface.mcp;

import dev.jagt.orchestrator.flow.Refusal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ToolFailureTest {

    static Stream<Arguments> thrown() {
        return Stream.of(
                Arguments.of(new ToolRefusal(ToolFailure.PERMISSION, "deploy_task is Master-only"),
                        ToolFailure.PERMISSION),
                Arguments.of(new IllegalArgumentException("mode must be one of [diff, project]"),
                        ToolFailure.VALIDATION),
                Arguments.of(new Refusal(Refusal.Code.ACTION_NOT_AVAILABLE, "Deploy is not available now"),
                        ToolFailure.BUSINESS),
                Arguments.of(new Refusal(Refusal.Code.NO_SUCH_TASK, "Task ABC-9 not found in state.json"),
                        ToolFailure.BUSINESS),
                Arguments.of(new IllegalStateException("branch ABC-1 is checked out elsewhere"), ToolFailure.BUSINESS),
                Arguments.of(new UncheckedIOException(new IOException("state.json locked")), ToolFailure.TRANSIENT));
    }

    @ParameterizedTest
    @MethodSource("thrown")
    void readsWhatToDoNextOffWhatTheHandlerThrew(Exception failure, ToolFailure expected) {
        assertThat(ToolFailure.of(failure)).isEqualTo(expected);
    }
}
