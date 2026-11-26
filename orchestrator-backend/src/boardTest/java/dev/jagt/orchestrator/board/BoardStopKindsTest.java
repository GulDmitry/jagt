package dev.jagt.orchestrator.board;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Page;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.master.Master;
import dev.jagt.orchestrator.task.stop.TaskStop;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BoardStopKindsTest extends BoardPageContext {

  @Autowired
  BoardStopKindsTest(BreakableJob breakable, StateService state) {
    super(breakable, state);
  }

  @Test
  void pausedCardSaysYouPausedItWithItsOwnMarkAndNeverJagtStopped() {
    state.putTask(
        "ABC-42",
        alphaTask("ABC-42", TaskStatus.IN_PROGRESS)
            .stop(new TaskStop(TaskStop.Trigger.PAUSE, 1_700_000_000_000L, "you paused it"))
            .build());

    Page page = openLiveBoard();

    assertThat(page.locator("article .badge.optional.paused")).hasText("you can lift the pause");
    assertThat(page.locator("article .badge"))
        .hasAttribute("data-tip", Pattern.compile("you paused this task; lift your pause"));
    assertThat(page.locator("article .status.paused")).containsText("‖ paused");
    assertThat(page.locator("article .status.stalled")).hasCount(0);
    assertThat(page.locator("article")).not().containsText(Pattern.compile("jagt stopped"));
    assertThat(page.locator("#waiting")).isHidden();
  }

  @Test
  void jagtStoppedCardAsksToLiftTheStopWithTheStopMarkAndItsRing() {
    state.putTask(
        "ABC-42",
        alphaTask("ABC-42", TaskStatus.CI_POLLING)
            .mrUrl("https://host.example/mr/7")
            .stop(
                new TaskStop(
                    TaskStop.Trigger.SHIPS,
                    1_700_000_000_000L,
                    "a ship asked past 3 without a new note"))
            .build());

    Page page = openLiveBoard();

    assertThat(page.locator("article .badge.required.stop"))
        .hasText("lift the stop — ships with no new note");
    assertThat(page.locator("article .mr-age.stalled")).hasCount(1);
  }

  @Test
  void brokenStopWearsTheProblemMarkWithoutTheRing() {
    state.putTask(
        "ABC-42",
        alphaTask("ABC-42", TaskStatus.CI_POLLING)
            .mrUrl("https://host.example/mr/7")
            .stop(new TaskStop(TaskStop.Trigger.CHECKS, 1_700_000_000_000L, "no pipeline read"))
            .build());

    Page page = openLiveBoard();

    assertThat(page.locator("article .badge.problem")).hasCount(1);
    assertThat(page.locator("article .badge.stop")).hasCount(0);
    assertThat(page.locator("article .mr-age.stalled")).hasCount(0);
  }

  @Test
  void failedRunAndPlanWaitingForYouWearDifferentMarksEachWithItsLegendRow() {
    state.putTask(
        "ABC-42",
        alphaTask("ABC-42", TaskStatus.CI_FAILED).mrUrl("https://host.example/mr/7").build());
    state.putTask(
        "ABC-43",
        alphaTask("ABC-43", TaskStatus.PLAN_PENDING)
            .master(Master.defaults().withLeft("approve the plan"))
            .build());

    Page page = openLiveBoard();
    page.locator("#reports-toggle").click();
    page.locator("#show-help").click();

    assertThat(page.locator("#tasks button[data-select='ABC-42'] .badge.problem")).hasCount(1);
    assertThat(page.locator("#tasks button[data-select='ABC-43'] .badge.problem")).hasCount(0);
    assertThat(page.locator("#tasks button[data-select='ABC-43'] .badge.required")).hasCount(1);
    assertThat(page.locator("#report-section .legend .badge.problem")).hasCount(1);
    assertThat(page.locator("#report-section .legend .badge.required.stop")).hasCount(1);
    assertThat(page.locator("#report-section .legend .badge.optional.paused")).hasCount(1);
    assertThat(page.locator("#report-section .legend .status.paused")).hasCount(1);
  }
}
