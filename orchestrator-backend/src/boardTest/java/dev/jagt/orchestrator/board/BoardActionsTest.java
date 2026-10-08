package dev.jagt.orchestrator.board;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BoardActionsTest extends BoardPageContext {

    @Test
    void revertingAsksFirstAndSaysOnlyTheLastDeployComesOut() throws Exception {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.DEPLOYED).alias("a1").mrUrl("https://host.example/mr/7")
                .lastActiveTimestamp(System.currentTimeMillis()).build());
        CompletableFuture<String> asked = new CompletableFuture<>();

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.onDialog(dialog -> {
            asked.complete(dialog.message());
            dialog.dismiss();
        });
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Revert").setExact(true)).click();

        org.assertj.core.api.Assertions.assertThat(asked.get(5, TimeUnit.SECONDS))
                .startsWith("Revert ABC-1?")
                .contains("This pushes a revert commit to:")
                .contains("alpha → dev")
                .contains("Only the LAST deploy comes out");
        verifyNoInteractions(commands);
    }

    @Test
    void clickingAnActionRunsItAndShowsTheSentenceItAnswered() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        when(commands.execute("ABC-1", TaskAction.FOCUS)).thenReturn("Focused ABC-1 — its window is in front.");

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Focus").setExact(true)).click();

        assertThat(page.locator("#toasts .toast")).hasText("Focused ABC-1 — its window is in front.");
        verify(commands).execute("ABC-1", TaskAction.FOCUS);
    }

    @Test
    void closingATaskAsksBeforeAnythingRuns() throws Exception {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        CompletableFuture<String> asked = new CompletableFuture<>();

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.onDialog(dialog -> {
            asked.complete(dialog.message());
            dialog.dismiss();
        });
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Done").setExact(true)).click();

        org.assertj.core.api.Assertions.assertThat(asked.get(5, TimeUnit.SECONDS)).startsWith("Done ABC-1?");
        verifyNoInteractions(commands);
    }

    @Test
    void deployingNamesEveryRepositoryAndTheBranchItWouldBePushedTo() throws Exception {
        state.putTask("ABC-1", TaskState.builder(List.of(
                        dev.jagt.orchestrator.task.TaskRepo.of("alpha", root.resolve("ABC-1-alpha").toString()),
                        new dev.jagt.orchestrator.task.TaskRepo("beta", root.resolve("ABC-1-beta").toString(),
                                null, "https://host.example/mr/8", null)),
                TaskStatus.REVIEW_PENDING).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        CompletableFuture<String> asked = new CompletableFuture<>();

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.onDialog(dialog -> {
            asked.complete(dialog.message());
            dialog.dismiss();
        });
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Deploy").setExact(true)).click();

        String question = asked.get(5, TimeUnit.SECONDS);
        org.assertj.core.api.Assertions.assertThat(question)
                .startsWith("Deploy ABC-1?")
                .contains("This merges and pushes:")
                .contains("alpha → dev")
                .contains("beta → dev");
        verifyNoInteractions(commands);
    }

    @Test
    void deployingAsksAboutTheBranchesAndAdvisesNothingAboutTheRound() throws Exception {
        Path worktree = Files.createDirectories(root.resolve("ABC-1-alpha"));
        Files.writeString(worktree.resolve("review_replies.md"), "## thread 1\nagreed, fixed\n");
        state.putTask("ABC-1", TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a1").mrUrl("https://host.example/mr/9").message("widget layout fixed")
                .lastActiveTimestamp(System.currentTimeMillis()).build());
        CompletableFuture<String> asked = new CompletableFuture<>();

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.onDialog(dialog -> {
            asked.complete(dialog.message());
            dialog.dismiss();
        });
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Deploy").setExact(true)).click();

        org.assertj.core.api.Assertions.assertThat(asked.get(5, TimeUnit.SECONDS))
                .isEqualTo("Deploy ABC-1?\n\nThis merges and pushes:\nalpha → dev");
        verifyNoInteractions(commands);
    }

    @Test
    void aRefusedActionAlsoSaysTheBoardHasCaughtUp() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        when(commands.execute("ABC-1", TaskAction.SHIP)).thenThrow(new Refusal(
                Refusal.Code.ACTION_NOT_AVAILABLE, "Ship is not available for ABC-1 (it is DEPLOYED)"));

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Ship").setExact(true)).click();

        assertThat(page.locator("#toasts .toast.error"))
                .containsText("Ship is not available for ABC-1 (it is DEPLOYED)");
        assertThat(page.locator("#toasts .toast.error")).containsText("The board is up to date now.");
    }

    @Test
    void aMoveInFlightLocksTheButtonsThatWriteAndLeavesTheLookOnlyOnesClickable() throws Exception {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .lastActiveTimestamp(System.currentTimeMillis()).build());
        CountDownLatch sweeping = new CountDownLatch(1);
        when(commands.execute("ABC-1", TaskAction.SWEEP)).thenAnswer(invocation -> {
            sweeping.await(10, TimeUnit.SECONDS);
            return "sweep ABC-1: checks success";
        });

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Check review").setExact(true)).click();

        try {
            assertThat(page.getByRole(AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("Focus").setExact(true))).isEnabled();
            assertThat(page.getByRole(AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("Open IDE").setExact(true))).isEnabled();
            assertThat(page.getByRole(AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("Diff").setExact(true))).isEnabled();
            assertThat(page.getByRole(AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("Ship").setExact(true))).isDisabled();
            assertThat(page.getByRole(AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("Restart agent").setExact(true))).isDisabled();
        } finally {
            sweeping.countDown();
        }
    }

    @Test
    void aLookOnlyClickThatFinishesLeavesTheLockOfTheMoveStillRunning() throws Exception {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .lastActiveTimestamp(System.currentTimeMillis()).build());
        CountDownLatch sweeping = new CountDownLatch(1);
        when(commands.execute("ABC-1", TaskAction.SWEEP)).thenAnswer(invocation -> {
            sweeping.await(10, TimeUnit.SECONDS);
            return "sweep ABC-1: checks success";
        });
        when(commands.execute("ABC-1", TaskAction.DIFF)).thenReturn("Opened the diff for ABC-1.");

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Check review").setExact(true)).click();

        try {
            page.waitForResponse(
                    response -> response.url().endsWith("/api/tasks")
                            && "GET".equals(response.request().method()),
                    () -> page.getByRole(AriaRole.BUTTON,
                            new Page.GetByRoleOptions().setName("Diff").setExact(true)).click());

            assertThat(page.getByRole(AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("Ship").setExact(true))).isDisabled();
        } finally {
            sweeping.countDown();
        }
    }

    @Test
    void aTypedMoveIsRefusedWhileAnotherOneIsStillRunningOnTheSameTask() throws Exception {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .lastActiveTimestamp(System.currentTimeMillis()).build());
        CountDownLatch sweeping = new CountDownLatch(1);
        when(commands.execute("ABC-1", TaskAction.SWEEP)).thenAnswer(invocation -> {
            sweeping.await(10, TimeUnit.SECONDS);
            return "sweep ABC-1: checks success";
        });

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Check review").setExact(true)).click();

        try {
            page.keyboard().press("Control+k");
            page.locator("#ask").fill("ship a1");
            page.locator("#ask").press("Enter");

            assertThat(page.locator("#toasts .toast.error")).containsText("already running sweep");
            verify(commands, never()).execute("ABC-1", TaskAction.SHIP);
        } finally {
            sweeping.countDown();
        }
    }

    @Test
    void aTypedLaunchThatIsRefusedSaysSoInsteadOfSittingThere() {
        when(launcher.launchLine(any())).thenThrow(new IllegalArgumentException("No ticket ABC-9 anywhere"));

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.keyboard().press("Control+k");
        page.locator("#ask").fill("do ABC-9");
        page.locator("#ask").press("Enter");

        assertThat(page.locator("#toasts .toast.error")).containsText("No ticket ABC-9 anywhere");
    }

    @Test
    void aClickActsOnTheTaskWhoseCardItIsWhenAnotherTasksAliasReadsLikeItsId() {
        state.putTask("4", TaskState.builder("alpha", root.resolve("4-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("41").lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("41", TaskState.builder("alpha", root.resolve("41-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("42").lastActiveTimestamp(System.currentTimeMillis()).build());
        when(commands.execute("41", TaskAction.SHIP)).thenReturn("Shipped 41.");

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.locator("article", new Page.LocatorOptions().setHas(page.locator(".alias:text-is(\"42\")")))
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Ship").setExact(true))
                .click();

        assertThat(page.locator("#toasts .toast")).hasText("Shipped 41.");
        verify(commands, never()).execute("4", TaskAction.SHIP);
    }

    @Test
    void aRefusalIsAnnouncedAsAnAlertAndDismissedByAButton() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        when(commands.execute("ABC-1", TaskAction.SHIP)).thenThrow(new IllegalStateException("nothing to ship"));

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Ship").setExact(true)).click();
        page.getByRole(AriaRole.ALERT).getByRole(AriaRole.BUTTON).click();

        assertThat(page.locator("#toasts .toast")).hasCount(0);
    }
}
