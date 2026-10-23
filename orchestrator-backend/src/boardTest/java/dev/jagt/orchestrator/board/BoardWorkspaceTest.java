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

        assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));
        assertThat(page.locator("#tasks .row .alias")).hasText(new String[]{"a2", "a1"});
        assertThat(page.locator("#tasks .group.yours .alias")).hasText(new String[]{"a2"});
        assertThat(page.locator("#pane article .alias")).hasText("a2");
    }

    @Test
    void groupsTheTasksByProjectNameWhateverTaskCameFirst() {
        state.putTask("ABC-1", TaskState.builder("beta", root.resolve("ABC-1-beta").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a2").lastActiveTimestamp(System.currentTimeMillis()).build());
        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));
        assertThat(page.locator("#tasks .group.project .alias")).hasText(new String[]{"a2", "a1"});
    }

    @Test
    void pickingARowOpensThatTaskInThePane() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a2").lastActiveTimestamp(System.currentTimeMillis()).build());
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

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
        assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

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

        state.putTask("ABC-2", state.task("ABC-2").orElseThrow().withStatus(TaskStatus.REVIEW_PENDING, "done", System.currentTimeMillis()));

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

        state.putTask("ABC-2", state.task("ABC-2").orElseThrow().withStatus(TaskStatus.REVIEW_PENDING, "done", System.currentTimeMillis()));
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
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a2").lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-3", TaskState.builder("alpha", root.resolve("ABC-3-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a3").lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-4", TaskState.builder("alpha", root.resolve("ABC-4-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a4").lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-5", TaskState.builder("alpha", root.resolve("ABC-5-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a5").lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-6", TaskState.builder("alpha", root.resolve("ABC-6-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a6").lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-7", TaskState.builder("alpha", root.resolve("ABC-7-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a7").lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-8", TaskState.builder("alpha", root.resolve("ABC-8-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a8").lastActiveTimestamp(System.currentTimeMillis()).build());
        Page page = session.newPage();
        page.setViewportSize(1280, 360);
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#tasks .row")).hasCount(8);

        page.locator("#tasks .row").last().scrollIntoViewIfNeeded();

        assertThat(page.locator("#pane article")).isInViewport(
                new com.microsoft.playwright.assertions.LocatorAssertions.IsInViewportOptions().setRatio(1));
    }

    @Test
    void tabReachesTheOpenTaskPastOneRowWhileTheKeysWalkTheRest() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a2").lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-3", TaskState.builder("alpha", root.resolve("ABC-3-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a3").lastActiveTimestamp(System.currentTimeMillis()).build());
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#tasks .row")).hasCount(3);
        page.locator("#tasks .row.selected .pick").focus();

        page.keyboard().press("j");
        assertThat(page.locator("#tasks .row:has-text(\"a2\") .pick")).isFocused();
        page.keyboard().press("Tab");
        page.keyboard().press("Tab");

        assertThat(page.locator("#pane button.flag")).isFocused();
    }

    @Test
    void aLinkedTaskStaysOpenOnceItsFilterIsCleared() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.CI_FAILED).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a2").lastActiveTimestamp(System.currentTimeMillis()).build());
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/?task=a2");
        assertThat(page.locator("#tasks .row")).hasCount(1);

        page.locator("#filter").press("Escape");

        assertThat(page.locator("#tasks .row")).hasCount(2);
        assertThat(page.locator("#pane article .alias")).hasText("a2");
    }

    @Test
    void anEmptyBoardLeavesNoGapAboveItsWords() {
        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#empty")).isVisible();
        org.assertj.core.api.Assertions.assertThat(page.locator("#board").boundingBox().height).isLessThan(1);
    }

    @Test
    void aTaskANarrowWindowNeverOpenedTurnsBoldWhenItMoves() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.CI_FAILED).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        Page page = session.newPage();
        page.setViewportSize(800, 900);
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#tasks .row")).hasCount(1);

        state.putTask("ABC-1", state.task("ABC-1").orElseThrow().withStatus(TaskStatus.IN_PROGRESS, "back to work", System.currentTimeMillis()));

        assertThat(page.locator("#tasks .row-title.changed")).hasCount(1);
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

    @Test
    void escapeClosesThePaletteButLeavesTheOpenTaskOnANarrowWindow() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        Page page = session.newPage();
        page.setViewportSize(800, 900);
        page.navigate("http://localhost:" + port + "/");
        page.locator("#tasks .row .pick").click();
        page.keyboard().press("Control+k");
        assertThat(page.locator("#palette")).isVisible();

        page.keyboard().press("Escape");

        assertThat(page.locator("#palette")).isHidden();
        assertThat(page.locator("#pane")).isVisible();
    }

    @Test
    void aFlaggedRowLeadsItsGroup() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a2").lastActiveTimestamp(System.currentTimeMillis()).build());
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));
        page.locator("#tasks .row:has-text(\"a2\") .pick").click();

        page.locator("#pane button.flag").click();

        assertThat(page.locator("#tasks .row .alias")).hasText(new String[]{"a2", "a1"});
    }

    @Test
    void theFilterBoxShowsItHoldsTheFocusWhenSlashMovesItThere() {
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

        page.keyboard().press("/");

        assertThat(page.locator(".search:focus-within")).hasCSS("border-top-color", "rgb(29, 78, 216)");
    }
}
