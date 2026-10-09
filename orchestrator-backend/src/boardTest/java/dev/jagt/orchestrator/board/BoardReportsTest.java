package dev.jagt.orchestrator.board;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.AriaRole;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BoardReportsTest extends BoardPageContext {

    @Test
    void theLogButtonKeepsItsNameAndCarriesTheLastMessageInItsHover() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        when(commands.execute("ABC-1", TaskAction.FOCUS)).thenReturn("Focused ABC-1 — its window is in front.");

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Focus").setExact(true)).click();

        assertThat(page.locator("#show-log")).hasText("log");
        assertThat(page.locator("#show-log"))
                .hasAttribute("data-tip", Pattern.compile("Focused ABC-1 — its window is in front."));
    }

    @Test
    void aReportThatAnswersForOneTaskGetsNoButtonInTheBarOfReports() {
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("#show-stats")).hasCount(1);
        assertThat(page.locator("#show-replies")).hasCount(0);
    }

    @Test
    void draftedReviewRepliesAreAnnouncedOnTheCard() throws IOException {
        Path worktree = Files.createDirectories(root.resolve("ABC-1-alpha"));
        Files.writeString(worktree.resolve("review_replies.md"), "## thread 1\nagreed, fixed\n");
        state.putTask("ABC-1", TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a1").mrUrl("https://host.example/mr/7").lastActiveTimestamp(System.currentTimeMillis())
                .build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("article .actions.tool .offer")).containsText("Replies");
    }

    @Test
    void anOfferedReportIsHintedByItsVerbEvenWhenTheVerbsArriveAfterTheCards() throws IOException {
        Path worktree = Files.createDirectories(root.resolve("ABC-19-alpha"));
        Files.writeString(worktree.resolve("review_replies.md"), "## thread 1\nFIXED - Renamed it.\n");
        state.putTask("ABC-19", TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a19").mrUrl("https://host.example/mr/7").lastActiveTimestamp(System.currentTimeMillis())
                .build());
        List<Route> verbs = new java.util.ArrayList<>();
        Page page = session.newPage();
        page.route("**/api/commands", verbs::add);
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("article .offer")).hasText("Replies");

        verbs.get(0).resume();

        assertThat(page.locator("article .offer")).hasAttribute("data-tip",
                "the answers a round drafted, comment by comment, before `ship` posts them");
    }

    @Test
    void verbsThatCouldNotBeReadAreSaidRatherThanSwallowed() {
        Page page = session.newPage();
        page.route("**/api/commands", route -> route.fulfill(new Route.FulfillOptions().setStatus(500)
                .setContentType("application/json").setBody("{\"error\":\"the command list failed\"}")));
        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#toasts .toast.error")).hasText("Cannot read the commands: the command list failed");
    }

    @Test
    void theDraftedRepliesLineOpensEveryCommentAndTheReplyItWillSend() throws IOException {
        Path worktree = Files.createDirectories(root.resolve("ABC-3-alpha"));
        Files.writeString(worktree.resolve("review_replies.md"),
                "## !12 thread 1\n> the canonical row count is wrong\nFIXED - Measured it and pinned the count.\n");
        state.putTask("ABC-3", TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a3").mrUrl("https://host.example/mr/7").lastActiveTimestamp(System.currentTimeMillis())
                .build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.locator("article .offer").click();

        assertThat(page.locator("#report-body")).containsText("1 · FIXED · !12 thread 1");
        assertThat(page.locator("#report-body")).containsText("Measured it and pinned the count.");
    }

    @Test
    void theCommentAndTheAnswerToItAreToldApartWithoutASeparator() throws IOException {
        Path worktree = Files.createDirectories(root.resolve("ABC-4-alpha"));
        Files.writeString(worktree.resolve("review_replies.md"),
                "## !12 thread 1\n> the canonical row count is wrong\nFIXED - Measured it and pinned the count.\n");
        state.putTask("ABC-4", TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a4").mrUrl("https://host.example/mr/7").lastActiveTimestamp(System.currentTimeMillis())
                .build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.locator("article .offer").click();

        assertThat(page.locator("#report-body .verdict.ok")).hasText("FIXED ");
        assertThat(page.locator("#report-body .quote")).containsText("the canonical row count is wrong");
    }

    @Test
    void aLineSaidAtAnOpenRoundReachesTheSessionOfTheTaskItIsAbout() throws IOException {
        Path worktree = Files.createDirectories(root.resolve("ABC-5-alpha"));
        Files.writeString(worktree.resolve("review_replies.md"), "## thread 1\nFIXED - Renamed it.\n");
        state.putTask("ABC-5", TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a5").mrUrl("https://host.example/mr/7").lastActiveTimestamp(System.currentTimeMillis())
                .build());
        when(sessions.say("a5", "no, answer 1 differently")).thenReturn("Said to the agent.");

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.locator("article .offer").click();
        page.locator("#say").fill("no, answer 1 differently");
        page.locator("#say").press("Enter");

        assertThat(page.locator("#said")).isVisible();
        verify(sessions).say("a5", "no, answer 1 differently");
    }

    @Test
    void aReportThatIsNotAboutOneTaskOffersNoLineEvenWhenAnArgumentNamesOne() {
        state.putTask("ABC-12", TaskState.builder("alpha", root.resolve("ABC-12-alpha").toString(),
                TaskStatus.REVIEW_PENDING).alias("a12").lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.keyboard().press("Control+k");
        page.locator("#ask").fill("activity a12");
        page.locator("#ask").press("Enter");

        assertThat(page.locator("#report-title")).containsText("activity");
        assertThat(page.locator("#report-line")).isHidden();
    }

    @Test
    void aLineTypedAtOneRoundIsNotCarriedIntoTheNextOneOpened() throws IOException {
        Path first = Files.createDirectories(root.resolve("ABC-13-alpha"));
        Files.writeString(first.resolve("review_replies.md"), "## thread 1\nFIXED - Renamed it.\n");
        Path second = Files.createDirectories(root.resolve("ABC-14-alpha"));
        Files.writeString(second.resolve("review_replies.md"), "## thread 1\nFIXED - Pinned the count.\n");
        state.putTask("ABC-13", TaskState.builder("alpha", first.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a13").mrUrl("https://host.example/mr/7").lastActiveTimestamp(System.currentTimeMillis())
                .build());
        state.putTask("ABC-14", TaskState.builder("alpha", second.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a14").mrUrl("https://host.example/mr/8").lastActiveTimestamp(System.currentTimeMillis())
                .build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.locator("article:has-text(\"a13\") .offer").click();
        page.locator("#say").fill("no, answer 1 differently");
        page.locator("#close-report").click();
        page.locator("article:has-text(\"a14\") .offer").click();

        assertThat(page.locator("#say")).hasValue("");
    }

    @Test
    void whatTheWaitingRingMeansIsReadableOverTheDialogItPulsesIn() throws IOException {
        Path worktree = Files.createDirectories(root.resolve("ABC-15-alpha"));
        Files.writeString(worktree.resolve("review_replies.md"), "## thread 1\nFIXED - Renamed it.\n");
        state.putTask("ABC-15", TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a15").mrUrl("https://host.example/mr/7").lastActiveTimestamp(System.currentTimeMillis())
                .build());
        when(sessions.say("a15", "no, answer 1 differently")).thenReturn("Said to the agent.");

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.locator("article .offer").click();
        page.locator("#say").fill("no, answer 1 differently");
        page.locator("#say").press("Enter");
        page.locator("#said").hover();

        assertThat(page.locator("#report #tip")).isVisible();
    }

    @Test
    void anOpenRoundNobodyHasAnsweredYetShowsNothingWaiting() throws IOException {
        Path worktree = Files.createDirectories(root.resolve("ABC-11-alpha"));
        Files.writeString(worktree.resolve("review_replies.md"), "## thread 1\nFIXED - Renamed it.\n");
        state.putTask("ABC-11", TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a11").mrUrl("https://host.example/mr/7").lastActiveTimestamp(System.currentTimeMillis())
                .build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.locator("article .offer").click();

        assertThat(page.locator("#said")).isHidden();
    }

    @Test
    void aReportAnsweringForEveryTaskHasNoSessionToSayAnythingTo() {
        state.putTask("ABC-8", TaskState.builder("alpha", root.resolve("ABC-8-alpha").toString(),
                TaskStatus.REVIEW_PENDING).alias("a8").lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.keyboard().press("Control+k");
        page.locator("#ask").fill("replies");
        page.locator("#ask").press("Enter");

        assertThat(page.locator("#report")).isVisible();
        assertThat(page.locator("#report-line")).isHidden();
    }

    @Test
    void anOpenRoundIsReadAgainWhenTheAgentAnswersUnderIt() throws IOException {
        Path worktree = Files.createDirectories(root.resolve("ABC-9-alpha"));
        Files.writeString(worktree.resolve("review_replies.md"), "## thread 1\nFIXED - Renamed it.\n");
        state.putTask("ABC-9", TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a9").mrUrl("https://host.example/mr/7").lastActiveTimestamp(System.currentTimeMillis())
                .build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.locator("article .offer").click();
        Files.writeString(worktree.resolve("review_replies.md"),
                "## thread 1\nNO CHANGE - The name is the one the caller uses.\n");
        state.putTask("ABC-9", state.task("ABC-9").orElseThrow()
                .withStatus(TaskStatus.REVIEW_PENDING, "answered"));

        assertThat(page.locator("#report-body")).containsText("The name is the one the caller uses.");
    }

    @Test
    void aRoundRewrittenWhileItsReportWasStillBeingReadShowsWhatTheFileSaysNow() throws IOException {
        Path worktree = Files.createDirectories(root.resolve("ABC-10-alpha"));
        Files.writeString(worktree.resolve("review_replies.md"), "## thread 1\nFIXED - Renamed it.\n");
        state.putTask("ABC-10", TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a10").mrUrl("https://host.example/mr/7").lastActiveTimestamp(System.currentTimeMillis())
                .build());
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.route("**/api/commands/replies**", route -> {
            APIResponse read = route.fetch();
            try {
                Files.writeString(worktree.resolve("review_replies.md"),
                        "## thread 1\nNO CHANGE - The name is the one the caller uses.\n");
            } catch (IOException cannotStageTheRewrite) {
                throw new UncheckedIOException(cannotStageTheRewrite);
            }
            route.fulfill(new Route.FulfillOptions().setResponse(read));
        });

        page.locator("article .offer").click();

        assertThat(page.locator("#report-body")).containsText("The name is the one the caller uses.");
    }

    @Test
    void aReportsOwnLinksAreFollowableAndNothingElseBecomesOne() throws IOException {
        Path worktree = Files.createDirectories(root.resolve("ABC-6-alpha"));
        Files.writeString(worktree.resolve("review_replies.md"),
                "## thread 1\n> see https://host.example/mr/7#note_8708.\nFIXED - javascript:alert(1) stays text.\n");
        state.putTask("ABC-6", TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a6").mrUrl("https://host.example/mr/7").lastActiveTimestamp(System.currentTimeMillis())
                .build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.locator("article .offer").click();

        assertThat(page.locator("#report-body a"))
                .hasAttribute("href", "https://host.example/mr/7#note_8708");
        assertThat(page.locator("#report-body")).containsText("javascript:alert(1) stays text.");
        assertThat(page.locator("#report-body a")).hasCount(1);
    }

    @Test
    void aReportThatCouldNotBeReadSaysTheSentenceOfTheRefusalRatherThanItsWireShape() throws IOException {
        Path worktree = Files.createDirectories(root.resolve("ABC-16-alpha"));
        Files.writeString(worktree.resolve("review_replies.md"), "## thread 1\nFIXED - Renamed it.\n");
        state.putTask("ABC-16", TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a16").mrUrl("https://host.example/mr/7").lastActiveTimestamp(System.currentTimeMillis())
                .build());
        doThrow(new IllegalStateException("the round file is not readable")).when(replies).render("a16");

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.locator("article .offer").click();

        assertThat(page.locator("#toasts .toast.error")).hasText("the round file is not readable");
    }

    @Test
    void aReportAboutATaskThatIsGoneSaysTheBoardIsUpToDateNow() throws IOException {
        Path worktree = Files.createDirectories(root.resolve("ABC-18-alpha"));
        Files.writeString(worktree.resolve("review_replies.md"), "## thread 1\nFIXED - Renamed it.\n");
        state.putTask("ABC-18", TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a18").mrUrl("https://host.example/mr/7").lastActiveTimestamp(System.currentTimeMillis())
                .build());
        doThrow(Refusal.noSuchTask("a18")).when(replies).render("a18");
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        page.locator("article .offer").click();

        assertThat(page.locator("#toasts .toast.error")).containsText("The board is up to date now.");
    }

    @Test
    void aRoundReadAgainTwiceShowsTheLaterReadWhenTheEarlierOneAnswersLast() throws IOException {
        Path worktree = Files.createDirectories(root.resolve("ABC-19-alpha"));
        Files.writeString(worktree.resolve("review_replies.md"), "## thread 1\nFIXED - Renamed it.\n");
        state.putTask("ABC-19", TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a19").mrUrl("https://host.example/mr/7").lastActiveTimestamp(System.currentTimeMillis())
                .build());
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.locator("article .offer").click();
        assertThat(page.locator("#report-body")).containsText("Renamed it.");
        List<Route> held = new java.util.concurrent.CopyOnWriteArrayList<>();
        List<APIResponse> read = new java.util.concurrent.CopyOnWriteArrayList<>();
        page.route("**/api/commands/replies**", route -> {
            read.add(route.fetch());
            held.add(route);
        });
        Files.writeString(worktree.resolve("review_replies.md"), "## thread 1\nFIXED - The older read.\n");
        page.waitForRequest("**/api/commands/replies**", () -> state.putTask("ABC-19",
                state.task("ABC-19").orElseThrow().withStatus(TaskStatus.REVIEW_PENDING, "older")));
        page.waitForCondition(() -> held.size() == 1);
        Files.writeString(worktree.resolve("review_replies.md"), "## thread 1\nFIXED - The newer read.\n");
        page.waitForRequest("**/api/commands/replies**", () -> state.putTask("ABC-19",
                state.task("ABC-19").orElseThrow().withStatus(TaskStatus.REVIEW_PENDING, "newer")));
        page.waitForCondition(() -> held.size() == 2);
        held.get(1).fulfill(new Route.FulfillOptions().setResponse(read.get(1)));
        assertThat(page.locator("#report-body")).containsText("The newer read.");

        page.waitForRequestFinished(() -> held.get(0).fulfill(new Route.FulfillOptions().setResponse(read.get(0))));

        org.assertj.core.api.Assertions.assertThat(page.locator("#report-body").textContent())
                .contains("The newer read.");
    }

    @Test
    void aReportAboutOneTaskIsTitledByBothTheNamesItAnswersTo() throws IOException {
        Path worktree = Files.createDirectories(root.resolve("ABC-7-alpha"));
        Files.writeString(worktree.resolve("review_replies.md"), "## thread 1\nFIXED - Renamed it.\n");
        state.putTask("ABC-7", TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a7").mrUrl("https://host.example/mr/7").lastActiveTimestamp(System.currentTimeMillis())
                .build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.locator("article .offer").click();

        assertThat(page.locator("#report-title")).hasText("replies a7 \u00b7 ABC-7");
    }

    @Test
    void aReportIsNamedToAScreenReaderByItsTitle() throws IOException {
        Path worktree = Files.createDirectories(root.resolve("ABC-7-alpha"));
        Files.writeString(worktree.resolve("review_replies.md"), "## thread 1\nFIXED - Renamed it.\n");
        state.putTask("ABC-7", TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a7").mrUrl("https://host.example/mr/7").lastActiveTimestamp(System.currentTimeMillis())
                .build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.locator("article .offer").click();

        assertThat(page.getByRole(AriaRole.DIALOG, new Page.GetByRoleOptions().setName("replies a7 \u00b7 ABC-7")))
                .isVisible();
    }

    @Test
    void aReportOpenedEarlierThatFailsAfterALaterOneOpenedRaisesNoError() throws IOException {
        Path first = Files.createDirectories(root.resolve("ABC-7-alpha"));
        Files.writeString(first.resolve("review_replies.md"), "## thread 1\nFIXED - The first round.\n");
        state.putTask("ABC-7", TaskState.builder("alpha", first.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a7").mrUrl("https://host.example/mr/7").lastActiveTimestamp(System.currentTimeMillis())
                .build());
        Path second = Files.createDirectories(root.resolve("ABC-8-alpha"));
        Files.writeString(second.resolve("review_replies.md"), "## thread 1\nFIXED - The second round.\n");
        state.putTask("ABC-8", TaskState.builder("alpha", second.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a8").mrUrl("https://host.example/mr/8").lastActiveTimestamp(System.currentTimeMillis())
                .build());
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        List<Route> held = new java.util.concurrent.CopyOnWriteArrayList<>();
        page.route(Pattern.compile("/api/commands/replies\\?about=a7$"), held::add);
        page.waitForRequest(Pattern.compile("/api/commands/replies\\?about=a7$"),
                () -> page.locator("article", new Page.LocatorOptions().setHasText("a7")).locator(".offer").click());
        page.locator("article", new Page.LocatorOptions().setHasText("a8")).locator(".offer").click();
        assertThat(page.locator("#report-title")).hasText("replies a8 \u00b7 ABC-8");

        page.waitForRequestFinished(new Page.WaitForRequestFinishedOptions()
                .setPredicate(request -> request.url().endsWith("about=a7")),
                () -> held.get(0).fulfill(new Route.FulfillOptions().setStatus(500).setBody("{\"error\":\"too late\"}")));
        page.evaluate("() => new Promise(done => setTimeout(() => requestAnimationFrame(() => done(true))))");

        assertThat(page.locator("#toasts .toast.error")).hasCount(0);
    }

    @Test
    void aReportOpenedLastStaysOnScreenWhenAnEarlierOneAnswersAfterIt() throws IOException {
        Path first = Files.createDirectories(root.resolve("ABC-7-alpha"));
        Files.writeString(first.resolve("review_replies.md"), "## thread 1\nFIXED - The first round.\n");
        state.putTask("ABC-7", TaskState.builder("alpha", first.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a7").mrUrl("https://host.example/mr/7").lastActiveTimestamp(System.currentTimeMillis())
                .build());
        Path second = Files.createDirectories(root.resolve("ABC-8-alpha"));
        Files.writeString(second.resolve("review_replies.md"), "## thread 1\nFIXED - The second round.\n");
        state.putTask("ABC-8", TaskState.builder("alpha", second.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a8").mrUrl("https://host.example/mr/8").lastActiveTimestamp(System.currentTimeMillis())
                .build());
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        List<Route> held = new java.util.concurrent.CopyOnWriteArrayList<>();
        page.route(Pattern.compile("/api/commands/replies\\?about=a7$"), held::add);
        page.waitForRequest(Pattern.compile("/api/commands/replies\\?about=a7$"),
                () -> page.locator("article", new Page.LocatorOptions().setHasText("a7")).locator(".offer").click());
        page.locator("article", new Page.LocatorOptions().setHasText("a8")).locator(".offer").click();
        assertThat(page.locator("#report-title")).hasText("replies a8 \u00b7 ABC-8");

        page.waitForRequestFinished(new Page.WaitForRequestFinishedOptions()
                .setPredicate(request -> request.url().endsWith("about=a7")), () -> held.get(0).resume());
        page.evaluate("() => new Promise(done => setTimeout(() => requestAnimationFrame(() => done(true))))");

        org.assertj.core.api.Assertions.assertThat(page.locator("#report-body").textContent())
                .contains("The second round.");
    }

    @Test
    void aTypedReportNarrowsToTheTaskItNamesInsteadOfAnsweringForAllOfThem() throws IOException {
        Path drafting = Files.createDirectories(root.resolve("ABC-2-alpha"));
        Files.writeString(drafting.resolve("review_replies.md"), "## thread 1\nFIXED - Renamed it.\n");
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.REVIEW_PENDING).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", drafting.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a2").lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.keyboard().press("Control+k");
        page.locator("#ask").fill("replies a1");
        page.locator("#ask").press("Enter");

        assertThat(page.locator("#report-body")).containsText("a1 has no drafted replies");
    }

    @Test
    void showsWhatJagtDidOnItsOwnReadBackFromItsLog() throws IOException {
        Files.writeString(root.resolve("jagt.log"), """
                {"@timestamp":"2026-08-18T08:00:00Z","message":"sweep ABC-1: 2 thread(s) relayed","task":"ABC-1"}
                """, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.locator("#show-activity").click();

        assertThat(page.locator("#report-title")).containsText("activity");
        assertThat(page.locator("#report-body")).containsText("2 thread(s) relayed");
    }

    @Test
    void aReportClosesWhenTheDimmedAreaAroundItIsClicked() {
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.locator("#show-activity").click();
        assertThat(page.locator("#report")).isVisible();

        page.mouse().click(4, 4);

        assertThat(page.locator("#report")).isHidden();
    }

    @Test
    void theShortcutsLeaveTheBoardAloneWhileAReportIsOpen() {
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.locator("#show-activity").click();
        assertThat(page.locator("#report")).isVisible();
        page.keyboard().press("Control+k");
        page.locator("#close-report").click();

        assertThat(page.locator("#palette")).isHidden();
    }

    @Test
    void aReportSurvivesASelectionThatStartedInsideItAndEndedOutside() {
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.locator("#show-activity").click();

        page.locator("#report-body").hover();
        page.mouse().down();
        page.mouse().move(4, 4);
        page.mouse().up();

        assertThat(page.locator("#report")).isVisible();
    }

    @Test
    void helpAlsoSaysWhatTheBoardsOwnMarksMean() {
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Help").setExact(true)).click();

        assertThat(page.locator("#report")).isVisible();
        assertThat(page.locator("#report-section .legend button.again")).hasText("Deploy");
        assertThat(page.locator("#report-section .legend .checks.red")).hasCount(1);
        assertThat(page.locator("#report-section .legend button.offer")).hasCount(1);
        assertThat(page.locator("#report-section .legend a.id")).hasCount(1);
        assertThat(page.locator("#report-section .legend .repos .tick")).hasCount(1);
    }
}
