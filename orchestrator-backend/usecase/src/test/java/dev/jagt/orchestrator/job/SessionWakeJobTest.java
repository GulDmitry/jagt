package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.service.SessionWakes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Predicate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionWakeJobTest {

    private final SessionWakes wakes = mock(SessionWakes.class);
    private final AgentSessions sessions = mock(AgentSessions.class);
    private final StepRelay relay = mock(StepRelay.class);

    @Test
    void saysTheResumeLineToEachSessionDue() {
        when(wakes.takeDue(any())).thenReturn(List.of("ABC-42", "ABC-43"));

        new SessionWakeJob(wakes, sessions, relay).run();

        verify(relay).say("ABC-42", "wake", "The usage limit has reset.");
        verify(relay).say("ABC-43", "wake", "The usage limit has reset.");
    }

    @Test
    void holdsBackASessionWhoseWindowHoldsAnUnsentDraft() {
        when(sessions.composing("ABC-42")).thenReturn(true);
        when(wakes.takeDue(any())).thenAnswer(call -> ((Predicate<String>) call.getArgument(0)).test("ABC-42")
                ? List.of() : List.of("ABC-42"));

        new SessionWakeJob(wakes, sessions, relay).run();

        verify(relay, never()).say("ABC-42", "wake", "The usage limit has reset.");
    }

    @Test
    void keepsWakingTheOthersWhenOneSessionIsGone() {
        when(wakes.takeDue(any())).thenReturn(List.of("ABC-42", "ABC-43"));
        doThrow(new IllegalStateException("No session")).when(relay).say("ABC-42", "wake", "The usage limit has reset.");

        new SessionWakeJob(wakes, sessions, relay).run();

        verify(relay).say("ABC-43", "wake", "The usage limit has reset.");
    }
}
