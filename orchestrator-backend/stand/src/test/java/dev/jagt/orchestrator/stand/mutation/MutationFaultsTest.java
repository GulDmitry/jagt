package dev.jagt.orchestrator.stand.mutation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jagt.orchestrator.stand.StandFailure;
import org.junit.jupiter.api.Test;

class MutationFaultsTest {

  @Test
  void mutationWrittenTwiceIsFailure() {
    String text =
        """
        ## same
        kind: world
        holds: x
        ## same
        kind: world
        holds: x
        """;

    assertThatThrownBy(() -> MutationBook.parse(text))
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("same is written twice");
  }

  @Test
  void mutationExpectingNoTaskStatusIsFailure() {
    String text =
        """
        ## odd
        kind: world
        holds: x
        expects: NOT_A_STATUS
        """;

    assertThatThrownBy(() -> MutationBook.parse(text))
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("no task status");
  }

  @Test
  void mutationNamingNoHoldsIsFailure() {
    String text =
        """
        ## odd
        kind: world
        """;

    assertThatThrownBy(() -> MutationBook.parse(text))
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("names no holds");
  }

  @Test
  void mutationOfAnUnknownKindIsFailure() {
    String text =
        """
        ## odd
        kind: weather
        holds: x
        """;

    assertThatThrownBy(() -> MutationBook.parse(text)).isInstanceOf(StandFailure.class);
  }

  @Test
  void worldMutationNoStandEventPlaysIsFailure() {
    MutationPlan plan = new MutationPlan((ask, ticket) -> ticket);
    Mutation unplayed = new Mutation("unplayed", Mutation.Kind.WORLD, "", "", "x", "", "");

    assertThatThrownBy(() -> plan.apply(unplayed, "ticket")).isInstanceOf(StandFailure.class);
  }

  @Test
  void mutationTheBookDoesNotHoldIsFailure() {
    MutationBook book = MutationBook.shipped();

    assertThatThrownBy(() -> book.named("no-such-mutation")).isInstanceOf(StandFailure.class);
  }
}
