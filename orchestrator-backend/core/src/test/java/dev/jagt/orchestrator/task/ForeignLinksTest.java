package dev.jagt.orchestrator.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ForeignLinksTest {

  private final TaskState task =
      TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
          .repos(
              List.of(
                  new TaskRepo("proj", "/wt", "git@host:team/proj.git", null),
                  new TaskRepo("web", "/wt-web", null, null)))
          .build();

  @Test
  void takesALinkOnThePrimaryRepositoryForForeignWhenItsRemoteDoesNotHoldIt() {
    ForeignLinks.Judged judged =
        ForeignLinks.judge(task, Map.of(), "https://host/other/proj/-/merge_requests/9");

    assertThat(judged.refusedAll()).isTrue();
    assertThat(judged.foreign())
        .extracting(ForeignLinks.Foreign::remote)
        .containsExactly("git@host:team/proj.git");
  }

  @Test
  void judgesNothingOfARepositoryWhoseRemoteIsUnknown() {
    ForeignLinks.Judged judged =
        ForeignLinks.judge(
            task,
            Map.of("web", "https://host/anything/-/merge_requests/1"),
            "https://host/anything/-/merge_requests/1");

    assertThat(judged.foreign()).isEmpty();
    assertThat(judged.byProject()).containsKey("web");
  }

  @Test
  void judgesNothingOfARepositoryWhoseRemoteIsALocalBareRepository() {
    TaskState local =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .repos(List.of(new TaskRepo("proj", "/wt", "file:///srv/git/origin.git", null)))
            .build();

    ForeignLinks.Judged judged =
        ForeignLinks.judge(local, Map.of(), "http://127.0.0.1:1/proj/merge_requests/1");

    assertThat(judged.foreign()).isEmpty();
  }

  @Test
  void clearsOnlyTheRequestsTheirRemotesDoNotHold() {
    TaskState linked =
        task.withMrUrl("proj", "https://host/other/proj/-/merge_requests/9", 1L)
            .withMrUrl("web", "https://host/web/-/merge_requests/3", 1L);

    TaskState cleared = ForeignLinks.cleared(linked);

    assertThat(cleared.reviewRequestOf("proj")).isEmpty();
    assertThat(cleared.reviewRequestOf("web")).isPresent();
  }
}
