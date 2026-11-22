package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class StandClockTest {

  @Test
  void readsTheMachinesClockNotFrozenOne() {
    Instant before = Instant.now(Clock.systemUTC());

    Instant read = StandClock.now();

    assertThat(read).isBetween(before, Instant.now(Clock.systemUTC()));
  }
}
