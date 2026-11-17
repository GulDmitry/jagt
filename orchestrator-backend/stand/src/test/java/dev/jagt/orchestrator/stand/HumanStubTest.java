package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class HumanStubTest {

  private static final String SHIP_IS_YOURS =
      """
      {"tasks":[{"id":"ABC-42","owner":"YOU","history":[{},{}],
        "actions":[{"id":"ship","primary":true},{"id":"done","primary":false}]}]}\
      """;

  private final StandBoard verbs = mock(StandBoard.class);
  private final JsonNode shipIsYours = JsonMapper.builder().build().readTree(SHIP_IS_YOURS);

  @Test
  void pressesTheHumansVerbOnceInJudge() {
    when(verbs.press("ABC-42", "ship")).thenReturn(JsonMapper.builder().build().createObjectNode());
    HumanStub human = new HumanStub(StandMode.JUDGE);

    human.take(shipIsYours, verbs, mock(Timeline.class));
    human.take(shipIsYours, verbs, mock(Timeline.class));

    verify(verbs, times(1)).press("ABC-42", "ship");
  }

  @Test
  void leavesTheShipToTheMasterInAct() {
    new HumanStub(StandMode.ACT).take(shipIsYours, verbs, mock(Timeline.class));

    verify(verbs, never()).press(anyString(), anyString());
  }

  @Test
  void approvesPlanWaitingOnAnotherOwnerInAct() {
    when(verbs.press("ABC-42", "approve-plan"))
        .thenReturn(JsonMapper.builder().build().createObjectNode());
    JsonNode board =
        JsonMapper.builder()
            .build()
            .readTree(
                """
                {"tasks":[{"id":"ABC-42","owner":"MASTER","status":"PLAN_PENDING","history":[{}],
                  "actions":[{"id":"approve-plan","primary":false}]}]}\
                """);

    new HumanStub(StandMode.ACT).take(board, verbs, mock(Timeline.class));

    verify(verbs).press("ABC-42", "approve-plan");
  }

  @Test
  void deploysGreenRequestOwnedByCiInJudge() {
    when(verbs.press("ABC-42", "deploy"))
        .thenReturn(JsonMapper.builder().build().createObjectNode());
    JsonNode board =
        JsonMapper.builder()
            .build()
            .readTree(
                """
                {"tasks":[{"id":"ABC-42","owner":"CI","status":"REVIEWED","pipeline":"GREEN","history":[{}],
                  "actions":[{"id":"deploy","primary":false}]}]}\
                """);

    new HumanStub(StandMode.JUDGE).take(board, verbs, mock(Timeline.class));

    verify(verbs).press("ABC-42", "deploy");
  }

  @Test
  void doesNotDeployRequestWhosePipelineIsStillRunning() {
    JsonNode board =
        JsonMapper.builder()
            .build()
            .readTree(
                """
                {"tasks":[{"id":"ABC-42","owner":"CI","status":"REVIEWED","pipeline":"RUNNING","history":[{}],
                  "actions":[{"id":"deploy","primary":false}]}]}\
                """);

    new HumanStub(StandMode.JUDGE).take(board, verbs, mock(Timeline.class));

    verify(verbs, never()).press(anyString(), anyString());
  }

  @Test
  void leavesTheDeployToTheMasterInAct() {
    JsonNode board =
        JsonMapper.builder()
            .build()
            .readTree(
                """
                {"tasks":[{"id":"ABC-42","owner":"MASTER","status":"APPROVED","pipeline":"GREEN","history":[{}],
                  "actions":[{"id":"deploy","primary":false}]}]}\
                """);

    new HumanStub(StandMode.ACT).take(board, verbs, mock(Timeline.class));

    verify(verbs, never()).press(anyString(), anyString());
  }

  @Test
  void hasNothingToPressOnDeployConflictOwnedByYou() {
    JsonNode board =
        JsonMapper.builder()
            .build()
            .readTree(
                """
                {"tasks":[{"id":"ABC-42","owner":"YOU","status":"DEPLOY_CONFLICT","history":[{},{}],
                  "actions":[{"id":"resolve","primary":false},{"id":"done","primary":false}]}]}\
                """);

    boolean nothing = new HumanStub(StandMode.JUDGE).nothingToPress(board);

    assertThat(nothing).isTrue();
  }

  @Test
  void stillHasShipToPressWhenYouOwnsAnUnpressedOne() {

    boolean nothing = new HumanStub(StandMode.JUDGE).nothingToPress(shipIsYours);

    assertThat(nothing).isFalse();
  }

  @Test
  void hasNothingLeftToPressOnceTheShipWasPressed() {
    when(verbs.press("ABC-42", "ship")).thenReturn(JsonMapper.builder().build().createObjectNode());
    HumanStub human = new HumanStub(StandMode.JUDGE);
    human.take(shipIsYours, verbs, mock(Timeline.class));

    boolean nothing = human.nothingToPress(shipIsYours);

    assertThat(nothing).isTrue();
  }
}
