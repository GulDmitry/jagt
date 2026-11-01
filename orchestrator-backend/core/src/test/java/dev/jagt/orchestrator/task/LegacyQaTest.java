package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyQaTest {

    @Test
    void readsTheQaMarksOfAnOldStateFileIntoTheQaRecordAndDropsThemFromTheTurn() {
        String json = """
                {"status":"DEPLOYED","lastActiveTimestamp":5,"mrCreatedAt":0,"lastPolledAt":0,"repos":[{"project":"proj","worktreePath":"/wt"}],
                 "turn":{"step":"ship","id":3,"digest":"","since":4,
                         "marks":{"qa":"proj@abc","qa:digest":"d1","qa:rows":"- row","press:ship":"tree"}}}
                """;

        TaskState task = new JsonMapper().readValue(json, TaskState.class);

        assertThat(task.roleOrNone("qa")).isEqualTo(new RoleRecord("", "proj@abc", "d1", "- row"));
        assertThat(task.turnOrNone().marks()).containsOnlyKeys("press:ship");
    }

    @Test
    void readsAnOpenQaTurnOnADeployedTaskAsARunningRead() {
        String json = """
                {"status":"DEPLOYED","lastActiveTimestamp":5,"mrCreatedAt":0,"lastPolledAt":0,"repos":[{"project":"proj","worktreePath":"/wt"}],
                 "turn":{"step":"accept","id":3,"digest":"d1","since":4}}
                """;

        TaskState task = new JsonMapper().readValue(json, TaskState.class);

        assertThat(task.roleOrNone("qa").reading()).isEqualTo("accept");
    }

    @Test
    void keepsTheQaRecordAStateFileWrittenNowCarries() {
        String json = """
                {"status":"DEPLOYED","lastActiveTimestamp":5,"mrCreatedAt":0,"lastPolledAt":0,"repos":[{"project":"proj","worktreePath":"/wt"}],
                 "qa":{"passed":"proj@abc"}}
                """;

        TaskState task = new JsonMapper().readValue(json, TaskState.class);

        assertThat(task.roleOrNone("qa").passed()).isEqualTo("proj@abc");
    }

    @Test
    void leavesATurnWithoutQaMarksAsItIs() {
        Turn turn = new Turn("ship", 3, "", 4, java.util.Map.of("press:ship", "tree"), java.util.List.of());

        assertThat(LegacyQa.withoutMarks(turn)).isSameAs(turn);
    }

    @Test
    void dropsAnyOneOfTheQaMarksFromATurn() {
        Turn turn = new Turn("ship", 3, "", 4, java.util.Map.of("qa:rows", "- row", "press:ship", "tree"),
                java.util.List.of());

        assertThat(LegacyQa.withoutMarks(turn).marks()).containsOnlyKeys("press:ship");
    }
}
