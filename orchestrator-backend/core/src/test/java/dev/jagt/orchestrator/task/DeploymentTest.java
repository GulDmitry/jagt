package dev.jagt.orchestrator.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class DeploymentTest {

  @Test
  void findsNoTargetForAProjectNoTargetHas() {
    Deployment deployment = new Deployment(0, List.of(Deployment.Target.of("api", "dev")), 0, null);

    assertThat(deployment.indexOf("web")).isEqualTo(-1);
  }

  @Test
  void takesAFixAsTheMergeWhereTheTargetRecordedNone() {
    Deployment.Target fixed = Deployment.Target.of("api", "dev").withFix("f1");

    assertThat(fixed.landing().merge()).isEqualTo("f1");
    assertThat(fixed.landing().fixes()).isEmpty();
  }

  @Test
  void isConflictedOnlyWhereAConflictIsRecorded() {
    Deployment.Target clean = Deployment.Target.of("api", "dev");

    assertThat(clean.conflicted()).isFalse();
    assertThat(clean.withConflict(Conflict.of(Deployment.Cause.MERGE, 1)).conflicted()).isTrue();
  }

  @Test
  void owesAReRunOfThePipelineOfAFixPushedAfterTheOneReRun() {
    Deployment.Target retried =
        Deployment.Target.of("api", "dev").withMerge("m1").withRetried("m1");

    assertThat(retried.retriedTip()).isTrue();
    assertThat(retried.withFix("f1").retriedTip()).isFalse();
  }

  @Test
  void readsAndWritesATargetAsOneFlatObject() {
    String json =
        "{\"project\":\"api\",\"deployBranch\":\"dev\",\"merge\":\"m1\",\"fixes\":[\"f1\"],"
            + "\"pushedAt\":7,\"revertCommit\":\"r1\",\"blocked\":true,\"bare\":true,\"unread\":2,\"retried\":\"f1\"}";
    JsonMapper mapper = new JsonMapper();

    Deployment.Target target = mapper.readValue(json, Deployment.Target.class);

    assertThat(target.landing().merge()).isEqualTo("m1");
    assertThat(target.landing().fixes()).containsExactly("f1");
    assertThat(target.landing().pushedAt()).isEqualTo(7);
    assertThat(target.revertCommit()).isEqualTo("r1");
    assertThat(target.isBlocked()).isTrue();
    assertThat(target.unreadReads()).isEqualTo(2);
    assertThat(mapper.readTree(mapper.writeValueAsString(target))).isEqualTo(mapper.readTree(json));
  }

  @Test
  void readsAConflictedTargetFromItsFlatConflictedKey() {
    Deployment.Target held =
        Deployment.Target.of("api", "dev").withConflict(Conflict.of(Deployment.Cause.MERGE, 1));
    JsonMapper mapper = new JsonMapper();

    Deployment.Target read =
        mapper.readValue(mapper.writeValueAsString(held), Deployment.Target.class);

    assertThat(mapper.readTree(mapper.writeValueAsString(held)).has("conflicted")).isTrue();
    assertThat(read.progress().conflict()).isEqualTo(held.progress().conflict());
  }
}
