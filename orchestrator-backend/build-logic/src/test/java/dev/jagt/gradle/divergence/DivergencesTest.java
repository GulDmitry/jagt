package dev.jagt.gradle.divergence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DivergencesTest {

  private static final String ROW = "| 2026-11-23 | 111 | tree-a | a Windows path | abc1234 |";

  @Test
  void redRunOfRecordedGreenTreeWithoutRowIsNamed() {
    List<String> runs = List.of("222\tsha-a\tfailure\tpull_request");

    List<String> missing = Divergences.unregistered(runs, sha -> "tree-a", Set.of("tree-a"), ROW);

    assertThat(missing)
        .containsExactly("unregistered: run 222 is red on the recorded-green tree tree-a");
  }

  @Test
  void redRunWithItsRowIsNotNamed() {
    List<String> runs = List.of("111\tsha-a\tfailure\tpull_request");

    assertThat(Divergences.unregistered(runs, sha -> "tree-a", Set.of("tree-a"), ROW)).isEmpty();
  }

  @Test
  void redRunOfTreeNeverRecordedGreenIsNotNamed() {
    List<String> runs = List.of("222\tsha-b\tfailure\tpull_request");

    assertThat(Divergences.unregistered(runs, sha -> "tree-b", Set.of("tree-a"), ROW)).isEmpty();
  }

  @Test
  void redRunOfUnknownCommitIsNotNamed() {
    List<String> runs = List.of("222\tsha-c\tfailure\tpull_request");

    assertThat(Divergences.unregistered(runs, sha -> "", Set.of("tree-a"), ROW)).isEmpty();
  }

  @Test
  void greenCancelledAndNightlyRunsAreNotNamed() {
    List<String> runs =
        List.of(
            "301\tsha-a\tsuccess\tpull_request",
            "302\tsha-a\tcancelled\tpull_request",
            "303\tsha-a\tfailure\tschedule");

    assertThat(Divergences.unregistered(runs, sha -> "tree-a", Set.of("tree-a"), ROW)).isEmpty();
  }

  @Test
  void timedOutRunOfRecordedGreenTreeIsNamed() {
    List<String> runs = List.of("401\tsha-a\ttimed_out\tpush");

    assertThat(Divergences.unregistered(runs, sha -> "tree-a", Set.of("tree-a"), ROW)).hasSize(1);
  }
}
