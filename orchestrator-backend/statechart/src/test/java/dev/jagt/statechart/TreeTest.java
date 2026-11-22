package dev.jagt.statechart;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.statechart.TestHistoryBuilder.Place;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TreeTest {

  private final Tree<String> nested =
      Tree.of(
          Map.of(Place.S0, List.of(Place.S01), Place.S01, List.of(Place.S011)),
          Map.of(),
          Map.of(),
          Map.of(),
          Map.of(),
          Map.of());

  @Test
  void moveEntersTheAncestorsOfTheTargetOutermostFirst() {
    Traversal move = nested.move(Place.S3, Place.S011, Place.S3);

    assertThat(move.entries())
        .extracting(Traversal.Entry::state)
        .containsExactly(Place.S0, Place.S01, Place.S011);
  }

  @Test
  void moveWithinEntersOnlyBelowItsSourceOutermostFirst() {
    Traversal move = nested.moveWithin(Place.S0, Place.S011, Place.S011);

    assertThat(move.entries())
        .extracting(Traversal.Entry::state)
        .containsExactly(Place.S01, Place.S011);
  }
}
