package dev.jagt.orchestrator.board;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.mockito.Mockito.when;

class BoardInteractionLogTest extends BoardPageContext {

    @Test
    void aPressIsLoggedByItsVerbAndItsTasksStatusAlone() throws Exception {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        when(commands.execute("ABC-1", TaskAction.FOCUS)).thenReturn("Focused ABC-1.");
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

        page.waitForResponse("**/api/interactions", () -> page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Focus").setExact(true)).click());

        org.assertj.core.api.Assertions.assertThat(Files.readString(root.resolve("interactions.jsonl")))
                .contains("\"control\":\"action:focus\",\"status\":\"IN_PROGRESS\"")
                .doesNotContain("ABC-1").doesNotContain("a1");
    }

    @Test
    void aKeyIsLoggedByItsLetterAndTheOpenTasksStatus() throws Exception {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#tasks .row")).hasCount(1);

        page.waitForResponse("**/api/interactions", () -> page.keyboard().press("j"));

        org.assertj.core.api.Assertions.assertThat(Files.readString(root.resolve("interactions.jsonl")))
                .contains("\"control\":\"key:j\",\"status\":\"IN_PROGRESS\"");
    }
}
