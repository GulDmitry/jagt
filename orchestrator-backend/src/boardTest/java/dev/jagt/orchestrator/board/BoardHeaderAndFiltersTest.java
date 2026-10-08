package dev.jagt.orchestrator.board;

import com.microsoft.playwright.Page;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

class BoardHeaderAndFiltersTest extends BoardPageContext {

    @Test
    void saysThatTheUnattendedPollIsOnAndWhenItWillNextLookAtATask() {
        long shipped = System.currentTimeMillis();
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").lastActiveTimestamp(shipped)
                .mrUrl("https://host/alpha/-/merge_requests/1").mrCreatedAt(shipped).lastPolledAt(shipped)
                .build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("#auto-review")).hasText("auto-review on");
        assertThat(page.locator("#auto-review")).hasClass(java.util.regex.Pattern.compile("on"));
        assertThat(page.locator("article .meta a.mr-age"))
                .hasAttribute("data-tip", Pattern.compile("next poll 10m"));
    }

    @Test
    void saysNothingAboutAPollForATaskThatIsNotOutForReview() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

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

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

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

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("article .alias")).hasText(new String[]{"a10", "a2"});
    }

    @Test
    void ordersTasksByAliasWhenTheOrderControlIsPressed() {
        state.putTask("ABC-10", TaskState.builder("alpha", root.resolve("ABC-10-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a10").lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a2").lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.locator("#phases button.order").click();

        assertThat(page.locator("article .alias")).hasText(new String[]{"a2", "a10"});
        assertThat(page.locator("#phases button.order")).hasText("order: alias");
    }

    @Test
    void foldsATitleTooLongForACardIntoItsHover() {
        String whole = "A ticket whose first paragraph was pasted into its title".repeat(4);
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").title(whole).lastActiveTimestamp(System.currentTimeMillis())
                        .build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("article .title")).hasText(whole.substring(0, 150) + "…");
        assertThat(page.locator("article .title")).hasAttribute("data-tip", whole);
    }

    @Test
    void foldsALineTooLongForACardIntoItsHover() {
        String reason = "a merge conflict in a file whose path runs on ".repeat(4);
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.DEPLOY_CONFLICT).alias("a1").message(reason).lastActiveTimestamp(System
                        .currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("article .detail")).hasText(("NEEDS YOU: " + reason).substring(0, 150) + "…");
        assertThat(page.locator("article .detail")).hasAttribute("data-tip", "NEEDS YOU: " + reason);
    }

    @Test
    void showsOnlyThePhaseWhoseCountWasClicked() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                TaskStatus.REVIEW_PENDING).alias("a2").lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
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

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.locator("#phases button.phase").nth(1).click();

        assertThat(page.locator("#phases")).containsText("build 1 · review 1");
    }

    @Test
    void refusesToFilterByAPhaseThatHoldsNothing() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("#phases button.phase").nth(1)).isDisabled();
    }

    @Test
    void saysThatFiltersAreHidingEverythingRatherThanShowingABlankBoard() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.IN_PROGRESS).alias("a1").title("Widget layout is off")
                .lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
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

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.locator("#phases button.phase").nth(1).click();
        page.locator("#phases .clear-filters").click();

        assertThat(page.locator("article .alias")).hasText(new String[]{"a1", "a2"});
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

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
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

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.locator("#filter").fill("xyz-9");

        assertThat(page.locator("article .alias")).hasText(new String[]{"x1"});
    }

    @Test
    void anEmptyBoardSaysWhereATaskComesFrom() {
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("#empty")).containsText("No tasks.");
    }

    @Test
    void saysNothingInTheHeaderAboutJobsWhileNoneHasFailed() {
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        assertThat(page.locator("#auto-review")).hasText("auto-review on");

        assertThat(page.locator("#jobs-pulse")).isHidden();
    }

    @Test
    void theHeaderCountsTheTasksWhoseTurnItIs() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                TaskStatus.REVIEWED).alias("a2").lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("#waiting")).hasText("1 need your action");
    }

    @Test
    void waitingOnMeLeavesOnlyTheTasksWhoseTurnItIs() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                TaskStatus.REVIEWED).alias("a2").lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.locator("#mine").check();

        assertThat(page.locator("article .alias")).hasText(new String[]{"a2"});
    }
}
