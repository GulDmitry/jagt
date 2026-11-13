package dev.jagt.orchestrator.testing;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.LoggerFactory;

public final class FakeLogCapture implements AutoCloseable {

  private final Logger logger;
  private final OwnThreadAppender appender;

  public FakeLogCapture(Class<?> source) {
    this(source.getName());
  }

  public FakeLogCapture(String loggerName) {
    logger = (Logger) LoggerFactory.getLogger(loggerName);
    appender = new OwnThreadAppender(Thread.currentThread());
    appender.start();
    logger.addAppender(appender);
  }

  public List<ILoggingEvent> events() {
    return List.copyOf(appender.events);
  }

  @Override
  public void close() {
    logger.detachAppender(appender);
    appender.stop();
  }

  private static final class OwnThreadAppender extends AppenderBase<ILoggingEvent> {

    private final Thread owner;
    private final List<ILoggingEvent> events = new CopyOnWriteArrayList<>();

    OwnThreadAppender(Thread owner) {
      this.owner = owner;
    }

    @Override
    protected void append(ILoggingEvent event) {
      if (Thread.currentThread() == owner) {
        events.add(event);
      }
    }
  }
}
