package dev.jagt.orchestrator.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class LegacyRolesTest {

  private static final Deployment LIVE =
      new Deployment(
          1,
          List.of(Deployment.Target.of("proj", "dev").withMerge("0123456789abcdef")),
          1,
          "digest");

  @Test
  void aStateFileWrittenBeforeRolesKeepsTheQaRolesPassOnTheDeploy() {
    String json =
        """
        {"status":"DEPLOYED","lastActiveTimestamp":5,"mrCreatedAt":0,"lastPolledAt":0,
         "repos":[{"project":"proj","worktreePath":"/wt"}],
         "qa":{"passed":"proj@0123456789abcdef"}}
        """;

    TaskState task = new JsonMapper().readValue(json, TaskState.class).withDeployment(LIVE);

    assertThat(task.passedBy("acceptance")).isTrue();
    JsonNode written = new JsonMapper().readTree(new JsonMapper().writeValueAsString(task));
    assertThat(written.has("qa")).isFalse();
    assertThat(written.path("roles").has("qa")).isFalse();
    assertThat(written.path("roles").has("acceptance")).isTrue();
  }

  @Test
  void aRolesRecordKeyedQaLoadsAsTheAcceptanceRecord() {
    String json =
        """
        {"status":"DEPLOYED","lastActiveTimestamp":5,"mrCreatedAt":0,"lastPolledAt":0,
         "repos":[{"project":"proj","worktreePath":"/wt"}],
         "roles":{"qa":{"passed":"proj@abc"}}}
        """;

    TaskState task = new JsonMapper().readValue(json, TaskState.class);

    assertThat(task.roleOrNone("acceptance").passed()).isEqualTo("proj@abc");
    assertThat(task.roles()).doesNotContainKey("qa");
  }

  @Test
  void aTaskCarryingBothKeepsTheRolesRecord() {
    String json =
        """
        {"status":"DEPLOYED","lastActiveTimestamp":5,"mrCreatedAt":0,"lastPolledAt":0,
         "repos":[{"project":"proj","worktreePath":"/wt"}],
         "qa":{"passed":"proj@old"},
         "roles":{"qa":{"passed":"proj@new"}}}
        """;

    TaskState task = new JsonMapper().readValue(json, TaskState.class);

    assertThat(task.roleOrNone("acceptance").passed()).isEqualTo("proj@new");
  }

  @Test
  void aStateFileWithANullRoleRecordLoadsWithoutIt() {
    String json =
        """
        {"status":"DEPLOYED","lastActiveTimestamp":5,"mrCreatedAt":0,"lastPolledAt":0,
         "repos":[{"project":"proj","worktreePath":"/wt"}],
         "roles":{"qa":null}}
        """;

    TaskState task = new JsonMapper().readValue(json, TaskState.class);

    assertThat(task.roles()).isEmpty();
  }
}
