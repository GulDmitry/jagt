package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.service.TicketReader;
import dev.jagt.orchestrator.port.TrackerAssistant;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.TrackerWorkflow;
import dev.jagt.orchestrator.task.TicketFacts;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntakeCandidatesTest {

    private final TrackerAssistant assistant = mock(TrackerAssistant.class);
    private final TicketReader tickets = mock(TicketReader.class);
    private final TrackerWorkflow workflow = mock(TrackerWorkflow.class);
    private final IntakeCandidates candidates = new IntakeCandidates(assistant, tickets, workflow);

    @Test
    void answersNothingAtAllWhenTheSearchNeverReachedTheTracker() {
        when(workflow.candidateQuery()).thenReturn("whatever is ready");
        when(assistant.findCandidates("whatever is ready")).thenReturn(Answer.unavailable());

        assertThat(candidates.waiting(Set.of())).isEmpty();
    }

    @Test
    @ResourceLock(Resources.GLOBAL)
    void warnsRatherThanErrsWhenAPollNeverReachedTheTracker() {
        ListAppender<ILoggingEvent> log = new ListAppender<>();
        log.start();
        Logger intakeLog = (Logger) LoggerFactory.getLogger(IntakeCandidates.class);
        intakeLog.addAppender(log);
        when(workflow.candidateQuery()).thenReturn("whatever is ready");
        when(assistant.findCandidates("whatever is ready")).thenReturn(Answer.unavailable());

        candidates.waiting(Set.of());

        assertThat(log.list).extracting(ILoggingEvent::getLevel).containsExactly(Level.WARN);
        intakeLog.detachAppender(log);
    }

    @Test
    void buysNoReadForAnItemJagtAlreadyHolds() {
        when(workflow.candidateQuery()).thenReturn("whatever is ready");
        when(assistant.findCandidates("whatever is ready"))
                .thenReturn(new Answer<>(Optional.of(List.of("ABC-42")), TokenUsage.NONE));

        candidates.waiting(Set.of("ABC-42"));

        verify(tickets, never()).read(anyString());
    }

    @Test
    void buysNoReadForSomethingTheSearchAnsweredThatIsNotAnIssueKey() {
        when(workflow.candidateQuery()).thenReturn("whatever is ready");
        when(assistant.findCandidates("whatever is ready"))
                .thenReturn(new Answer<>(Optional.of(List.of("I could not find any")), TokenUsage.NONE));

        candidates.waiting(Set.of());

        verify(tickets, never()).read(anyString());
    }

    @Test
    void asksTheWorkflowNothingAboutAnItemWhoseStageNobodyCouldRead() {
        when(workflow.candidateQuery()).thenReturn("whatever is ready");
        when(assistant.findCandidates("whatever is ready"))
                .thenReturn(new Answer<>(Optional.of(List.of("ABC-42")), TokenUsage.NONE));
        when(tickets.read("ABC-42")).thenReturn(new Answer<>(Optional.of(TicketFacts.defaults()
                .withExists(true).withKey("ABC-42").withTitle("Widget layout is off")
                .withUrl("https://tracker/ABC-42")), TokenUsage.NONE));

        assertThat(candidates.waiting(Set.of())).contains(List.of());
        verify(workflow, never()).startsWork(any());
    }

    @Test
    void offersTheItemTheWorkflowSaysAMachineMayStart() {
        TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-42")
                .withTitle("Widget layout is off").withUrl("https://tracker/ABC-42")
                .withTrackerStatus("In Progress").withAssignee("dzmitry");
        when(workflow.candidateQuery()).thenReturn("whatever is ready");
        when(assistant.findCandidates("whatever is ready"))
                .thenReturn(new Answer<>(Optional.of(List.of("ABC-42")), TokenUsage.NONE));
        when(tickets.read("ABC-42")).thenReturn(new Answer<>(Optional.of(item), TokenUsage.NONE));
        when(workflow.startsWork(item)).thenReturn(true);

        assertThat(candidates.waiting(Set.of())).contains(List.of(new IntakeCandidates.Ready(item,
                TokenUsage.NONE)));
    }
}
