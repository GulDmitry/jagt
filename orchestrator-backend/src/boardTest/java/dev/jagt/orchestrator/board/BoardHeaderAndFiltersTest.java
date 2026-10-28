package dev.jagt.orchestrator.board;

import com.microsoft.playwright.Page;
import dev.jagt.orchestrator.job.Jobs;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Duration;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

class BoardHeaderAndFiltersTest extends BoardPageContext {

    @Autowired
    Jobs jobs;

    @Test
    void aPollRunningAsItShouldIsNotNewsWhileItsNextLookStaysOnTheRequest() {
        long shipped = System.currentTimeMillis();
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").lastActiveTimestamp(shipped)
                .mrUrl("https://host/alpha/-/merge_requests/1").mrCreatedAt(shipped).lastPolledAt(shipped)
                .build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

        assertThat(page.locator("#auto-review")).isHidden();
        assertThat(page.locator("article .meta a.mr-age"))
                .hasAttribute("data-tip", Pattern.compile("next poll 10m"));
    }

    @Test
    void saysNothingAboutAPollForATaskThatIsNotOutForReview() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

        assertThat(page.locator("article")).hasCount(1);
        assertThat(page.locator("article a.mr-age")).hasCount(0);
    }

    @Test
    void countsEveryPhaseWhetherOrNotItHoldsATask() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                TaskStatus.REVIEW_PENDING).alias("a2").lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-3", TaskState.builder("beta", root.resolve("ABC-3-beta").toString(),
                TaskStatus.DEPLOYED).alias("b1").lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

        assertThat(page.locator("#phases .phase")).hasText(
                new String[]{"build 1", "review 1", "check 0", "ready 0", "deploy 1", "done 0"});
        assertThat(page.locator("#phases"))
                .hasText("build 1 · review 1 · check 0 · ready 0 · deploy 1 · done 0 · order: added");
    }

    @Test
    void ordersTasksAsTheyWereRegisteredSoAReusedAliasCannotMoveACardAlreadyOnTheBoard() {
        state.putTask("ABC-10", TaskState.builder("alpha", root.resolve("ABC-10-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a10").lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a2").lastActiveTimestamp(System.currentTimeMillis() - 60_000).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

        assertThat(page.locator("#tasks .row .alias")).hasText(new String[]{"a10", "a2"});
    }

    @Test
    void ordersTasksByAliasWhenTheOrderControlIsPressed() {
        state.putTask("ABC-10", TaskState.builder("alpha", root.resolve("ABC-10-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a10").lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a2").lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));
        page.locator("#phases button.order").click();

        assertThat(page.locator("#tasks .row .alias")).hasText(new String[]{"a2", "a10"});
        assertThat(page.locator("#phases button.order")).hasText("order: alias");
    }

    @Test
    void theOpenTaskShowsATitleTooLongForItsRowWhole() {
        String whole = "A ticket whose first paragraph was pasted into its title".repeat(4);
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").title(whole).lastActiveTimestamp(System.currentTimeMillis())
                        .build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

        assertThat(page.locator("#pane article .title")).hasText(whole);
        assertThat(page.locator("#tasks .row-title")).hasText(whole);
    }

    @Test
    void theOpenTaskShowsItsWholeLine() {
        String reason = "a merge conflict in a file whose path runs on ".repeat(4);
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.DEPLOY_CONFLICT).alias("a1").message(reason).lastActiveTimestamp(System
                        .currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

        assertThat(page.locator("#pane article .detail")).hasText("NEEDS YOU: " + reason);
    }

    @Test
    void showsOnlyThePhaseWhoseCountWasClicked() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                TaskStatus.REVIEW_PENDING).alias("a2").lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));
        page.locator("#phases button.phase").nth(1).click();

        assertThat(page.locator("article .alias")).hasText(new String[]{"a2"});
    }

    @Test
    void keepsEveryPhaseCountWhileOneOfThemIsTheFilter() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                TaskStatus.REVIEW_PENDING).alias("a2").lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));
        page.locator("#phases button.phase").nth(1).click();

        assertThat(page.locator("#phases")).containsText("build 1 · review 1");
    }

    @Test
    void refusesToFilterByAPhaseThatHoldsNothing() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

        assertThat(page.locator("#phases button.phase").nth(1)).isDisabled();
    }

    @Test
    void saysThatFiltersAreHidingEverythingRatherThanShowingABlankBoard() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.IN_PROGRESS).alias("a1").title("Widget layout is off")
                .lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));
        page.locator("#filter").fill("nothing matches this");

        assertThat(page.locator("article")).hasCount(0);
        assertThat(page.locator("#filtered")).hasText("No task matches: 1 filter(s) on, 1 task(s) hidden.");
    }

    @Test
    void clearsEveryFilterAtOnce() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                TaskStatus.REVIEW_PENDING).alias("a2").lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));
        page.locator("#phases button.phase").nth(1).click();
        page.locator("#phases .clear-filters").click();

        assertThat(page.locator("#tasks .row .alias")).hasText(new String[]{"a1", "a2"});
    }

    @Test
    void narrowsTheBoardToWhateverMatchesTheTypedText() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.IN_PROGRESS).alias("a1").title("Widget layout is off")
                .lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                        TaskStatus.IN_PROGRESS).alias("a2").title("Invoice totals are wrong")
                .lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));
        page.locator("#filter").fill("invoice");

        assertThat(page.locator("article .alias")).hasText(new String[]{"a2"});
    }

    @Test
    void findsATaskByItsTicketNumberAsWellAsItsTitle() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("XYZ-9", TaskState.builder("alpha", root.resolve("XYZ-9-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("x1").lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));
        page.locator("#filter").fill("xyz-9");

        assertThat(page.locator("article .alias")).hasText(new String[]{"x1"});
    }

    @Test
    void anEmptyBoardSaysWhereATaskComesFrom() {
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

        assertThat(page.locator("#empty")).containsText("No tasks yet.");
        assertThat(page.locator("#empty")).not().containsText("ABC-42");
    }

    @Test
    void saysNothingInTheHeaderAboutJobsWhileNoneHasFailed() {
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

        assertThat(page.locator("#jobs-pulse")).isHidden();
    }

    @Test
    void givesActBackFromTheHeaderWhereItStoppedItself() throws Exception {
        java.nio.file.Files.writeString(root.resolve("act-stopped"), "ABC-1\n");
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#act-stopped")).isVisible();

        page.locator("#act-stopped").click();

        assertThat(page.locator("#act-stopped")).isHidden();
    }

    @Test
    void namesTheDailyCeilingOnTheActStoppedChipAndLiftsItOnAPress() throws Exception {
        java.nio.file.Files.writeString(root.resolve("starts-stopped"), java.time.LocalDate.now() + " 50\n");
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#act-stopped")).hasAttribute("data-tip", Pattern.compile("50 unattended starts"));

        page.locator("#act-stopped").click();

        assertThat(page.locator("#act-stopped")).isHidden();
    }

    @Test
    void namesTheUnreadableStopFileOnTheActStoppedChip() throws Exception {
        java.nio.file.Files.writeString(root.resolve("starts-stopped"), "garbage\n");
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#act-stopped")).hasAttribute("data-tip", Pattern.compile("starts-stopped cannot be read"));
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void anOpenTipOnTheJobsChipFollowsItsCountdownThroughARepaint() {
        breakable.breakIt();
        long now = System.currentTimeMillis();
        Page page = session.newPage();
        page.clock().setFixedTime(now - Duration.ofSeconds(630).toMillis());
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));
        jobs.runNow("breakable");
        page.waitForCondition(() -> jobs.summary(System.currentTimeMillis()).failing() > 0);
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(now).build());
        page.locator("#jobs-pulse").hover();
        assertThat(page.locator("#tip")).containsText("next run: 11m");

        page.clock().setFixedTime(now - Duration.ofSeconds(330).toMillis());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a2").lastActiveTimestamp(now).build());

        assertThat(page.locator("#tasks .row")).hasCount(2);
        assertThat(page.locator("#tip")).containsText("next run: 6m");
    }

    @Test
    void theHeaderCountsTheTasksWhoseTurnItIs() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                TaskStatus.REVIEWED).alias("a2").lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

        assertThat(page.locator("#waiting")).hasText("1 need your action");
    }

}
