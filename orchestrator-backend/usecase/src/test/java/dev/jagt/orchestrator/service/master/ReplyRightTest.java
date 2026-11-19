package dev.jagt.orchestrator.service.master;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.service.CaseDoor;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.ArbiterConfig;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReplyRightTest {

  private final CaseDoor door = mock(CaseDoor.class);
  private final ReplyRight right = new ReplyRight(door);

  @Test
  void forbidsTheMasterFromAnsweringPersonWhereThatRightWasWithheld() {
    ConfigFile config =
        ConfigFile.defaults()
            .withArbiter(ArbiterConfig.defaults().withMode("act").withMine(List.of("reply")));

    Optional<String> step =
        OriginContext.as(ActionOrigin.MASTER, () -> right.repliesStep("ABC-42", config));

    assertThat(step)
        .hasValueSatisfying(text -> assertThat(text).contains("may not answer a person"));
  }

  @Test
  void holdsNothingOfTheHumansOwnShipWhereTheRightWasWithheld() {
    ConfigFile config =
        ConfigFile.defaults()
            .withArbiter(ArbiterConfig.defaults().withMode("act").withMine(List.of("reply")));

    Optional<String> step =
        OriginContext.as(ActionOrigin.BOARD, () -> right.repliesStep("ABC-42", config));

    assertThat(step).isEmpty();
  }

  @Test
  void forbidsTheShipWhereNoCaseLetsTheMasterReply() {
    ConfigFile config = ConfigFile.defaults().withArbiter(ArbiterConfig.defaults().withMode("act"));
    when(door.presses(anyString(), anyString())).thenReturn(false);

    Optional<String> step = right.repliesStep("ABC-42", config);

    assertThat(step).isPresent();
  }

  @Test
  void holdsNothingWhereOneCaseLetsTheMasterReply() {
    ConfigFile config = ConfigFile.defaults().withArbiter(ArbiterConfig.defaults().withMode("act"));
    when(door.presses(anyString(), anyString())).thenReturn(true);

    Optional<String> step = right.repliesStep("ABC-42", config);

    assertThat(step).isEmpty();
  }
}
