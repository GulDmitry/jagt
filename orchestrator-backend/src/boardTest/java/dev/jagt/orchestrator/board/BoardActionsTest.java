package dev.jagt.orchestrator.board;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BoardActionsTest extends BoardPageContext {

  @Autowired
  BoardActionsTest(BreakableJob breakable, StateService state) {
    super(breakable, state);
  }

  @Test
  void revertingAsksFirstAndSaysOnlyTheLastDeployComesOut() throws Exception {
    state.putTask(
        "ABC-1",
        alphaTask("ABC-1", TaskStatus.DEPLOYED)
            .mrUrl("https://host.example/mr/7")
            .build());

    Page page = openLiveBoard();
    CompletableFuture<String> asked = dismissedDialogMessage(page);
    button(page, "Revert").click();

    org.assertj.core.api.Assertions.assertThat(asked.get(5, TimeUnit.SECONDS))
        .startsWith("Revert ABC-1?")
        .contains("This pushes a revert commit to:")
        .contains("alpha → dev")
        .contains("Only the LAST deploy comes out");
    verifyNoInteractions(commands);
  }

  @Test
  void clickingAnActionRunsItAndShowsTheSentenceItAnswered() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    when(commands.execute("ABC-1", TaskAction.FOCUS, null))
        .thenReturn("Focused ABC-1 — its window is in front.");

    Page page = openLiveBoard();
    button(page, "Focus").click();

    assertThat(page.locator("#toasts .toast")).hasText("Focused ABC-1 — its window is in front.");
    verify(commands).execute("ABC-1", TaskAction.FOCUS, null);
  }

  @Test
  void closingTaskAsksBeforeAnythingRuns() throws Exception {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());

    Page page = openLiveBoard();
    CompletableFuture<String> asked = dismissedDialogMessage(page);
    button(page, "Done").click();

    org.assertj.core.api.Assertions.assertThat(asked.get(5, TimeUnit.SECONDS))
        .startsWith("Done ABC-1?");
    verifyNoInteractions(commands);
  }

  @Test
  void deployingNamesEveryRepositoryAndTheBranchItWouldBePushedTo() throws Exception {
    state.putTask(
        "ABC-1",
        TaskState.builder(
                List.of(
                    dev.jagt.orchestrator.task.TaskRepo.of(
                        "alpha", root.resolve("ABC-1-alpha").toString()),
                    new dev.jagt.orchestrator.task.TaskRepo(
                        "beta",
                        root.resolve("ABC-1-beta").toString(),
                        null,
                        "https://host.example/mr/8")),
                TaskStatus.REVIEW_PENDING)
            .alias("a1")
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = openLiveBoard();
    CompletableFuture<String> asked = dismissedDialogMessage(page);
    button(page, "Deploy").click();

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
    draftReplies("ABC-1", "## thread 1\nagreed, fixed\n");
    state.putTask(
        "ABC-1",
        alphaTask("ABC-1", TaskStatus.REVIEW_PENDING)
            .mrUrl("https://host.example/mr/9")
            .message("widget layout fixed")
            .build());

    Page page = openLiveBoard();
    CompletableFuture<String> asked = dismissedDialogMessage(page);
    button(page, "Deploy").click();

    org.assertj.core.api.Assertions.assertThat(asked.get(5, TimeUnit.SECONDS))
        .isEqualTo("Deploy ABC-1?\n\nThis merges and pushes:\nalpha → dev");
    verifyNoInteractions(commands);
  }

  @Test
  void refusedActionAlsoSaysTheBoardHasCaughtUp() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    when(commands.execute("ABC-1", TaskAction.SHIP, null))
        .thenThrow(
            new Refusal(
                Refusal.Code.ACTION_NOT_AVAILABLE,
                "Ship is not available for ABC-1 (it is DEPLOYED)"));

    Page page = openLiveBoard();
    button(page, "Ship").click();

    assertThat(page.locator("#toasts .toast.error"))
        .containsText("Ship is not available for ABC-1 (it is DEPLOYED)");
    assertThat(page.locator("#toasts .toast.error")).containsText("The board is up to date now.");
  }

  @Test
  void moveInFlightLocksTheButtonsThatWriteAndLeavesTheLookOnlyOnesClickable() throws Exception {
    CountDownLatch sweeping = startSweepingTheFirstTask();

    Page page = openLiveBoard();
    button(page, "Check review").click();

    try {
      assertThat(button(page, "Focus")).isEnabled();
      assertThat(button(page, "Open IDE")).isEnabled();
      assertThat(button(page, "Diff")).isEnabled();
      assertThat(button(page, "Ship")).isDisabled();
      assertThat(button(page, "Restart agent")).isDisabled();
    } finally {
      sweeping.countDown();
    }
  }

  @Test
  void lookOnlyClickThatFinishesLeavesTheLockOfTheMoveStillRunning() throws Exception {
    CountDownLatch sweeping = startSweepingTheFirstTask();
    when(commands.execute("ABC-1", TaskAction.DIFF, null)).thenReturn("Opened the diff for ABC-1.");

    Page page = openLiveBoard();
    button(page, "Check review").click();

    try {
      page.waitForResponse(
          response ->
              response.url().endsWith("/api/tasks") && "GET".equals(response.request().method()),
          () -> button(page, "Diff").click());

      assertThat(button(page, "Ship")).isDisabled();
    } finally {
      sweeping.countDown();
    }
  }

  @Test
  void typedMoveIsRefusedWhileAnotherOneIsStillRunningOnTheSameTask() throws Exception {
    CountDownLatch sweeping = startSweepingTheFirstTask();

    Page page = openLiveBoard();
    button(page, "Check review").click();

    try {
      ask(page, "ship a1");

      assertThat(page.locator("#toasts .toast.error")).containsText("already running sweep");
      verify(commands, never()).execute("ABC-1", TaskAction.SHIP, null);
    } finally {
      sweeping.countDown();
    }
  }

  @Test
  void typedLaunchThatIsRefusedSaysSoInsteadOfSittingThere() {
    when(launcher.launchLine(any()))
        .thenThrow(new IllegalArgumentException("No ticket ABC-9 anywhere"));

    Page page = openLiveBoard();
    ask(page, "do ABC-9");

    assertThat(page.locator("#toasts .toast.error")).containsText("No ticket ABC-9 anywhere");
  }

  @Test
  void clickActsOnTheTaskWhoseCardItIsWhenAnotherTasksAliasReadsLikeItsId() {
    state.putTask(
        "4",
        TaskState.builder("alpha", root.resolve("4-alpha").toString(), TaskStatus.IN_PROGRESS)
            .alias("41")
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());
    state.putTask(
        "41",
        TaskState.builder("alpha", root.resolve("41-alpha").toString(), TaskStatus.IN_PROGRESS)
            .alias("42")
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());
    when(commands.execute("41", TaskAction.SHIP, null)).thenReturn("Shipped 41.");

    Page page = openLiveBoard();
    page.locator(
            "#tasks .row", new Page.LocatorOptions().setHas(page.locator(".alias:text-is(\"42\")")))
        .locator(".pick")
        .click();
    page.locator("#pane article")
        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Ship").setExact(true))
        .click();

    assertThat(page.locator("#toasts .toast")).hasText("Shipped 41.");
    verify(commands, never()).execute("4", TaskAction.SHIP, null);
  }

  @Test
  void refusalIsAnnouncedAsAnAlertAndDismissedByButton() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    when(commands.execute("ABC-1", TaskAction.SHIP, null))
        .thenThrow(new IllegalStateException("nothing to ship"));

    Page page = openLiveBoard();
    button(page, "Ship").click();
    page.getByRole(AriaRole.ALERT).getByRole(AriaRole.BUTTON).click();

    assertThat(page.locator("#toasts .toast")).hasCount(0);
  }
}
