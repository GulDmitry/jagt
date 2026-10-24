package dev.jagt.orchestrator.adapter.replay;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class TurnScriptTest {

    @Test
    void answersTheLaunchAndEachOpenedTurnWithTheMovesRecordedThere() {
        RecordingLine.Wrote notes = new RecordingLine.Wrote("ABC-1", 0, Map.of("task_notes.md", "done"));
        RecordingLine.Pushed push = new RecordingLine.Pushed("ABC-1", 1, "Fix the widget", List.of("widget.txt"));
        TurnScript script = TurnScript.of("ABC-1", List.of(notes, new RecordingLine.Opened("ABC-1", 1, "ship"), push,
                new RecordingLine.Opened("ABC-1", 2, "deploy")));

        List<RecordingLine.Move> launched = script.launched();
        TurnScript.Reply ship = script.opened("ship");
        TurnScript.Reply deploy = script.opened("deploy");

        assertThat(launched).containsExactly(notes);
        assertThat(ship.moves()).containsExactly(push);
        assertThat(deploy.moves()).isEmpty();
        assertThat(deploy.diverged()).isFalse();
        assertThat(script.drained()).isTrue();
    }

    @Test
    void divergesNamingTheRecordedStepWhenAnotherTurnOpens() {
        TurnScript script = TurnScript.of("ABC-1", List.of(new RecordingLine.Opened("ABC-1", 1, "ship")));

        TurnScript.Reply reply = script.opened("verify");

        assertThat(reply.divergence()).isEqualTo("recorded turn at ship never opened, the turn opened at verify");
    }

    @Test
    void divergesNamingTheStepOfATurnOpenedPastTheRecording() {
        TurnScript script = TurnScript.of("ABC-1", List.of(new RecordingLine.Called("ABC-1", 0, "update_agent_status",
                JsonMapper.builder().build().createObjectNode())));
        script.launched();

        TurnScript.Reply reply = script.opened("deploy");

        assertThat(reply.divergence()).isEqualTo("turn opened at deploy with no recorded move");
    }
}
