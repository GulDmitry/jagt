package dev.jagt.orchestrator.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The projection is what a surface renders, so anything it hands over as a LINK has to be one. Neither URL is
 * jagt's own — the ticket link comes back from a model reading a tracker, the request link from an agent's
 * status message, and `state.json` is hand-editable — while the board turns both into anchors in a page that
 * can POST `deploy` to the local API.
 */
class TaskViewTest {

    private static TaskView viewOf(String ticketUrl, String reviewRequestUrl) {
        return TaskView.of("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .alias("a1").ticketUrl(ticketUrl).mrUrl(reviewRequestUrl).build(), false);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:fetch('/api/tasks/a1/actions/deploy',{method:'POST'})",
            "JavaScript:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "file:///etc/passwd",
            "not a url at all",
    })
    void dropsALinkThatIsNotHttpRatherThanHandingItToAnAnchor(String hostile) {
        TaskView view = viewOf(hostile, hostile);

        assertThat(view.ticketUrl()).isNull();
        assertThat(view.reviewRequestUrl()).isNull();
    }

    @Test
    void keepsRealLinksAndTrimsTheWhitespaceAroundThem() {
        TaskView view = viewOf("  https://tracker.example.com/browse/ABC-1  ",
                "http://host.example.com/x/-/merge_requests/9");

        assertThat(view.ticketUrl()).isEqualTo("https://tracker.example.com/browse/ABC-1");
        assertThat(view.reviewRequestUrl()).isEqualTo("http://host.example.com/x/-/merge_requests/9");
    }

    /** A task with no links is the normal case for a fresh one; the projection must not invent empty strings. */
    @Test
    void leavesBothLinksUnsetWhenTheTaskHasNone() {
        TaskView view = viewOf(null, null);

        assertThat(view.ticketUrl()).isNull();
        assertThat(view.reviewRequestUrl()).isNull();
    }

    /**
     * The DASHBOARD's own detail line is built from the raw state, so a dropped link must not silently turn a
     * CI_POLLING card into a blank one: the fallback text is what tells the human the link is missing.
     */
    @Test
    void stillSaysSomethingWhenTheStoredRequestLinkWasUnusable() {
        assertThat(viewOf(null, "javascript:alert(1)").detail()).isEqualTo("javascript:alert(1)");
    }
    @Test
    void carriesEveryRepositoryWithItsOwnRequestSoNoSurfaceHasToGuessWhichDiffIsWhich() {
        TaskView view = TaskView.of("ABC-1", TaskState.builder(List.of(
                new TaskRepo("api", "/api-wt", "git@host:g/api.git", "https://host/api/-/merge_requests/1", null),
                new TaskRepo("web", "/web-wt", "git@host:g/web.git", "https://host/web/-/merge_requests/2", null)),
                TaskStatus.CI_POLLING).alias("a1").build(), false);

        assertThat(view.repos()).extracting(TaskView.RepoView::project, TaskView.RepoView::reviewRequestUrl)
                .containsExactly(tuple("api", "https://host/api/-/merge_requests/1"),
                        tuple("web", "https://host/web/-/merge_requests/2"));
        assertThat(view.project()).isEqualTo("api");
    }

    @Test
    void offersASweepAsSoonAsANYRepositoryHasARequestOpen() {
        TaskView view = TaskView.of("ABC-1", TaskState.builder(List.of(
                new TaskRepo("api", "/api-wt", "git@host:g/api.git", null, null),
                new TaskRepo("web", "/web-wt", "git@host:g/web.git", "https://host/web/-/merge_requests/2", null)),
                TaskStatus.CI_POLLING).alias("a1").build(), false);

        assertThat(view.actions()).extracting(TaskView.ActionView::id).contains("sweep");
    }
}
