package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.turn.ActionOrigin;
import org.junit.jupiter.api.Test;

class ThreadOriginTest {

  @Test
  void namesTheOriginTheEntryPointOpenedOnThisThread() {
    ActionOrigin seen = OriginContext.as(ActionOrigin.MASTER, () -> new ThreadOrigin().current());

    assertThat(seen).isEqualTo(ActionOrigin.MASTER);
  }
}
