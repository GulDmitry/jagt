package dev.jagt.orchestrator.board;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.RequestOptions;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.task.LaunchRequest;
import dev.jagt.orchestrator.task.Launched;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BoardPaletteAndLaunchTest extends BoardPageContext {

    @Test
    void thePaletteConfirmsALineItCanRunAsTyped() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.keyboard().press("Control+k");
        page.locator("#ask").fill("ship a1");

        assertThat(page.locator("#palette-state")).containsText("runs as typed");
    }

    @Test
    void thePaletteExecutesACommandItUnderstandsWithoutPayingForTheModel() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        when(commands.execute("ABC-1", TaskAction.SHIP)).thenReturn("Shipped ABC-1 — review request updated.");

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.keyboard().press("Control+k");
        page.locator("#ask").fill("ship a1");
        page.locator("#ask").press("Enter");

        assertThat(page.locator("#toasts .toast")).hasText("Shipped ABC-1 — review request updated.");
        verifyNoInteractions(naturalLanguage);
    }

    @Test
    void thePaletteSaysSoBeforeRunningWhenItCannotFindTheTask() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.keyboard().press("Control+k");
        page.locator("#ask").fill("ship nope");

        assertThat(page.locator("#palette-state")).hasClass(Pattern.compile("\\bbad\\b"));
        assertThat(page.locator("#palette-state")).containsText("no task");
    }

    @Test
    void thePaletteRunsARetiredVerbItselfInsteadOfPayingTheModelForIt() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host/x/merge_requests/7")
                .lastActiveTimestamp(System.currentTimeMillis()).build());
        when(commands.execute("ABC-1", TaskAction.SWEEP)).thenReturn("sweep ABC-1: checks success");

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.keyboard().press("Control+k");
        page.locator("#ask").fill("review a1");

        assertThat(page.locator("#palette-state")).containsText("runs as typed");

        page.locator("#ask").press("Enter");

        assertThat(page.locator("#toasts .toast")).hasText("sweep ABC-1: checks success");
        verifyNoInteractions(naturalLanguage);
    }

    @Test
    void freeTextIsInterpretedAndTheBoardLeadsWithHowItWasUnderstood() {
        when(naturalLanguage.interpret("send the widget work out for review"))
                .thenReturn("understood as `ship a1` — Shipped ABC-1.");

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.keyboard().press("Control+k");
        page.locator("#ask").fill("send the widget work out for review");
        page.locator("#ask").press("Enter");

        assertThat(page.locator("#toasts .toast")).hasText("understood as `ship a1` — Shipped ABC-1.");
    }

    @Test
    void startingATaskWithoutPickingAProjectLeavesTheChoiceToTheTicketRead() {
        when(launcher.launch(any())).thenReturn(Launched.created("ABC-9", "Started ABC-9."));

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.locator("#ref").fill("ABC-9");
        page.locator("#launch button[type=submit]").click();

        assertThat(page.locator("#toasts .toast")).hasText("Started ABC-9.");
        verify(launcher).launch(LaunchRequest.of("ABC-9").withStrategy("fresh"));
    }

    @Test
    void sendsTheExtraInstructionsTypedForTheAgent() {
        when(launcher.launch(any())).thenReturn(Launched.created("ABC-9", "Started ABC-9."));

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.locator("#ref").fill("ABC-9");
        page.locator("#notes").fill("start with the failing test");
        page.locator("#launch button[type=submit]").click();

        assertThat(page.locator("#toasts .toast")).hasText("Started ABC-9.");
        verify(launcher).launch(LaunchRequest.of("ABC-9").withStrategy("fresh")
                .withNotes("start with the failing test"));
    }

    @Test
    void keepsEverythingTypedWhenTheLaunchCreatedNoTask() {
        when(launcher.launch(any()))
                .thenReturn(Launched.refused("error: read failed: ABC-9 (cause in the log) — no task created"));

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.locator("#ref").fill("ABC-9");
        page.locator("#base-branch").fill("feature/parent");
        page.locator("#notes").fill("start with the failing test");
        page.locator("#launch button[type=submit]").click();

        assertThat(page.locator("#toasts .toast")).containsText("no task created");
        assertThat(page.locator("#ref")).hasValue("ABC-9");
        assertThat(page.locator("#base-branch")).hasValue("feature/parent");
        assertThat(page.locator("#notes")).hasValue("start with the failing test");
    }

    @Test
    void aTypedLaunchCarriesEveryModifierAndNotOnlyTheTicket() {
        when(launcher.launchLine(any())).thenReturn(Launched.created("ABC-9", "Started ABC-9."));

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.keyboard().press("Control+k");
        page.locator("#ask").fill("do ABC-9 plan keep the API stable");
        page.locator("#ask").press("Enter");

        assertThat(page.locator("#toasts .toast")).containsText("Started ABC-9.");
        verify(launcher).launchLine("ABC-9 plan keep the API stable");
    }

    @Test
    void aVerbTypedAloneHandsOverToThePartOfTheBoardItNames() {
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.keyboard().press("Control+k");
        page.locator("#ask").fill("do");
        page.locator("#ask").press("Enter");

        assertThat(page.locator("#ref")).isFocused();
        verifyNoInteractions(launcher, naturalLanguage);
    }

    @Test
    void aReviewRequestIsResumedFromAskWhichIsItsOnlyControl() {
        when(launcher.resume("https://host.example/mr/42"))
                .thenReturn(Launched.created("ABC-9", "Resumed ABC-9 on its existing branch"));

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.keyboard().press("Control+k");
        page.locator("#ask").fill("resume https://host.example/mr/42");
        page.locator("#ask").press("Enter");

        assertThat(page.locator("#toasts .toast")).hasText("Resumed ABC-9 on its existing branch");
        assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Resume").setExact(true)))
                .hasCount(0);
    }

    @Test
    void keepsTheTypedPaletteLineWhenTheLaunchItRanCreatedNoTask() {
        when(launcher.launchLine(any()))
                .thenReturn(Launched.refused("branch 'ABC-9' already exists in alpha (previous run)"));

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.keyboard().press("Control+k");
        page.locator("#ask").fill("do ABC-9");
        page.locator("#ask").press("Enter");

        assertThat(page.locator("#toasts .toast")).containsText("already exists in alpha");
        assertThat(page.locator("#ask")).hasValue("do ABC-9");
    }

    @Test
    void offersTheProjectsWithNonePickedSoAMultiSelectStartsEmpty() {
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("#project option")).hasCount(2);
        assertThat(page.locator("#project")).hasValues(new String[]{});
    }

    @Test
    void answersATasklessVerbItselfInsteadOfSendingItToTheModel() {
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.keyboard().press("Control+k");
        page.locator("#ask").fill("ship");
        page.locator("#ask").press("Enter");

        assertThat(page.locator("#palette-state")).containsText("ship needs a task");
        verifyNoInteractions(naturalLanguage);
    }

    @Test
    void startingATaskWithAPickedProjectSendsThatProject() {
        when(launcher.launch(any())).thenReturn(Launched.created("ABC-9", "Started ABC-9 in beta."));

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.locator("#ref").fill("ABC-9");
        page.locator("#project").selectOption("beta");
        page.locator("#launch button[type=submit]").click();

        assertThat(page.locator("#toasts .toast")).hasText("Started ABC-9 in beta.");
        verify(launcher).launch(LaunchRequest.of("ABC-9").withProject("beta").withStrategy("fresh"));
    }

    @Test
    void startingATaskWithAChosenBranchStrategySendsIt() {
        when(launcher.launch(any())).thenReturn(Launched.created("ABC-9", "Started ABC-9."));

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.locator("#ref").fill("ABC-9");
        page.locator("#strategy").selectOption("recreate");
        page.locator("#launch button[type=submit]").click();

        assertThat(page.locator("#toasts .toast")).hasText("Started ABC-9.");
        verify(launcher).launch(LaunchRequest.of("ABC-9").withStrategy("recreate"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"/api/tasks/say|{\"line\": \"hello\"}|'task'",
            "/api/tasks|{not json|not readable"})
    void aMalformedRequestIsRefusedInTheShapeEveryRefusalHas(String path, String body, String named) {
        APIResponse refused = session.request().post("http://localhost:" + port + path,
                RequestOptions.create().setHeader("Content-Type", "application/json")
                        .setHeader("Origin", "http://localhost:" + port).setData(body));

        org.assertj.core.api.Assertions.assertThat(refused.status()).isEqualTo(400);
        org.assertj.core.api.Assertions.assertThat(refused.text()).startsWith("{\"error\":").contains(named)
                .doesNotContain("timestamp");
    }

    @ParameterizedTest
    @CsvSource({"filter, filter", "ref, ticket", "base-branch, base branch", "notes, instructions",
            "ask, command", "say, tell this session", "project, projects", "strategy, when the branch exists"})
    void everyFieldIsNamedWithoutLeaningOnItsPlaceholder(String id, String named) {
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("#" + id)).hasAttribute("aria-label", Pattern.compile(named));
    }
}
