package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PortsMapTest {

  @Test
  void readsEveryRowOfThePortsTable() {
    assertThat(PortsMap.rows()).hasSizeGreaterThan(30);
  }

  @Test
  void everyPortTheMapNamesExistsInMain() {
    assertThat(PortsMap.portsNotInMain()).isEmpty();
  }

  @Test
  void everyClassTheMapNamesExistsInMain() {
    assertThat(PortsMap.answersNotInMain()).isEmpty();
  }

  @Test
  void everyClassTheMapGivesAsTheAnswerImplementsItsPort() {
    assertThat(PortsMap.answersNotImplementingTheirPort()).isEmpty();
  }
}
