package dev.jagt.orchestrator.board;

import com.microsoft.playwright.Page;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.job.Jobs;
import dev.jagt.orchestrator.task.Launched;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BoardPopupsAndTabsTest extends BoardPageContext {

    @Autowired
    Jobs jobs;

    @Test
    void theReportsMenuOpensAReportAndClosesOnEscape() {
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

        page.locator("#reports-toggle").click();
        assertThat(page.locator("#reports")).isVisible();
        page.keyboard().press("Escape");
        assertThat(page.locator("#reports")).isHidden();
        page.locator("#reports-toggle").click();
        page.locator("#show-stats").click();

        assertThat(page.locator("#report")).isVisible();
        assertThat(page.locator("#reports")).isHidden();
    }

    @Test
    void theKeyTOpensTheNewTaskFormAtItsTicket() {
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

        page.keyboard().press("t");

        assertThat(page.locator("#launch-dialog")).isVisible();
        assertThat(page.locator("#ref")).isFocused();
    }

    @Test
    void addingAProjectReadsThePathAndRefusesOneThatIsNotARepository() throws Exception {
        Path folder = Files.createDirectories(root.resolve("notes"));
        String before = Files.readString(root.resolve("jagt.yml"));
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        page.locator("#new-task").click();
        page.locator("#add-project").click();

        page.locator("#project-path").fill(folder.toString());
        assertThat(page.locator("#project-checks")).containsText("not a git repository");
        page.locator("#project-key").fill("notes");
        page.locator("#project-base").fill("origin/main");
        page.locator("#project-form button[type=submit]").click();

        assertThat(page.locator("#toasts .toast.error")).containsText("not a git repository");
        org.assertj.core.api.Assertions.assertThat(Files.readString(root.resolve("jagt.yml"))).isEqualTo(before);
    }

    @Test
    void aFailedJobOpensFromTheHeaderAsATableWhoseRowRunsItNow() {
        breakable.breakIt();
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));
        jobs.runNow("breakable");
        page.waitForCondition(() -> jobs.summary(System.currentTimeMillis()).failing() > 0);
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());

        page.locator("#jobs-pulse").click();
        assertThat(page.locator("table.jobs tr.failed")).containsText("breakable");
        assertThat(page.locator("table.jobs[aria-label=housekeeping] tbody tr").first()).hasClass(Pattern.compile("failed"));
        page.locator("table.jobs button[data-job=breakable]").click();

        assertThat(page.locator("#toasts .toast").last()).containsText("breakable runs within a second");
    }

    @Test
    void theSessionTabShowsTheScreenAndSendsALineToThatSession() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        when(sessions.screen("ABC-1")).thenReturn(Optional.of("✗ two tests fail"));
        when(sessions.say("ABC-1", "mock the storage")).thenReturn("Said to the agent.");
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#pane article .alias")).hasText("a1");

        page.locator("#pane .tabs button[data-tab=session]").click();
        assertThat(page.locator("#pane .session .screen")).hasText("✗ two tests fail");
        page.locator("#pane .say input").fill("mock the storage");
        page.locator("#pane .say input").press("Enter");

        assertThat(page.locator("#toasts .toast")).hasText("Said to the agent.");
        verify(sessions).say("ABC-1", "mock the storage");
    }

    @Test
    void theReviewRoundTabReadsTheDraftedRepliesOrSaysThereAreNone() throws Exception {
        Path worktree = Files.createDirectories(root.resolve("ABC-2-alpha"));
        Files.writeString(worktree.resolve("review_replies.md"), "## thread 1\nFIXED - Renamed it.\n");
        state.putTask("ABC-2", TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a2").mrUrl("https://host.example/mr/7").lastActiveTimestamp(System.currentTimeMillis())
                .build());
        state.putTask("ABC-3", TaskState.builder("alpha", root.resolve("ABC-3-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a3").lastActiveTimestamp(System.currentTimeMillis()).build());
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        page.locator("#tasks .row:has-text(\"a2\") .pick").click();

        page.locator("#pane .tabs button[data-tab=round]").click();
        assertThat(page.locator("#pane .round")).containsText("Renamed it.");
        assertThat(page.locator("#pane .round .verdict.ok")).hasText("FIXED");
        page.locator("#tasks .row:has-text(\"a3\") .pick").click();

        assertThat(page.locator("#pane .round")).containsText("Nothing is drafted for this round.");
    }

    @Test
    void aVerbsKeyRunsItOnTheOpenTask() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        when(commands.execute("ABC-1", TaskAction.FOCUS)).thenReturn("Focused ABC-1.");
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#pane article .alias")).hasText("a1");

        page.keyboard().press("f");

        assertThat(page.locator("#toasts .toast")).hasText("Focused ABC-1.");
        verify(commands).execute("ABC-1", TaskAction.FOCUS);
    }

    @Test
    void doneStandsLastInItsRowApartFromTheVerbsThatMoveTheTaskOn() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#pane .actions.flow button").last()).hasClass(Pattern.compile("\\bapart\\b"));
        assertThat(page.locator("#pane .actions.flow button.apart")).hasText("Done");
        org.assertj.core.api.Assertions.assertThat(page.locator("#pane button.apart").boundingBox().x)
                .isGreaterThan(page.locator("#pane button[data-action=respawn]").boundingBox().x);
    }

    @Test
    void theHeaderSaysWhileTheMasterPressesTheHumansButtons() {
        Page page = session.newPage();
        page.route("**/api/tasks", route -> route.fulfill(new com.microsoft.playwright.Route.FulfillOptions()
                .setResponse(route.fetch())
                .setBody(route.fetch().text().replaceFirst("\"masterMode\":\"[a-z]+\"", "\"masterMode\":\"act\""))));

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#master-mode")).hasText("Master: act");
    }

    @Test
    void theReportsMenuDropsBelowItsButtonAndLeavesTheHeaderOneLine() {
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

        page.locator("#reports-toggle").click();

        var toggle = page.locator("#reports-toggle").boundingBox();
        org.assertj.core.api.Assertions.assertThat(page.locator("#reports").boundingBox().y)
                .isGreaterThanOrEqualTo(toggle.y + toggle.height);
        org.assertj.core.api.Assertions.assertThat(page.locator("body > header").boundingBox().height).isLessThan(60);
    }

    @Test
    void aVerbsKeyDoesNothingWhileANarrowWindowShowsOnlyTheList() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        when(commands.execute("ABC-1", TaskAction.FOCUS)).thenReturn("Focused ABC-1.");
        Page page = session.newPage();
        page.setViewportSize(800, 900);
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#tasks .row")).hasCount(1);

        page.keyboard().press("f");
        page.keyboard().press("Enter");
        page.keyboard().press("f");

        assertThat(page.locator("#toasts .toast")).hasCount(1);
        verify(commands, org.mockito.Mockito.times(1)).execute("ABC-1", TaskAction.FOCUS);
    }

    @Test
    void theSessionTabReadsTheScreenAgainWhileItStaysOpen() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        when(sessions.screen("ABC-1")).thenReturn(Optional.of("compiling"), Optional.of("tests pass"));
        Page page = session.newPage();
        page.clock().install();
        page.navigate("http://localhost:" + port + "/");
        page.locator("#pane .tabs button[data-tab=session]").click();
        assertThat(page.locator("#pane .session .screen")).hasText("compiling");

        page.clock().runFor(5_000);

        assertThat(page.locator("#pane .session .screen")).hasText("tests pass");
    }

    @Test
    void aJobThatHasNotRunYetIsNotCalledFine() {
        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/styleguide.html");

        assertThat(page.locator("table.jobs [aria-label='not run yet']")).hasCount(1);
    }

    @Test
    void aTaskWaitingOnTheHumanThatBrokeWearsARedBadgeAndItsHistoryReadsInWords() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.CI_FAILED).alias("a1").message("lint red").lastActiveTimestamp(System.currentTimeMillis())
                .build());
        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#tasks .row .badge.problem")).hasCount(1);
        assertThat(page.locator("#pane .history")).containsText("checks failed");
    }

    @Test
    void theListSaysWhatItsKeysDo() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#tasks .keys")).containsText("n next yours");
    }

    @Test
    void anEmptyBoardOffersANewTaskAndAProject() {
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

        page.locator("#empty-project").click();

        assertThat(page.locator("#project-dialog")).isVisible();
    }

    @Test
    void theSessionTabKeepsItsScreenWhileAPushMakesItReadAgain() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        when(sessions.screen("ABC-1")).thenReturn(Optional.of("compiling")).thenAnswer(call -> {
            release.await();
            return Optional.of("tests pass");
        });
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        page.locator("#pane .tabs button[data-tab=session]").click();
        assertThat(page.locator("#pane .session .screen")).hasText("compiling");

        state.putTask("ABC-1", state.task("ABC-1").orElseThrow().touched());
        verify(sessions, timeout(5000).times(2)).screen("ABC-1");

        assertThat(page.locator("#pane .session .screen")).hasText("compiling");
        release.countDown();
        assertThat(page.locator("#pane .session .screen")).hasText("tests pass");
    }

    @Test
    void theRoundTabReadsRepliesThatAppearedWithoutAnyPush() throws Exception {
        Path worktree = Files.createDirectories(root.resolve("ABC-4-alpha"));
        state.putTask("ABC-4", TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a4").mrUrl("https://host.example/mr/7").lastActiveTimestamp(System.currentTimeMillis())
                .build());
        when(launcher.launch(any())).thenReturn(Launched.created("ABC-9", "Started ABC-9."));
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        page.locator("#pane .tabs button[data-tab=round]").click();
        assertThat(page.locator("#pane .round")).containsText("Nothing is drafted for this round.");

        Files.writeString(worktree.resolve("review_replies.md"), "## thread 1\nFIXED - Renamed it.\n");
        page.locator("#new-task").click();
        page.locator("#ref").fill("ABC-9");
        page.locator("#launch button[type=submit]").click();

        assertThat(page.locator("#pane .round")).containsText("Renamed it.");
    }

    @Test
    void aSessionNoWindowHoldsSaysSoAndABlankLiveScreenDoesNot() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        when(sessions.screen("ABC-1")).thenReturn(Optional.of(""));
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");

        page.locator("#pane .tabs button[data-tab=session]").click();

        assertThat(page.locator("#pane .session .screen")).isEmpty();
    }

    @Test
    void theJobsTableTurnsAFailedJobRedWithoutReopeningTheReport() {
        breakable.breakIt();
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));
        page.locator("#reports-toggle").click();
        page.locator("#show-jobs").click();
        assertThat(page.locator("table.jobs tr.failed")).hasCount(0);

        jobs.runNow("breakable");
        page.waitForCondition(() -> jobs.summary(System.currentTimeMillis()).failing() > 0);
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());

        assertThat(page.locator("table.jobs tr.failed")).containsText("breakable");
    }

    @Test
    void theJobGroupsOfTheJobsTableShareOneColumnGrid() {
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/styleguide.html");

        var first = page.locator("table.jobs").nth(0);
        var second = page.locator("table.jobs").nth(1);

        org.assertj.core.api.Assertions.assertThat(first.locator("th").nth(3).boundingBox().x)
                .isEqualTo(second.locator("th").nth(3).boundingBox().x);
        org.assertj.core.api.Assertions.assertThat(first.locator("td").nth(3).boundingBox().x)
                .isEqualTo(second.locator("td").nth(3).boundingBox().x);
        org.assertj.core.api.Assertions.assertThat(first.locator("td").nth(3).boundingBox().width)
                .isEqualTo(second.locator("td").nth(3).boundingBox().width);
    }
}
