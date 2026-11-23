package dev.jagt.orchestrator.service.presence;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import dev.jagt.orchestrator.port.StateEvents.StateWritten;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.session.Session;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class SessionTransitionLogTest {

  @Test
  void logsOnceWhenSessionsWindowGoesFromRunningToMissing() {
    try (FakeLogCapture log = new FakeLogCapture(SessionTransitionLog.class)) {
      AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
      context.registerBean(SessionTransitionLog.class);
      context.refresh();
      TaskState running =
          TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
              .build()
              .withSession(
                  Session.defaults()
                      .withWindow(new Session.Window(Session.Window.State.RUNNING, 1_000, 0)));
      TaskState missing =
          running.withSession(
              running
                  .work()
                  .session()
                  .withWindow(new Session.Window(Session.Window.State.MISSING, 2_000, 0)));

      context.publishEvent(new StateWritten(Map.of("ABC-1", running), Map.of("ABC-1", missing)));

      assertThat(log.events())
          .filteredOn(event -> "session window changed".equals(event.getMessage()))
          .hasSize(1)
          .flatExtracting(ILoggingEvent::getKeyValuePairs)
          .extracting(pair -> pair.key + "=" + pair.value)
          .containsExactly("task=ABC-1", "from=RUNNING", "to=MISSING");
    }
  }
}
