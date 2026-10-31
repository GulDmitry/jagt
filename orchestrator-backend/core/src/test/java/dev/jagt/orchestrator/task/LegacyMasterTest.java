package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyMasterTest {

    @Test
    void readsTheMasterMarksOfAnOldStateFileIntoTheMasterRecordAndDropsThemFromTheTurn() {
        String json = """
                {"status":"REVIEWED","lastActiveTimestamp":5,"mrCreatedAt":0,"lastPolledAt":0,
                 "repos":[{"project":"proj","worktreePath":"/wt"}],
                 "turn":{"step":"ship","id":3,"digest":"","since":4,
                         "marks":{"left":"deploy x","unanswered":"u1","press:deploy":"5",
                                  "master-review:seal":"abc","press:ship":"tree"}}}
                """;

        TaskState task = new JsonMapper().readValue(json, TaskState.class);

        assertThat(task.masterOrNone()).isEqualTo(new Master("deploy x", "u1", "5", "abc", 0));
        assertThat(task.turnOrNone().marks()).containsOnlyKeys("press:ship");
    }

    @Test
    void keepsTheMasterRecordAStateFileWrittenNowCarries() {
        String json = """
                {"status":"REVIEWED","lastActiveTimestamp":5,"mrCreatedAt":0,"lastPolledAt":0,
                 "repos":[{"project":"proj","worktreePath":"/wt"}],
                 "master":{"left":"deploy x","sealedRound":4}}
                """;

        TaskState task = new JsonMapper().readValue(json, TaskState.class);

        assertThat(task.masterOrNone()).isEqualTo(new Master("deploy x", "", "", "", 4));
    }
}
