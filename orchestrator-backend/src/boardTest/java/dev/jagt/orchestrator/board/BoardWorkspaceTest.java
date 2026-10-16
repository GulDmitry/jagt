package dev.jagt.orchestrator.board;

import com.microsoft.playwright.Page;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BoardWorkspaceTest extends BoardPageContext {

    @Test
    void aTaskWaitingOnTheHumanStandsFirstInItsOwnBand() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                TaskStatus.CI_FAILED).alias("a2").lastActiveTimestamp(System.currentTimeMillis()).build());
        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        assertThat(page.locator("#tasks .row .alias")).hasText(new String[]{"a2", "a1"});
        assertThat(page.locator("#tasks .group.yours .alias")).hasText(new String[]{"a2"});
        assertThat(page.locator("#pane article .alias")).hasText("a2");
    }

    @Test
    void pickingARowOpensThatTaskInThePane() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a2").lastActiveTimestamp(System.currentTimeMillis()).build());
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        page.locator("#tasks .row:has-text(\"a2\") .pick").click();

        assertThat(page.locator("#pane article .alias")).hasText("a2");
        assertThat(page.locator("#tasks .row.selected .alias")).hasText("a2");
    }

    @Test
    void nOpensTheNextTaskWaitingOnTheHuman() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.CI_FAILED).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a2").lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-3", TaskState.builder("alpha", root.resolve("ABC-3-alpha").toString(),
                TaskStatus.CI_FAILED).alias("a3").lastActiveTimestamp(System.currentTimeMillis()).build());
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#pane article .alias")).hasText("a1");

        page.keyboard().press("n");

        assertThat(page.locator("#pane article .alias")).hasText("a3");
    }

    @Test
    void theRowsOwnFocusActsOnItsTaskWithoutOpeningIt() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.CI_FAILED).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a2").lastActiveTimestamp(System.currentTimeMillis()).build());
        when(commands.execute("ABC-2", TaskAction.FOCUS)).thenReturn("Focused ABC-2.");
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        page.getByLabel("Focus a2", new Page.GetByLabelOptions().setExact(true)).click();

        assertThat(page.locator("#toasts .toast")).hasText("Focused ABC-2.");
        verify(commands).execute("ABC-2", TaskAction.FOCUS);
        assertThat(page.locator("#pane article .alias")).hasText("a1");
    }

    @Test
    void theTabsTitleCountsTheTasksWaitingOnTheHuman() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.CI_FAILED).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a2").lastActiveTimestamp(System.currentTimeMillis()).build());
        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page).hasTitle("(1) jagt");
    }

    @Test
    void aRowWhoseStatusMovedWhileItWasClosedIsBoldUntilItIsOpened() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.CI_FAILED).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a2").lastActiveTimestamp(System.currentTimeMillis()).build());
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#tasks .row")).hasCount(2);

        state.putTask("ABC-2", state.task("ABC-2").orElseThrow().withStatus(TaskStatus.REVIEW_PENDING, "done"));

        assertThat(page.locator("#tasks .row-title.changed")).hasCount(1);
        page.locator("#tasks .row:has-text(\"a2\") .pick").click();
        assertThat(page.locator("#tasks .row-title.changed")).hasCount(0);
    }

    @Test
    void aRowThatMovedWhileThePageWasClosedIsBoldWhenItOpensAgain() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.CI_FAILED).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a2").lastActiveTimestamp(System.currentTimeMillis()).build());
        Page first = session.newPage();
        first.navigate("http://localhost:" + port + "/");
        assertThat(first.locator("#tasks .row")).hasCount(2);
        first.close();

        state.putTask("ABC-2", state.task("ABC-2").orElseThrow().withStatus(TaskStatus.REVIEW_PENDING, "done"));
        Page again = session.newPage();
        again.navigate("http://localhost:" + port + "/");

        assertThat(again.locator("#tasks .row-title.changed")).hasCount(1);
    }

    @Test
    void aPollThatStoppedRingsTheRowOfATaskThatIsNotOpen() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .mrCreatedAt(System.currentTimeMillis() - java.time.Duration.ofDays(9).toMillis())
                .pipelineStatus("failed")
                .lastPolledAt(System.currentTimeMillis()).lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a2").lastActiveTimestamp(System.currentTimeMillis()).build());
        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        page.locator("#tasks .row:has-text(\"a2\") .pick").click();
        assertThat(page.locator("#pane article .alias")).hasText("a2");
        assertThat(page.locator("#tasks .row:has-text(\"a1\") .status.stalled")).hasCount(1);
    }

    @Test
    void aRowWaitingOnTheHumanNamesItsProjectWhereSeveralShareTheBand() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.CI_FAILED).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-2", TaskState.builder("beta", root.resolve("ABC-2-beta").toString(),
                TaskStatus.IN_PROGRESS).alias("b2").lastActiveTimestamp(System.currentTimeMillis()).build());
        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#tasks .group.yours .row-facts")).containsText("alpha");
        assertThat(page.locator("#tasks .group.project .row-facts")).not().containsText("beta");
    }

    @Test
    void theOpenTaskStaysInViewWhileTheListScrolls() {
        for (int i = 1; i <= 16; i++) {
            state.putTask("ABC-" + i, TaskState.builder("alpha", root.resolve("ABC-" + i + "-alpha").toString(),
                    TaskStatus.IN_PROGRESS).alias("a" + i).lastActiveTimestamp(System.currentTimeMillis()).build());
        }
        Page page = session.newPage();
        page.setViewportSize(1280, 600);
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#tasks .row")).hasCount(16);

        page.locator("#tasks .row").last().scrollIntoViewIfNeeded();

        assertThat(page.locator("#pane article")).isInViewport();
    }

    @Test
    void aNarrowWindowShowsTheListOrTheOpenTaskAndGoesBack() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        Page page = session.newPage();
        page.setViewportSize(800, 900);
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#pane")).isHidden();

        page.locator("#tasks .row .pick").click();
        assertThat(page.locator("#tasks")).isHidden();
        page.locator("#pane .back").click();

        assertThat(page.locator("#tasks")).isVisible();
        assertThat(page.locator("#pane")).isHidden();
    }
}
