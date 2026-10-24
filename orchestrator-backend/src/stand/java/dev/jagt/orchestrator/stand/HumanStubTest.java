package dev.jagt.orchestrator.stand;

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

    private static final String SHIP_IS_YOURS = """
            {"tasks":[{"id":"ABC-42","owner":"YOU","history":[{},{}],
              "actions":[{"id":"ship","primary":true},{"id":"done","primary":false}]}]}""";

    @Test
    void pressesTheHumansVerbOnceInJudge() {
        StandBoard verbs = mock(StandBoard.class);
        when(verbs.press("ABC-42", "ship")).thenReturn(JsonMapper.builder().build().createObjectNode());
        JsonNode board = JsonMapper.builder().build().readTree(SHIP_IS_YOURS);
        HumanStub human = new HumanStub(StandMode.JUDGE);

        human.take(board, verbs, mock(Timeline.class));
        human.take(board, verbs, mock(Timeline.class));

        verify(verbs, times(1)).press("ABC-42", "ship");
    }

    @Test
    void leavesTheShipToTheMasterInAct() {
        StandBoard verbs = mock(StandBoard.class);
        JsonNode board = JsonMapper.builder().build().readTree(SHIP_IS_YOURS);

        new HumanStub(StandMode.ACT).take(board, verbs, mock(Timeline.class));

        verify(verbs, never()).press(anyString(), anyString());
    }
}
