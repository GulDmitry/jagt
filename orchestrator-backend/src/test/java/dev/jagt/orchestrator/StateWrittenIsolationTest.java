package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import dev.jagt.orchestrator.port.StateEvents.StateWritten;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class StateWrittenIsolationTest {

  static class Startup extends ApplicationEvent {
    Startup() {
      super("startup");
    }
  }

  @Test
  void laterListenerStillHearsWrittenStateWhenAnEarlierOneThrows() {
    AtomicInteger second = new AtomicInteger();
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    context.registerBean("applicationEventMulticaster", StateWrittenIsolation.class);
    context.addApplicationListener(
        new ApplicationListener<PayloadApplicationEvent<StateWritten>>() {
          @Override
          public void onApplicationEvent(PayloadApplicationEvent<StateWritten> event) {
            throw new IllegalStateException("this listener is broken");
          }
        });
    context.addApplicationListener(
        new ApplicationListener<PayloadApplicationEvent<StateWritten>>() {
          @Override
          public void onApplicationEvent(PayloadApplicationEvent<StateWritten> event) {
            second.incrementAndGet();
          }
        });
    context.refresh();

    context.publishEvent(new StateWritten(Map.of(), Map.of()));

    assertThat(second).hasValue(1);
  }

  @Test
  void failureOfAnyOtherEventStillPropagates() {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    context.registerBean("applicationEventMulticaster", StateWrittenIsolation.class);
    context.addApplicationListener(
        new ApplicationListener<Startup>() {
          @Override
          public void onApplicationEvent(Startup event) {
            throw new IllegalStateException("startup refused");
          }
        });
    context.refresh();

    assertThatIllegalStateException()
        .isThrownBy(() -> context.publishEvent(new Startup()));
  }
}
