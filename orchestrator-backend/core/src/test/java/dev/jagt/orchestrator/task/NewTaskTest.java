package dev.jagt.orchestrator.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class NewTaskTest {

  @Test
  void keepsTheSessionsOwnProjectFirstAndAddsTheOthersAfterIt() {
    NewTask task = NewTask.builder("ABC-42", "api").alsoIn(List.of("web", "docs")).build();

    assertThat(task.projectKeys()).containsExactly("api", "web", "docs");
    assertThat(task.projectKey()).isEqualTo("api");
  }

  @Test
  void dropsBlankOrMissingFurtherProjectInsteadOfPromotingIt() {
    NewTask task = NewTask.builder("ABC-42", "api").alsoIn(Arrays.asList("web", " ", null)).build();

    assertThat(task.projectKeys()).containsExactly("api", "web");
  }

  @Test
  void cutsOneWorktreeForRepositoryNamedTwice() {
    NewTask task = NewTask.builder("ABC-42", "api").alsoIn(List.of("web", "api", "web")).build();

    assertThat(task.projectKeys()).containsExactly("api", "web");
  }

  @Test
  void refusesTaskWhoseSessionProjectIsBlank() {
    assertThatThrownBy(() -> NewTask.builder("ABC-42", " ").alsoIn(List.of("web")).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("A task needs the project its session runs in");
  }

  @Test
  void carriesEveryOptionTheCallerNamed() {
    NewTask task =
        NewTask.builder("ABC-42", "api")
            .instructions("do it")
            .mode("plan")
            .branchStrategy("resume")
            .baseBranch("release")
            .title("Widget layout is off")
            .ticketUrl("https://tracker/ABC-42")
            .build();

    assertThat(task)
        .isEqualTo(
            new NewTask(
                "ABC-42",
                List.of("api"),
                "do it",
                "plan",
                "resume",
                "release",
                "Widget layout is off",
                "https://tracker/ABC-42"));
  }

  @Test
  void namesNoProjectWhenItWasBuiltWithoutAny() {
    NewTask task = new NewTask("ABC-42", List.of(), null, null, null, null, null, null);

    assertThat(task.projectKey()).isNull();
  }
}
