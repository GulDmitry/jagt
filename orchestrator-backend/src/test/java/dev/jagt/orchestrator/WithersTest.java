package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WithersTest {

  @Test
  void witherChangesItsOwnComponentAndNoOther() {
    assertThat(RecordWithers.all().map(RecordWithers::defect).filter(defect -> !defect.isEmpty()))
        .isEmpty();
  }

  @Test
  void readsEveryWitherItClaimsToCheck() {
    assertThat(RecordWithers.all().count()).isGreaterThan(80);
  }
}
