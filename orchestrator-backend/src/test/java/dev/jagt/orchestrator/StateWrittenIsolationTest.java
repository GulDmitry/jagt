package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import dev.jagt.orchestrator.port.StateEvents.StateWritten;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class StateWrittenIsolationTest {

  @Test
  void laterListenerStillHearsWrittenStateWhenAnEarlierOneThrows() {
    AtomicInteger second = new AtomicInteger();
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    context.registerBean("applicationEventMulticaster", StateWrittenIsolation.class);
    context.addApplicationListener(
        new FakeStateWrittenListener(() -> {
          throw new IllegalStateException("this listener is broken");
        }));
    context.addApplicationListener(new FakeStateWrittenListener(second::incrementAndGet));
    context.refresh();

    context.publishEvent(new StateWritten(Map.of(), Map.of()));

    assertThat(second).hasValue(1);
  }

  @Test
  void failureOfAnyOtherEventStillPropagates() {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    context.registerBean("applicationEventMulticaster", StateWrittenIsolation.class);
    context.addApplicationListener(
        new FakeStartupListener(() -> {
          throw new IllegalStateException("startup refused");
        }));
    context.refresh();

    assertThatIllegalStateException()
        .isThrownBy(() -> context.publishEvent(new FakeStartupEvent()));
  }
}
