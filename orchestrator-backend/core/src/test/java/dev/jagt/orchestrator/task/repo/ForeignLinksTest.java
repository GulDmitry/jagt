package dev.jagt.orchestrator.task.repo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.LinkedHashMap;
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
  void takesLinkOnThePrimaryRepositoryForForeignWhenItsRemoteDoesNotHoldIt() {
    ForeignLinks.Judged judged =
        ForeignLinks.judge(task, Map.of(), "https://host/other/proj/-/merge_requests/9");

    assertThat(judged.refusedAll()).isTrue();
    assertThat(judged.foreign())
        .extracting(ForeignLinks.Foreign::remote)
        .containsExactly("git@host:team/proj.git");
  }

  @Test
  void judgesNothingOfRepositoryWhoseRemoteIsUnknown() {
    ForeignLinks.Judged judged =
        ForeignLinks.judge(
            task,
            Map.of("web", "https://host/anything/-/merge_requests/1"),
            "https://host/anything/-/merge_requests/1");

    assertThat(judged.foreign()).isEmpty();
    assertThat(judged.byProject()).containsKey("web");
  }

  @Test
  void judgesNothingOfRepositoryWhoseRemoteIsLocalBareRepository() {
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

  @Test
  void staysOnTheFirstKeptRequestInTheTasksRepositoryOrderWhenAnotherIsForeign() {
    TaskState spanning =
        TaskState.builder(
                List.of(
                    new TaskRepo("proj", "/wt", "git@host:team/proj.git", null),
                    new TaskRepo("api", "/wt-api", null, null),
                    new TaskRepo("db", "/wt-db", null, null),
                    new TaskRepo("web", "/wt-web", null, null)),
                TaskStatus.IN_PROGRESS)
            .build();
    Map<String, String> asked = new LinkedHashMap<>();
    asked.put("web", "https://host/web/-/merge_requests/3");
    asked.put("db", "https://host/db/-/merge_requests/2");
    asked.put("proj", "https://host/other/proj/-/merge_requests/9");
    asked.put("api", "https://host/api/-/merge_requests/1");

    ForeignLinks.Judged judged = ForeignLinks.judge(spanning, asked, null);

    assertThat(judged.url()).isEqualTo("https://host/api/-/merge_requests/1");
  }

  @Test
  void staysOnTheReportsFirstKeptRequestWhenNoneIsOnProjectOfTheTask() {
    Map<String, String> asked = new LinkedHashMap<>();
    asked.put("docs", "https://host/docs/-/merge_requests/4");
    asked.put("proj", "https://host/other/proj/-/merge_requests/9");
    asked.put("site", "https://host/site/-/merge_requests/5");

    ForeignLinks.Judged judged = ForeignLinks.judge(task, asked, null);

    assertThat(judged.url()).isEqualTo("https://host/docs/-/merge_requests/4");
  }
}
