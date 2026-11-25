package dev.jagt.orchestrator.board;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.mockito.Mockito.when;

import com.microsoft.playwright.Page;
import dev.jagt.orchestrator.flow.Pressed;
import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.job.Jobs;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BoardInteractionLogTest extends BoardPageContext {

  final Jobs jobs;

  @Autowired
  BoardInteractionLogTest(BreakableJob breakable, StateService state, Jobs jobs) {
    super(breakable, state);
    this.jobs = jobs;
  }

  @Test
  void pressIsLoggedByItsVerbAndItsTasksStatusAlone() throws Exception {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    when(commands.press("ABC-1", TaskAction.FOCUS, null))
        .thenReturn(new Pressed("Focused ABC-1.", Outcome.Kind.OK));
    Page page = openLiveBoard();

    page.waitForResponse("**/api/interactions", () -> button(page, "Focus").click());

    org.assertj.core.api.Assertions.assertThat(
            Files.readString(root.resolve("records/interactions.jsonl")))
        .contains("\"control\":\"action:focus\",\"status\":\"IN_PROGRESS\"")
        .doesNotContain("ABC-1")
        .doesNotContain("a1");
  }

  @Test
  void pickingRowIsLoggedWithTheRowsStatus() throws Exception {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    Page page = openBoard();
    assertThat(page.locator("#tasks .row")).hasCount(1);

    page.waitForResponse("**/api/interactions", () -> page.locator("#tasks .row .pick").click());

    org.assertj.core.api.Assertions.assertThat(
            Files.readString(root.resolve("records/interactions.jsonl")))
        .contains("\"control\":\"select\",\"status\":\"IN_PROGRESS\"")
        .doesNotContain("ABC-1");
  }

  @Test
  void keyIsLoggedByItsLetterAndTheOpenTasksStatus() throws Exception {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    Page page = openBoard();
    assertThat(page.locator("#tasks .row")).hasCount(1);

    page.waitForResponse("**/api/interactions", () -> page.keyboard().press("j"));

    org.assertj.core.api.Assertions.assertThat(
            Files.readString(root.resolve("records/interactions.jsonl")))
        .contains("\"control\":\"key:j\",\"status\":\"IN_PROGRESS\"");
  }

  @Test
  void taskTurningRequiredIsLoggedOnceByItsStatusAloneWhateverBoardsAreOpen() throws Exception {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    Page page = openBoard();
    Page second = session.newPage();
    second.navigate("http://localhost:" + port + "/");
    assertThat(page.locator("#tasks .row")).hasCount(1);
    assertThat(second.locator("#tasks .row")).hasCount(1);

    page.waitForResponse(
        "**/api/interactions",
        () -> {
          state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.REVIEWED).build());
          state.putTask("ABC-2", alphaTask("ABC-2", TaskStatus.IN_PROGRESS).build());
        });
    second.waitForResponse("**/api/interactions", () -> second.keyboard().press("j"));

    String log = Files.readString(root.resolve("records/interactions.jsonl"));
    org.assertj.core.api.Assertions.assertThat(log)
        .contains("\"control\":\"shown:required\",\"status\":\"REVIEWED\"")
        .doesNotContain("ABC-1")
        .doesNotContain("a1");
    org.assertj.core.api.Assertions.assertThat(log.split("shown:required", -1)).hasSize(2);
  }

  @Test
  void theFailedJobsChipAppearingIsLoggedWithoutStatus() throws Exception {
    breakable.breakIt();
    Page page = openLiveBoard();

    page.waitForResponse(
        "**/api/interactions",
        () -> {
          jobs.runNow("breakable");
          page.waitForCondition(() -> jobs.summary(System.currentTimeMillis()).failing() > 0);
          state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
        });

    org.assertj.core.api.Assertions.assertThat(
            Files.readString(root.resolve("records/interactions.jsonl")))
        .contains("\"control\":\"shown:jobs-failed\",\"status\":null");
  }
}
