package dev.jagt.orchestrator.task.master;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.flow.role.Arbiter;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MasterRightTest {

  @ParameterizedTest
  @CsvSource({"deploy,DEPLOY", "' Reply ',REPLY", "PLAN,PLAN", "repo,REPO"})
  void readsMasterRightByItsWord(String word, MasterRight right) {
    assertThat(MasterRight.of(word)).contains(right);
  }

  @ParameterizedTest
  @CsvSource({
    "ACT, none, SHIP, true",
    "ACT, none, REPLY, true",
    "ACT, reply, SHIP, true",
    "ACT, reply, REPLY, false",
    "ACT, ship, SHIP, false",
    "ACT, ship, REPLY, true",
    "JUDGE, none, SHIP, false",
    "JUDGE, none, REPLY, false",
    "OFF, none, SHIP, false",
    "OFF, none, REPLY, false"
  })
  void masterMayShipAndReplyOnlyInActAndWhereTheHumanKeptNeither(
      MasterMode mode, String mine, MasterRight right, boolean may) {
    assertThat(new Arbiter.Profile(mode, Set.of(mine)).may(right)).isEqualTo(may);
  }

  @Test
  void refusesMasterRightNoneOfTheWordsMean() {
    assertThat(MasterRight.of("sometimes")).isEmpty();
    assertThat(MasterRight.of(null)).isEmpty();
    assertThat(MasterRight.ids())
        .containsExactly("ship", "deploy", "revert", "reply", "answer", "plan", "repo", "lift");
  }
}
