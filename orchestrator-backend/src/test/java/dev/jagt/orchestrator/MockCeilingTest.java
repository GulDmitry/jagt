package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MockCeilingTest {

  @Test
  void testWiresTheCollaboratorCeilingOfMocksAtMost() {
    assertThat(MockedTypes.unitTests().flatMap(MockedTypes::overTheCeiling)).isEmpty();
  }

  @Test
  void readsEveryUnitSuiteItClaimsToCheck() {
    assertThat(MockedTypes.unitTests().count()).isGreaterThan(200);
  }
}
