package dev.jagt.orchestrator.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.jagt.orchestrator.task.Session;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SessionTransitionLogTest {

    @Test
    @ResourceLock(Resources.GLOBAL)
    void logsOnceWhenASessionsWindowGoesFromRunningToMissing() {
        ListAppender<ILoggingEvent> log = new ListAppender<>();
        log.start();
        Logger watched = (Logger) LoggerFactory.getLogger(SessionTransitionLog.class);
        watched.addAppender(log);
        try {
            SessionTransitionLog transitions = new SessionTransitionLog(mock(StateService.class));
            TaskState running = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build().withSession(
                    Session.defaults().withWindow(new Session.Window(Session.Window.State.RUNNING, 1_000, 0)));
            TaskState missing = running.withSession(
                    running.session().withWindow(new Session.Window(Session.Window.State.MISSING, 2_000, 0)));

            transitions.observe(Map.of("ABC-1", running));
            transitions.observe(Map.of("ABC-1", missing));

            assertThat(List.copyOf(log.list)).filteredOn(event -> "session window changed".equals(event.getMessage()))
                    .hasSize(1).flatExtracting(ILoggingEvent::getKeyValuePairs)
                    .extracting(pair -> pair.key + "=" + pair.value)
                    .containsExactly("task=ABC-1", "from=RUNNING", "to=MISSING");
        } finally {
            watched.detachAppender(log);
        }
    }
}
