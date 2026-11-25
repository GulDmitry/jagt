package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class StandSlotsTest {

  @Test
  void freedSlotIsTakenAgainWhileCapIsFull() {
    StandSlots slots = new StandSlots(2);
    slots.take();
    slots.take();
    slots.free();

    assertThatCode(slots::take).doesNotThrowAnyException();
  }
}
