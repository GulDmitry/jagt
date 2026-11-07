package dev.jagt.orchestrator.flow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.Session;
import org.junit.jupiter.api.Test;

class SessionSignReadTest {

  private static final Session.Halt GONE_AT_100 =
      new Session.Halt(Session.Halt.Kind.GONE, 100, "e");

  @Test
  void keepsTheGoneHaltWhereTheProcessRunsButNothingWasReadAfterIt() {
    SessionSign.Read running = new SessionSign.Read(Session.Window.State.RUNNING, 0, 0, 0, 0, null);

    Session read = running.applyTo(Session.defaults().withHalt(GONE_AT_100), 100);

    assertThat(read.halt()).isEqualTo(GONE_AT_100);
  }

  @Test
  void dropsTheGoneHaltWhereTheProcessRunsAgainAfterIt() {
    SessionSign.Read running = new SessionSign.Read(Session.Window.State.RUNNING, 0, 0, 0, 0, null);

    Session read = running.applyTo(Session.defaults().withHalt(GONE_AT_100), 101);

    assertThat(read.halt()).isNull();
  }

  @Test
  void keepsTheIdleHaltWhereTheWindowPrintedNothingSinceIt() {
    Session.Halt idle = new Session.Halt(Session.Halt.Kind.IDLE, 100, null);
    SessionSign.Read running =
        new SessionSign.Read(Session.Window.State.RUNNING, 100, 0, 0, 0, null);

    Session read = running.applyTo(Session.defaults().withHalt(idle), 200);

    assertThat(read.halt()).isEqualTo(idle);
  }

  @Test
  void goesQuietWhereTheStaleAfterSpanHasJustPassedSinceTheLastMove() {
    SessionSign.Read running =
        new SessionSign.Read(Session.Window.State.RUNNING, 0, 100, 0, 50, null);

    Session read = running.applyTo(Session.defaults(), 150);

    assertThat(read.quietSince()).isEqualTo(100);
  }

  @Test
  void keepsTheWindowWhereItsStateAndPrintedAtAreUnchanged() {
    Session held =
        Session.defaults().withWindow(new Session.Window(Session.Window.State.RUNNING, 5, 7));

    Session same =
        new SessionSign.Read(Session.Window.State.RUNNING, 7, 0, 0, 0, null).applyTo(held, 100);
    Session printed =
        new SessionSign.Read(Session.Window.State.RUNNING, 8, 0, 0, 0, null).applyTo(held, 100);

    assertThat(same.window().at()).isEqualTo(5);
    assertThat(printed.window().at()).isEqualTo(100);
  }

  @Test
  void keepsTheWindowAsItWasWhereTheReadFoundNothingToRead() {
    Session held =
        Session.defaults().withWindow(new Session.Window(Session.Window.State.RUNNING, 5, 7));

    Session read =
        new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 0, 0, null).applyTo(held, 100);

    assertThat(read.window()).isEqualTo(held.window());
  }
}
