package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ForeignLinksTest {

    private final TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .repos(List.of(new TaskRepo("proj", "/wt", "git@host:team/proj.git", null),
                    new TaskRepo("web", "/wt-web", null, null))).build();

    @Test
    void takesALinkOnThePrimaryRepositoryForForeignWhenItsRemoteDoesNotHoldIt() {
        ForeignLinks.Judged judged = ForeignLinks.judge(task, Map.of(), "https://host/other/proj/-/merge_requests/9");

        assertThat(judged.refusedAll()).isTrue();
        assertThat(judged.foreign()).extracting(ForeignLinks.Foreign::remote).containsExactly("git@host:team/proj.git");
    }

    @Test
    void judgesNothingOfARepositoryWhoseRemoteIsUnknown() {
        ForeignLinks.Judged judged = ForeignLinks.judge(task, Map.of("web", "https://host/anything/-/merge_requests/1"),
                "https://host/anything/-/merge_requests/1");

        assertThat(judged.foreign()).isEmpty();
        assertThat(judged.byProject()).containsKey("web");
    }

    @Test
    void clearsOnlyTheRequestsTheirRemotesDoNotHold() {
        TaskState linked = task.withMrUrl("proj", "https://host/other/proj/-/merge_requests/9", 1L)
                .withMrUrl("web", "https://host/web/-/merge_requests/3", 1L);

        TaskState cleared = ForeignLinks.cleared(linked);

        assertThat(cleared.reviewRequestOf("proj")).isEmpty();
        assertThat(cleared.reviewRequestOf("web")).isPresent();
    }
}
