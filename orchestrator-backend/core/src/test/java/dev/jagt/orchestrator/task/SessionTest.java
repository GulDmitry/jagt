package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionTest {

    @Test
    void countsAStartedOverSessionAsTheNextGeneration() {
        Session next = Session.startedOver(Session.defaults().withGeneration(2));

        assertThat(next.generation()).isEqualTo(3);
    }

    @Test
    void isAliveOnlyWhereTheLastWindowReadShowedItsProcessRunning() {
        Session running = Session.defaults().withWindow(new Session.Window(Session.Window.State.RUNNING, 1, 0));
        Session dead = Session.defaults().withWindow(new Session.Window(Session.Window.State.DEAD_SHELL, 1, 0));

        assertThat(running.alive()).isTrue();
        assertThat(dead.alive()).isFalse();
        assertThat(Session.defaults().alive()).isNull();
    }
}
