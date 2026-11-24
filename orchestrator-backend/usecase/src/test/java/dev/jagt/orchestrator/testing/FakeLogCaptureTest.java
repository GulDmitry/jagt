package dev.jagt.orchestrator.testing;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.mock.env.MockEnvironment;

class FakeLogCaptureTest {

  private static final Logger SHARED =
      LoggerFactory.getLogger("dev.jagt.orchestrator.testing.shared");

  @Test
  void capturesNothingAfterItIsClosed() {
    FakeLogCapture capture = new FakeLogCapture(SHARED.getName());
    capture.close();

    SHARED.warn("after close");

    assertThat(capture.events()).isEmpty();
  }

  @Test
  void keepsItsEventsWhenSpringInitialisesLoggingWhileItListens() {
    try (FakeLogCapture capture = new FakeLogCapture(SHARED.getName())) {
      LoggingSystem spring = LoggingSystem.get(getClass().getClassLoader());
      spring.beforeInitialize();
      spring.initialize(new LoggingInitializationContext(new MockEnvironment()), null, null);

      SHARED.warn("still heard");

      assertThat(capture.events())
          .extracting(ILoggingEvent::getMessage)
          .containsExactly("still heard");
    }
  }
}
