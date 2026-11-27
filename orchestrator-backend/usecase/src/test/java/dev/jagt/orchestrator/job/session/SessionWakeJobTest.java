package dev.jagt.orchestrator.job.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.session.SessionStanding;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.presence.SessionWakes;
import dev.jagt.orchestrator.service.turn.StepRelay;
import dev.jagt.orchestrator.task.turn.Step;
import dev.jagt.orchestrator.testing.FakeJobFaults;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class SessionWakeJobTest {

  private final SessionWakes wakes = mock(SessionWakes.class);
  private final AgentSessions sessions = mock(AgentSessions.class);
  private final StepRelay relay = mock(StepRelay.class);

  @Test
  void saysTheResumeLineToEachSessionDue() {
    when(wakes.takeDue(any()))
        .thenReturn(
            List.of(
                new SessionWakes.Due("ABC-42", Step.WAKE, SessionStanding.ABSENT),
                new SessionWakes.Due("ABC-43", Step.WAKE, SessionStanding.ABSENT)));

    new SessionWakeJob(wakes, sessions, relay, new FakeJobFaults()).run();

    verify(relay).say("ABC-42", Step.WAKE, "The usage limit has reset.", SessionStanding.ABSENT);
    verify(relay).say("ABC-43", Step.WAKE, "The usage limit has reset.", SessionStanding.ABSENT);
  }

  @Test
  void holdsBackSessionWhoseWindowHoldsAnUnsentDraft() {
    when(sessions.composing("ABC-42")).thenReturn(true);
    when(wakes.takeDue(any()))
        .thenAnswer(call ->
            call.<Predicate<String>>getArgument(0).test("ABC-42")
                ? List.of()
                : List.of(new SessionWakes.Due("ABC-42", Step.WAKE, SessionStanding.ABSENT)));

    new SessionWakeJob(wakes, sessions, relay, new FakeJobFaults()).run();

    verify(relay, never())
        .say("ABC-42", Step.WAKE, "The usage limit has reset.", SessionStanding.ABSENT);
  }

  @Test
  void keepsWakingTheOthersWhenOneSessionIsGone() {
    when(wakes.takeDue(any()))
        .thenReturn(
            List.of(
                new SessionWakes.Due("ABC-42", Step.WAKE, SessionStanding.ABSENT),
                new SessionWakes.Due("ABC-43", Step.WAKE, SessionStanding.ABSENT)));
    doThrow(new IllegalStateException("No session"))
        .when(relay)
        .say("ABC-42", Step.WAKE, "The usage limit has reset.", SessionStanding.ABSENT);
    FakeJobFaults faults = new FakeJobFaults();

    new SessionWakeJob(wakes, sessions, relay, faults).run();

    verify(relay).say("ABC-43", Step.WAKE, "The usage limit has reset.", SessionStanding.ABSENT);
    assertThat(faults.causes()).containsExactly("No session");
  }

  @Test
  void tellsSessionWhoseWaitIsOverToProbeWhatItNamed() {
    when(wakes.takeDue(any()))
        .thenReturn(
            List.of(new SessionWakes.Due("ABC-42", Step.WAIT_OVER, SessionStanding.ABSENT)));

    new SessionWakeJob(wakes, sessions, relay, new FakeJobFaults()).run();

    verify(relay)
        .say("ABC-42", Step.WAIT_OVER, "The wait you asked for is over.", SessionStanding.ABSENT);
  }
}
