package dev.jagt.orchestrator.surface.ui;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.spi.FilterReply;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;

class TaskAliasLogFilterTest {

  @Test
  void aLineNamingOnlyItsTaskCarriesTheTasksAlias() {
    TaskAliasLogFilter.remember("ABC-42", "p3");
    var event =
        new LoggingEvent(
            "x", new LoggerContext().getLogger("x"), Level.INFO, "master presses", null, null);
    event.addKeyValuePair(new KeyValuePair("task", "ABC-42"));

    new TaskAliasLogFilter().decide(event);

    assertThat(event.getKeyValuePairs())
        .extracting(pair -> pair.key + "=" + pair.value)
        .containsExactly("task=ABC-42", "alias=p3");
  }

  @Test
  void everyLineStillReachesItsAppender() {
    var event =
        new LoggingEvent(
            "x", new LoggerContext().getLogger("x"), Level.INFO, "master presses", null, null);

    FilterReply reply = new TaskAliasLogFilter().decide(event);

    assertThat(reply).isEqualTo(FilterReply.NEUTRAL);
  }

  @Test
  void aLineAnotherAppenderAlreadyGaveItsAliasKeepsOneAlias() {
    TaskAliasLogFilter.remember("ABC-43", "p4");
    var event =
        new LoggingEvent(
            "x", new LoggerContext().getLogger("x"), Level.INFO, "master presses", null, null);
    event.addKeyValuePair(new KeyValuePair("task", "ABC-43"));

    new TaskAliasLogFilter().decide(event);
    new TaskAliasLogFilter().decide(event);

    assertThat(event.getKeyValuePairs()).extracting(pair -> pair.key).containsOnlyOnce("alias");
  }
}
