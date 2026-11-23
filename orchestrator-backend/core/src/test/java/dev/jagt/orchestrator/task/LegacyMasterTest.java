package dev.jagt.orchestrator.task;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.master.Master;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class LegacyMasterTest {

  @Test
  void readsTheMasterMarksOfAnOldStateFileIntoTheMasterRecordAndDropsThemFromTheTurn() {
    String json =
        """
        {"status":"REVIEWED","lastActiveTimestamp":5,"mrCreatedAt":0,"lastPolledAt":0,
         "repos":[{"project":"proj","worktreePath":"/wt"}],
         "turn":{"step":"ship","id":3,"digest":"","since":4,
                 "marks":{"left":"deploy x","unanswered":"u1","press:deploy":"5",
                          "master-review:seal":"abc","press:ship":"tree"}}}
        """;

    TaskState task = new JsonMapper().readValue(json, TaskState.class);

    assertThat(task.work().arbiterOrNone())
        .isEqualTo(new Master("deploy x", "u1", "5", "abc", 0, "", 0));
    assertThat(task.work().turnOrNone().marks()).containsOnlyKeys("press:ship");
  }

  @Test
  void keepsTheMasterRecordStateFileWrittenNowCarries() {
    String json =
        """
        {"status":"REVIEWED","lastActiveTimestamp":5,"mrCreatedAt":0,"lastPolledAt":0,
         "repos":[{"project":"proj","worktreePath":"/wt"}],
         "master":{"left":"deploy x","sealedRound":4}}
        """;

    TaskState task = new JsonMapper().readValue(json, TaskState.class);

    assertThat(task.work().arbiterOrNone()).isEqualTo(new Master("deploy x", "", "", "", 4, "", 0));
  }
}
