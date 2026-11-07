package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.port.ReachLost;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReachLedgerTest {

  private final ReachLedger reach = new ReachLedger();

  @Test
  void oneDriverOnOneHostLostTwiceStandsOnce() {
    reach.lost(
        new ReachLost(
            "glab", "git.example.invalid", List.of("glab", "auth", "login"), "signed out"));
    reach.lost(
        new ReachLost(
            "glab", "git.example.invalid", List.of("glab", "auth", "login"), "signed out"));

    assertThat(reach.standing()).hasSize(1);
  }

  @Test
  void twoHostsOfOneDriverStandApartAndEndApart() {
    reach.lost(new ReachLost("glab", "a.example.invalid", List.of(), "unreachable"));
    reach.lost(new ReachLost("glab", "b.example.invalid", List.of(), "unreachable"));

    reach.reached("glab", "a.example.invalid");

    assertThat(reach.standing()).extracting(ReachLost::host).containsExactly("b.example.invalid");
  }
}
