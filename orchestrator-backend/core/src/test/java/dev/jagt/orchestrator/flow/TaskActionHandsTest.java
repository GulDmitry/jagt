package dev.jagt.orchestrator.flow;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class TaskActionHandsTest {

    @Test
    void keepsExactlyTheVerbThatClosesATaskInAHumansHands() {
        var kept = Arrays.stream(TaskAction.values()).filter(TaskAction::humanOnly).toList();

        assertThat(kept).containsExactly(TaskAction.DONE);
    }
}
