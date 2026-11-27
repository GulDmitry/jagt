package dev.jagt.orchestrator.service.reach;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.flow.Unclassified;
import java.util.List;
import org.junit.jupiter.api.Test;

class UnclassifiedMovesTest {

  @Test
  void marksTheBoardHeadWithTheKindAndWhereItHasNoClass() {
    ReachLedger ledger = new ReachLedger();
    UnclassifiedMoves moves = new UnclassifiedMoves(ledger);

    moves.accept(new Unclassified("MergedByHost", List.of("request.OPEN", "checks.NONE"), false));

    assertThat(ledger.standing())
        .singleElement()
        .satisfies(lost -> {
          assertThat(lost.key()).isEqualTo("flow@MergedByHost");
          assertThat(lost.said()).contains("request.OPEN checks.NONE");
        });
  }
}
