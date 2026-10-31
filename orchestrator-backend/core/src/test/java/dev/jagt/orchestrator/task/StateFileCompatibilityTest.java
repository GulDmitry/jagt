package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class StateFileCompatibilityTest {

    @Test
    void aStateFileWrittenBeforeRolesLoadsWithQasPassAndTheMastersSeal() {
        String json = """
                {"status":"DEPLOYED","lastActiveTimestamp":5,"mrCreatedAt":0,"lastPolledAt":0,
                 "ticketUrl":"https://tracker.example/ABC-42",
                 "repos":[{"project":"proj","worktreePath":"/wt"}],
                 "turn":{"step":"ship","id":3,"digest":"","since":4,"marks":{"qa:digest":"d1","press:ship":"tree"}},
                 "qa":{"passed":"proj@abc"},
                 "master":{"seal":"abc","sealedRound":3}}
                """;

        TaskState task = new JsonMapper().readValue(json, TaskState.class);

        assertThat(task.roleOrNone("qa").passed()).isEqualTo("proj@abc");
        assertThat(task.masterOrNone()).isEqualTo(new Master("", "", "", "abc", 3));
        assertThat(task.turnOrNone().marks()).containsOnlyKeys("press:ship");
    }
}
