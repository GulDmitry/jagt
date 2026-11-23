package dev.jagt.orchestrator.task;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.deploy.ChecksUnread;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class TaskStateChecksTest {

  @Test
  void keepsWhatTheHostSaidAboutTheChecksWhenTheTaskGoesOnMoving() {
    TaskState red =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .build()
            .withChecksRead("failed", null);

    TaskState reported = red.withStatus(TaskStatus.CI_FAILED, "build broken", 2_000);

    assertThat(reported.checks().pipelineStatus()).isEqualTo("failed");
  }

  @Test
  void keepsTheChecksFlaggedUnreadInFileWrittenBeforeTheReadsWereKept() {
    TaskState legacy =
        new JsonMapper()
            .readValue(
                """
                {"project":"demo","worktreePath":"/wt","status":"CI_POLLING","lastActiveTimestamp":5,
                 "mrCreatedAt":0,"lastPolledAt":0,"pipelineStatus":"failed","pipelineUnread":true}\
                """,
                TaskState.class);

    assertThat(legacy.checks().unread()).isTrue();
  }

  @Test
  void keepsOnlyTheReadsRunningThatCanStopTheTask() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .build()
            .withChecksUnread(new ChecksUnread(1_000, "a"))
            .withChecksUnread(new ChecksUnread(2_000, "b"))
            .withChecksUnread(new ChecksUnread(3_000, "c"))
            .withChecksUnread(new ChecksUnread(4_000, "d"));

    assertThat(task.checks().checksUnread())
        .containsExactly(
            new ChecksUnread(2_000, "b"),
            new ChecksUnread(3_000, "c"),
            new ChecksUnread(4_000, "d"));
  }

  @Test
  void forgetsTheReadsRunningThatCouldNotListTheChecksOnceOneDid() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .build()
            .withChecksUnread(new ChecksUnread(1_000, "list_pipelines: 403 Forbidden"));

    TaskState read = task.withChecksRead("success", null);

    assertThat(read.checks().checksUnread()).isEmpty();
  }

  @Test
  void reportsTheChecksUnreadOnlyWhileReadRunningCouldNotListThem() {
    TaskState clear = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState unread =
        TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS)
            .checksUnread(List.of(new ChecksUnread(1, "403")))
            .build();

    assertThat(clear.checks().unread()).isFalse();
    assertThat(unread.checks().unread()).isTrue();
  }

  @Test
  void readsChecksAndApprovalAsUnknownWhenTheHeadMovedOffTheCommitTheyWereReadOn() {
    TaskState read =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .build()
            .withHead("aaa")
            .withChecksRead("failed", "aaa")
            .withApproved(true);

    TaskState pushed = read.withHead("bbb");

    assertThat(pushed.checks().read()).isNull();
    assertThat(pushed.approvedRead()).isNull();
  }

  @Test
  void keepsChecksWhenTheHeadIsStillTheCommitTheyWereReadOn() {
    TaskState read =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .build()
            .withHead("aaa")
            .withChecksRead("failed", "aaa")
            .withApproved(true);

    assertThat(read.withHead("aaa").checks().read()).isEqualTo("failed");
    assertThat(read.withHead("aaa").approvedRead()).isTrue();
  }

  @Test
  void readsChecksAsUnknownWhenStoredReadNamesNoCommitOnceHeadIsKnown() {
    TaskState legacy =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).pipelineStatus("failed").build();

    assertThat(legacy.withHead("aaa").checks().read()).isNull();
  }
}
