package dev.jagt.orchestrator.board;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static dev.jagt.orchestrator.board.TestReplyDraftBuilder.replyDraft;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.LocatorAssertions;
import dev.jagt.orchestrator.flow.Pressed;
import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.job.Jobs;
import dev.jagt.orchestrator.port.terminal.WindowStream;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.plan.PlanBaseline;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.launch.Launched;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import dev.jagt.orchestrator.task.turn.StatusChange;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

class BoardPopupsAndTabsTest extends BoardPageContext {

  final Jobs jobs;

  final PlanBaseline planBaseline;

  @Autowired
  BoardPopupsAndTabsTest(
      BreakableJob breakable, StateService state, Jobs jobs, PlanBaseline planBaseline) {
    super(breakable, state);
    this.jobs = jobs;
    this.planBaseline = planBaseline;
  }

  @Test
  void theReportsMenuOpensReportAndClosesOnEscape() {
    Page page = openLiveBoard();

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
  void theEnterKeyOpensTheNewTaskFormAtItsTicket() {
    Page page = openLiveBoard();

    page.keyboard().press("t");

    assertThat(page.locator("#launch-dialog")).isVisible();
    assertThat(page.locator("#ref")).isFocused();
  }

  @Test
  void addingProjectReadsThePathAndRefusesOneThatIsNotRepository() throws Exception {
    final Path folder = Files.createDirectories(root.resolve("notes"));
    final String before = Files.readString(root.resolve("jagt.yml"));
    Page page = openBoard();
    page.locator("#new-task").click();
    page.locator("#add-project").click();

    page.locator("#project-path").fill(folder.toString());
    assertThat(page.locator("#project-checks")).containsText("not a git repository");
    page.locator("#project-key").fill("notes");
    page.locator("#project-base").fill("origin/main");
    page.locator("#project-form button[type=submit]").click();

    assertThat(page.locator("#toasts .toast.error")).containsText("not a git repository");
    org.assertj.core.api.Assertions.assertThat(Files.readString(root.resolve("jagt.yml")))
        .isEqualTo(before);
  }

  @Test
  void commaSeparatedLabelsAndHumanPathsTypedInTheProjectFormAreSentAsLists() throws Exception {
    final Path folder = Files.createDirectories(root.resolve("notes"));
    Page page = openBoard();
    page.locator("#new-task").click();
    page.locator("#add-project").click();
    page.locator("#project-path").fill(folder.toString());
    page.locator("#project-key").fill("notes");
    page.locator("#project-base").fill("origin/main");
    page.locator("#project-labels").fill("bill, pay");
    page.locator("#project-human").fill("**/auth/**");

    com.microsoft.playwright.Request sent =
        page.waitForRequest(
            request -> request.url().endsWith("/api/projects") && "POST".equals(request.method()),
            () -> page.locator("#project-form button[type=submit]").click());

    org.assertj.core.api.Assertions.assertThat(sent.postData())
        .contains("\"labels\":[\"bill\",\"pay\"]")
        .contains("\"humanPaths\":[\"**/auth/**\"]");
  }

  @Test
  void typingFolderPrefixInTheProjectPathOffersTheFoldersUnderIt() throws Exception {
    Files.createDirectories(root.resolve("billing/.git"));
    Files.createDirectories(root.resolve("billboard"));
    Files.createDirectories(root.resolve("notes"));
    Page page = openBoard();
    page.locator("#new-task").click();
    page.locator("#add-project").click();

    page.locator("#project-path").fill(root + "/bil");

    assertThat(page.locator("#project-folders option")).hasCount(2);
    assertThat(page.locator("#project-folders option").first())
        .hasAttribute("value", root + "/billing/");
  }

  @Test
  void theProjectVerbTypedAloneOpensTheAddProjectForm() {
    Page page = openLiveBoard();

    ask(page, "project");

    assertThat(page.locator("#project-dialog")).isVisible();
    assertThat(page.locator("#project-path")).isFocused();
  }

  @Test
  void theProjectVerbWithEditAndKeyOpensTheFormFilledWithThatProject() {
    Page page = openLiveBoard();

    ask(page, "project edit beta");

    assertThat(page.locator("#project-dialog")).isVisible();
    assertThat(page.locator("#project-title")).hasText("Edit beta");
    assertThat(page.locator("#project-key")).hasValue("beta");
    assertThat(page.locator("#project-path")).hasValue(root.resolve("beta").toString());
    assertThat(page.locator("#project-deploy")).hasValue("dev");
    assertThat(page.locator("#project-form button[type=submit]")).hasText("Save to jagt.yml");
  }

  @Test
  void failedJobOpensFromTheHeaderAsTableWhoseRowRunsItNow() {
    breakable.breakIt();
    Page page = openLiveBoard();
    jobs.runNow("breakable");
    page.waitForCondition(breakable::failing);
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());

    page.locator("#jobs-pulse").click();
    assertThat(page.locator("table.jobs tr:has(button[data-job=breakable])"))
        .hasClass(Pattern.compile("failed"));
    page.locator("table.jobs button[data-job=breakable]").click();

    assertThat(page.locator("#toasts .toast").last())
        .containsText("breakable runs within a second");
  }

  @Test
  void theSessionTabStreamsTheSessionsTerminalAndSendsLineToThatSession() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    WindowStream.View view = mock(WindowStream.View.class);
    when(streams.ofTask(eq("ABC-1"), anyInt(), anyInt(), any()))
        .thenAnswer(call -> {
          call.<WindowStream.Sink>getArgument(3).output("✗ two tests fail".getBytes(UTF_8));
          return Optional.of(view);
        });
    when(sessions.say("ABC-1", "mock the storage")).thenReturn("Said to the agent.");
    Page page = openBoard();
    assertThat(page.locator("#pane article .alias")).hasText("a1");

    page.locator("#pane .tabs button[data-tab=session]").click();
    assertThat(page.locator("#pane .session .terminal")).containsText("✗ two tests fail");
    page.locator("#pane .say input").fill("mock the storage");
    page.locator("#pane .say input").press("Enter");

    assertThat(page.locator("#toasts .toast")).hasText("Said to the agent.");
    verify(sessions).say("ABC-1", "mock the storage");
  }

  @Test
  void whatIsTypedInTheSessionTabsTerminalReachesTheSession() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    WindowStream.View view = mock(WindowStream.View.class);
    when(streams.ofTask(eq("ABC-1"), anyInt(), anyInt(), any())).thenReturn(Optional.of(view));
    Page page = openBoard();
    page.locator("#pane .tabs button[data-tab=session]").click();

    page.locator("#pane .session .terminal textarea").focus();
    page.keyboard().type("l");

    verify(view, timeout(5000)).type("l");
  }

  @Test
  void leavingTheSessionTabClosesTheLiveView() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    WindowStream.View view = mock(WindowStream.View.class);
    doReturn(Optional.of(view)).when(streams).ofTask(eq("ABC-1"), anyInt(), anyInt(), any());
    Page page = openBoard();
    page.locator("#pane .tabs button[data-tab=session]").click();
    assertThat(page.locator("#pane .session .terminal")).isVisible();
    verify(view, never()).close();

    page.locator("#pane .tabs button[data-tab=overview]").click();

    verify(view, timeout(5000)).close();
  }

  @Test
  void theSessionTabSaysNoWindowHoldsTheSessionWhereTheStreamFindsNone() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    when(streams.ofTask(eq("ABC-1"), anyInt(), anyInt(), any())).thenReturn(Optional.empty());
    Page page = openBoard();

    page.locator("#pane .tabs button[data-tab=session]").click();

    assertThat(page.locator("#pane .session")).containsText("no window holds this session");
  }

  @Test
  void theSessionTabStreamsTheLoginWindowAndSaysToIt() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    when(windows.open()).thenReturn(List.of("login"));
    WindowStream.View view = mock(WindowStream.View.class);
    when(streams.ofWindow(eq("login"), anyInt(), anyInt(), any()))
        .thenAnswer(call -> {
          call.<WindowStream.Sink>getArgument(3)
              .output("code ABCD-1234 at https://host.example.invalid/device".getBytes(UTF_8));
          return Optional.of(view);
        });
    when(windows.say("login", "y")).thenReturn("Said to login.");
    Page page = openBoard();

    page.locator("#pane .tabs button[data-tab=session]").click();
    assertThat(page.locator("#pane .session")).containsText("no window holds this session");
    page.locator("#pane .session button[data-window=login]").click();
    assertThat(page.locator("#pane .session .terminal"))
        .containsText("code ABCD-1234 at https://host.example.invalid/device");
    page.locator("#pane .say input").fill("y");
    page.locator("#pane .say input").press("Enter");

    assertThat(page.locator("#toasts .toast")).hasText("Said to login.");
    verify(windows).say("login", "y");
  }

  @Test
  void theSessionTabRunsTheLineThroughTheRunVerbInsteadOfSayingIt() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    when(sessions.screen("ABC-1")).thenReturn(Optional.of("compiling"));
    when(commands.executeLine("ABC-1", TaskAction.RUN, "ls -la"))
        .thenReturn("Run in the agent's window.");
    Page page = openBoard();

    page.locator("#pane .tabs button[data-tab=session]").click();
    page.locator("#pane .say input").fill("ls -la");
    page.locator("#pane .say button[data-run]").click();

    assertThat(page.locator("#toasts .toast")).hasText("Run in the agent's window.");
    verify(commands).executeLine("ABC-1", TaskAction.RUN, "ls -la");
    verify(sessions, org.mockito.Mockito.never()).say("ABC-1", "ls -la");
  }

  @ParameterizedTest
  @ValueSource(strings = {"overview", "artifacts", "diff"})
  void theMessageLineUnderAnyTabTellsThatTasksSessionAndClears(String tab) {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    when(sessions.screen("ABC-1")).thenReturn(Optional.of("compiling"));
    when(sessions.say("ABC-1", "use the fake clock")).thenReturn("Said to the agent.");
    Page page = openBoard();

    page.locator("#pane .tabs button[data-tab=" + tab + "]").click();
    page.locator("#pane .dock .say input").fill("use the fake clock");
    page.locator("#pane .dock .say input").press("Enter");

    assertThat(page.locator("#toasts .toast")).hasText("Said to the agent.");
    assertThat(page.locator("#pane .dock .say input")).hasValue("");
    verify(sessions).say("ABC-1", "use the fake clock");
  }

  @Test
  void theWorkingSessionsLastNonBlankLinePeeksAboveTheMessageLine() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    when(sessions.screen("ABC-1")).thenReturn(Optional.of("compiling\n> running tests\n\n  \n"));
    Page page = openBoard();

    assertThat(page.locator("#pane .dock .peek")).hasText("> running tests");
  }

  @Test
  void theOpenTasksPeekReadsItsScreenAgainEveryFiveSeconds() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    when(sessions.screen("ABC-1")).thenReturn(Optional.of("compiling"));
    Page page = session.newPage();
    page.clock().install();
    page.navigate("http://localhost:" + port + "/");
    assertThat(page.locator("#pane .dock .peek")).hasText("compiling");
    when(sessions.screen("ABC-1")).thenReturn(Optional.of("tests green"));

    page.clock().runFor(5_000);

    assertThat(page.locator("#pane .dock .peek")).hasText("tests green");
  }

  @Test
  void theSessionTabReadsItsWindowsAgainEveryFiveSeconds() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    when(sessions.screen("ABC-1")).thenReturn(Optional.of("compiling"));
    Page page = session.newPage();
    page.clock().install();
    page.navigate("http://localhost:" + port + "/");
    page.locator("#pane .tabs button[data-tab=session]").click();
    assertThat(page.locator("#pane .session")).isVisible();

    page.waitForRequest("**/api/windows", () -> page.clock().runFor(5_000));
  }

  @Test
  void theMessageLineIsDisabledAndSaysWhyWhenNoWindowHoldsTheSession() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    when(sessions.screen("ABC-1")).thenReturn(Optional.empty());
    Page page = openBoard();

    assertThat(page.locator("#pane .dock .say input")).isDisabled();
    assertThat(page.locator("#pane .dock .say input"))
        .hasAttribute("placeholder", "no window holds this session, so there is nobody to tell");
    assertThat(page.locator("#pane .dock .say button").first()).isDisabled();
  }

  @Test
  void theSessionTabHasOneMessageLineOnly() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    when(sessions.screen("ABC-1")).thenReturn(Optional.of("compiling"));
    Page page = openBoard();

    page.locator("#pane .tabs button[data-tab=session]").click();

    assertThat(page.locator("#pane .say")).hasCount(1);
    assertThat(page.locator("#pane .session .say")).hasCount(0);
  }

  @Test
  void theReviewRoundTabReadsTheDraftedRepliesOrSaysThereAreNone() throws Exception {
    replyDraft(root).withKey("ABC-2").withText("## thread 1\nFIXED - Renamed it.\n").build();
    putReviewTask("ABC-2");
    state.putTask("ABC-3", alphaTask("ABC-3", TaskStatus.IN_PROGRESS).build());
    Page page = openBoard();
    page.locator("#tasks .row:has-text(\"a2\") .pick").click();

    page.locator("#pane .tabs button[data-tab=round]").click();
    assertThat(page.locator("#pane .round")).containsText("Renamed it.");
    assertThat(page.locator("#pane .round .verdict.ok")).hasText("FIXED");
    page.locator("#tasks .row:has-text(\"a3\") .pick").click();

    assertThat(page.locator("#pane .round")).containsText("Nothing is drafted for this round.");
  }

  @Test
  void theDraftedReviewTabIsMarkedUntilOpenedAndAgainWhenTheRepliesAreWrittenAnew()
      throws Exception {
    Path worktree = Files.createDirectories(root.resolve("ABC-5-alpha"));
    Path replies = worktree.resolve("review_replies.md");
    Files.writeString(replies, "## thread 1\nFIXED - Renamed it.\n");
    putReviewTask("ABC-5");
    Page page = openBoard();
    assertThat(page.locator("#pane .tabs button[data-tab=round] .badge.required")).hasText("new");

    page.locator("#pane .tabs button[data-tab=round]").click();
    page.locator("#pane .tabs button[data-tab=overview]").click();
    assertThat(page.locator("#pane .tabs button[data-tab=round] .badge")).hasCount(0);
    Files.writeString(replies, "## thread 1\nFIXED - Renamed it again.\n");
    Files.setLastModifiedTime(
        replies, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 60_000));
    state.putTask(
        "ABC-5",
        state
            .task("ABC-5")
            .orElseThrow()
            .withStatus(TaskStatus.REVIEW_PENDING, "answered again", System.currentTimeMillis()));

    assertThat(page.locator("#pane .tabs button[data-tab=round] .badge.required")).hasText("new");
  }

  @Test
  void theArtifactsTabListsTheDocumentsThePresentWorktreeHoldsAndShowsTheOnePickedRendered()
      throws Exception {
    Path worktree = Files.createDirectories(root.resolve("ABC-4-alpha"));
    Files.writeString(worktree.resolve("plan.md"), "# The plan\n");
    Files.writeString(
        worktree.resolve("task_visual.md"), "# The sketch\n\n| a | b |\n|---|---|\n| 1 | 2 |\n");
    state.putTask("ABC-4", alphaTask("ABC-4", TaskStatus.IN_PROGRESS).build());
    Page page = openBoard();

    page.locator("#pane .tabs button[data-tab=artifacts]").click();
    assertThat(page.locator("#pane .doc-names button[data-file=\"task_notes.md\"]")).hasCount(0);
    assertThat(page.locator("#pane .doc h1")).hasText("The plan");
    page.locator("#pane .doc-names button[data-file=task_visual\\.md]").click();

    assertThat(page.locator("#pane .doc h1")).hasText("The sketch");
    assertThat(page.locator("#pane .doc table td").first()).hasText("1");
  }

  @Test
  void theArtifactsTabMarksPlanLineChangedSinceItsApprovalAndStrikesOneRemoved() throws Exception {
    Path worktree = Files.createDirectories(root.resolve("ABC-7-alpha"));
    state.putTask("ABC-7", alphaTask("ABC-7", TaskStatus.PLAN_PENDING).build());
    Files.writeString(
        worktree.resolve("plan.md"), "# The plan\n\nRetry the upload once\n\nShip it\n");
    planBaseline.keep("ABC-7");
    Files.writeString(
        worktree.resolve("plan.md"),
        "# The plan\n\nRetry the upload twice\n\nShip it\n\nAnnounce it\n");
    Page page = openBoard();

    page.locator("#pane .tabs button[data-tab=artifacts]").click();

    assertThat(page.locator("#pane .doc .changed"))
        .hasText(new String[] {"Retry the upload twice", "Announce it"});
    assertThat(page.locator("#pane .doc .removed")).hasText("Retry the upload once");
    assertThat(page.locator("#pane .doc .removed")).hasCSS("text-decoration-line", "line-through");
    assertThat(page.locator("#pane .doc p:not(.changed):not(.removed)")).hasText("Ship it");
  }

  @Test
  void mermaidFenceInAnOpenDocumentIsDrawnAsDiagram() throws Exception {
    Path worktree = Files.createDirectories(root.resolve("ABC-5-alpha"));
    Files.writeString(
        worktree.resolve("task_visual.md"), "```mermaid\nsequenceDiagram\nA->>B: go\n```\n");
    state.putTask("ABC-5", alphaTask("ABC-5", TaskStatus.IN_PROGRESS).build());
    Page page = openBoard();

    page.locator("#pane .tabs button[data-tab=artifacts]").click();

    assertThat(page.locator("#pane .doc .mermaid svg")).isVisible();
  }

  @Test
  void mermaidFenceThatCannotBeParsedReportsTheFailureInAnErrorToast() throws Exception {
    Path worktree = Files.createDirectories(root.resolve("ABC-7-alpha"));
    Files.writeString(worktree.resolve("task_visual.md"), "```mermaid\nnot a diagram\n```\n");
    state.putTask("ABC-7", alphaTask("ABC-7", TaskStatus.IN_PROGRESS).build());
    Page page = openBoard();

    page.locator("#pane .tabs button[data-tab=artifacts]").click();

    assertThat(page.locator("#toasts .toast.error")).containsText("could not draw the diagram");
  }

  @Test
  void theOpenTaskTabsStandInTheOrderTheyAreUsed() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    Page page = openBoard();

    assertThat(page.locator("#pane .tabs button"))
        .hasText(
            new String[] {"Overview", "Drafted review", "Artifacts", "Code", "Diff", "Session"});
  }

  @Test
  void theOpenTaskViewsReadAsOneRowOfTabsWithTheSelectedOneMarked() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    Page page = openBoard();

    assertThat(page.locator("#pane .tabs")).hasCSS("flex-direction", "row");
    assertThat(page.locator("#pane .tabs button[aria-selected=true]")).hasCount(1);
    assertThat(page.locator("#pane .tabs button[aria-selected=true]"))
        .hasCSS("border-bottom-width", "2px");
  }

  @Test
  void codeTabShowsOneFileOfTheWorktreeHighlighted() throws Exception {
    Path worktree = Files.createDirectories(root.resolve("ABC-6-alpha"));
    Files.writeString(worktree.resolve("A.java"), "public class A {}\n");
    new ProcessBuilder("git", "init", "-q").directory(worktree.toFile()).start().waitFor();
    state.putTask("ABC-6", alphaTask("ABC-6", TaskStatus.IN_PROGRESS).build());
    Page page = openBoard();

    page.locator("#pane .tabs button[data-tab=code]").click();

    assertThat(page.locator("#pane .code-view .cm-content")).containsText("public class A");
    assertThat(page.locator("#pane .code-view .tok-keyword").first()).isVisible();
  }

  @Test
  void codeTabListsTheChangedFilesWithCountsAndOpensOneBesideItsSource() throws Exception {
    Path worktree = Files.createDirectories(root.resolve("ABC-1-alpha"));
    Files.writeString(worktree.resolve("A.java"), "keep\nnew\nkeep\n");
    Files.writeString(worktree.resolve("B.java"), "one\ntwo\n");
    new ProcessBuilder("git", "init", "-q").directory(worktree.toFile()).start().waitFor();
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    Page page = session.newPage();
    page.route(
        "**/api/tasks/diff*",
        route ->
            route.fulfill(
                new com.microsoft.playwright.Route.FulfillOptions()
                    .setContentType("application/json")
                    .setBody(
                        "[{\"project\":\"alpha\",\"base\":\"origin/main\",\"cut\":false,\"diff\":"
                            + "\"diff --git a/A.java b/A.java\\n--- a/A.java\\n+++ b/A.java\\n"
                            + "@@ -1,3 +1,3 @@\\n keep\\n-old\\n+new\\n keep\\n"
                            + "diff --git a/B.java b/B.java\\n--- a/B.java\\n+++ b/B.java\\n"
                            + "@@ -1,0 +1,2 @@\\n+one\\n+two\\n\"}]")));
    page.navigate("http://localhost:" + port + "/");
    page.locator("#pane .tabs button[data-tab=code]").click();

    page.locator("#pane .code-modes button", new Page.LocatorOptions().setHasText("Changes"))
        .click();

    assertThat(page.locator("#pane .code-change"))
        .hasText(new String[] {"A.java +1 −1", "B.java +2 −0"});
    page.locator("#pane .code-change").first().click();
    assertThat(page.locator("#pane .code-view .cm-mergeView"))
        .isVisible(new LocatorAssertions.IsVisibleOptions().setTimeout(20_000));
    assertThat(page.locator("#pane .code-view .cm-mergeView .cm-content").first())
        .containsText("old");
    assertThat(page.locator("#pane .code-view .cm-mergeView .cm-content").last())
        .containsText("new");
  }

  @Test
  void codeVerbStillOpensTheViewerWhenAnEditorIsConfigured() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    when(commands.press("ABC-1", TaskAction.CODE, null))
        .thenReturn(new Pressed("Opened /wt as a project in the editor", Outcome.Kind.OK));
    Page page = openBoard();

    page.locator("#pane button[data-action=code]").click();

    assertThat(page.locator("#pane .tabs button[data-tab=code]"))
        .hasAttribute("aria-selected", "true");
    assertThat(page.locator("#toasts .toast")).hasText("Opened /wt as a project in the editor");
  }

  @Test
  void codeTabNamesTheCauseWhenTheViewerDoesNotLoad() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    Page page = session.newPage();
    page.route("**/viewer/Code.js", route -> route.abort());
    page.navigate("http://localhost:" + port + "/");

    page.locator("#pane .tabs button[data-tab=code]").click();

    assertThat(page.locator("#pane .detail.problem")).containsText("could not read it");
    assertThat(page.locator("#pane .muted", new Page.LocatorOptions().setHasText("reading")))
        .hasCount(0);
  }

  @Test
  void codeTabNamesTheCauseWhenTheTreeCannotBeRead() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    Page page = session.newPage();
    page.route(
        "**/api/tasks/code/tree*",
        route ->
            route.fulfill(
                new com.microsoft.playwright.Route.FulfillOptions()
                    .setStatus(400)
                    .setContentType("application/json")
                    .setBody("{\"error\":\"the worktree of alpha is not on disk\"}")));
    page.navigate("http://localhost:" + port + "/");

    page.locator("#pane .tabs button[data-tab=code]").click();

    assertThat(page.locator("#pane .code .detail.problem"))
        .hasText("the worktree of alpha is not on disk");
    assertThat(page.locator("#pane .code .muted", new Page.LocatorOptions().setHasText("reading")))
        .hasCount(0);
  }

  @Test
  void theDiffTabMarksAddedAndRemovedLinesAndSaysWhenItWasCut() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    Page page = session.newPage();
    page.route(
        "**/api/tasks/diff*",
        route ->
            route.fulfill(
                new com.microsoft.playwright.Route.FulfillOptions()
                    .setContentType("application/json")
                    .setBody(
                        "[{\"project\":\"alpha\",\"base\":\"origin/main\",\"cut\":true,\"diff\":"
                            + "\"--- a/f\\n+++ b/f\\n@@ -1 +1 @@\\n-old\\n+new\\n\"}]")));
    page.navigate("http://localhost:" + port + "/");

    page.locator("#pane .tabs button[data-tab=diff]").click();

    assertThat(page.locator("#pane .diff h2")).hasText("alpha · origin/main");
    assertThat(page.locator("#pane .diff .changed")).hasText("+new\n");
    assertThat(page.locator("#pane .diff .removed")).hasText("-old\n");
    assertThat(page.locator("#pane .diff p.muted"))
        .hasText("The diff is cut here; the rest is not shown.");
  }

  @Test
  void verbsKeyRunsItOnTheOpenTask() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    when(commands.press("ABC-1", TaskAction.FOCUS, null))
        .thenReturn(new Pressed("Focused ABC-1.", Outcome.Kind.OK));
    Page page = openBoard();
    assertThat(page.locator("#pane article .alias")).hasText("a1");

    page.keyboard().press("f");

    assertThat(page.locator("#toasts .toast")).hasText("Focused ABC-1.");
    verify(commands).press("ABC-1", TaskAction.FOCUS, null);
  }

  @Test
  void doneStandsLastInItsRowApartFromTheVerbsThatMoveTheTaskOn() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    Page page = openBoard();

    assertThat(page.locator("#pane .actions.flow button").last())
        .hasClass(Pattern.compile("\\bapart\\b"));
    assertThat(page.locator("#pane .actions.flow button.apart")).hasText("Done");
    org.assertj.core.api.Assertions.assertThat(
            Objects.requireNonNull(page.locator("#pane button.apart").boundingBox()).x)
        .isGreaterThan(
            Objects.requireNonNull(page.locator("#pane button[data-action=respawn]").boundingBox())
                .x);
  }

  @Test
  void theHeaderSaysWhileTheMasterPressesTheHumansButtons() {
    Page page = session.newPage();
    page.route(
        "**/api/tasks",
        route ->
            route.fulfill(
                new com.microsoft.playwright.Route.FulfillOptions()
                    .setResponse(route.fetch())
                    .setBody(
                        route
                            .fetch()
                            .text()
                            .replaceFirst(
                                "\"chips\":\\[\\]",
                                "\"chips\":[{\"key\":\"header.acting\",\"broken\":false}]"))));

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("#role-chips .chip")).hasText("Master: act");
  }

  @Test
  void theReportsMenuDropsBelowItsButtonAndLeavesTheHeaderOneLine() {
    Page page = openLiveBoard();

    page.locator("#reports-toggle").click();

    var toggle = page.locator("#reports-toggle").boundingBox();
    org.assertj.core.api.Assertions.assertThat(
            Objects.requireNonNull(page.locator("#reports").boundingBox()).y)
        .isGreaterThanOrEqualTo(Objects.requireNonNull(toggle).y + toggle.height);
    org.assertj.core.api.Assertions.assertThat(
            Objects.requireNonNull(page.locator("body > header").boundingBox()).height)
        .isLessThan(60);
  }

  @Test
  void verbsKeyDoesNothingWhileNarrowWindowShowsOnlyTheList() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    when(commands.press("ABC-1", TaskAction.FOCUS, null))
        .thenReturn(new Pressed("Focused ABC-1.", Outcome.Kind.OK));
    Page page = session.newPage();
    page.setViewportSize(800, 900);
    page.navigate("http://localhost:" + port + "/");
    assertThat(page.locator("#tasks .row")).hasCount(1);

    page.keyboard().press("f");
    page.keyboard().press("Enter");
    page.keyboard().press("f");

    assertThat(page.locator("#toasts .toast")).hasCount(1);
    verify(commands, org.mockito.Mockito.times(1)).press("ABC-1", TaskAction.FOCUS, null);
  }

  @Test
  void jobThatHasNotRunYetIsNotCalledFine() {
    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/styleguide.html");

    assertThat(page.locator("table.jobs [aria-label='not run yet']")).hasCount(1);
  }

  @Test
  void taskWaitingOnTheHumanThatBrokeWearsRedBadgeAndItsHistoryReadsInWords() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.CI_FAILED).message("lint red").build());
    Page page = openBoard();

    assertThat(page.locator("#tasks .row .badge.problem")).hasCount(1);
    assertThat(page.locator("#pane .history")).containsText("checks failed");
  }

  @Test
  void historyNamesTheCheckerRoleOfAnAcceptanceRound() {
    long now = System.currentTimeMillis();
    state.putTask(
        "ABC-1",
        alphaTask("ABC-1", TaskStatus.CI_FAILED)
            .lastActiveTimestamp(now)
            .history(
                List.of(
                    new StatusChange(
                        TaskStatus.IN_PROGRESS,
                        now - 60_000,
                        ActionOrigin.ACCEPTANCE,
                        null,
                        null,
                        "security")))
            .build());
    Page page = openBoard();

    assertThat(page.locator("#pane .history li .muted").first()).hasText("security");
  }

  @Test
  void theStatusHistoryReadsNewestFirst() {
    long now = System.currentTimeMillis();
    state.putTask(
        "ABC-1",
        alphaTask("ABC-1", TaskStatus.CI_FAILED)
            .lastActiveTimestamp(now)
            .history(
                List.of(
                    new StatusChange(TaskStatus.IN_PROGRESS, now - 3_600_000, null),
                    new StatusChange(TaskStatus.CI_FAILED, now - 60_000, null)))
            .build());
    Page page = openBoard();

    assertThat(page.locator("#pane .history li").first()).containsText("checks failed");
  }

  @Test
  void stepsReasonReadsInItsHoverAndNowhereOnThePage() {
    long now = System.currentTimeMillis();
    state.putTask(
        "ABC-1",
        alphaTask("ABC-1", TaskStatus.REVIEW_PENDING)
            .lastActiveTimestamp(now)
            .history(
                List.of(
                    new StatusChange(TaskStatus.REVIEW_PENDING, now - 60_000, null)
                        .because("work done, tests green, ready for review")))
            .build());
    Page page = openBoard();

    page.locator("#pane .history li").first().hover();

    assertThat(page.locator("#tip")).hasText("work done, tests green, ready for review");
    assertThat(page.locator("#board")).not().containsText("ready for review");
  }

  @Test
  void theListSaysWhatItsKeysDo() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    Page page = openBoard();

    assertThat(page.locator("#tasks .keys")).containsText("n next yours");
  }

  @Test
  void anEmptyBoardOffersNewTaskAndProject() {
    Page page = openLiveBoard();

    page.locator("#empty-project").click();

    assertThat(page.locator("#project-dialog")).isVisible();
  }

  @Test
  void theRoundTabReadsRepliesThatAppearedWithoutAnyPush() throws Exception {
    Files.createDirectories(root.resolve("ABC-4-alpha"));
    putReviewTask("ABC-4");
    when(launcher.launch(any())).thenReturn(Launched.created("ABC-9", "Started ABC-9."));
    Page page = openBoard();
    page.locator("#pane .tabs button[data-tab=round]").click();
    assertThat(page.locator("#pane .round")).containsText("Nothing is drafted for this round.");

    replyDraft(root).withKey("ABC-4").withText("## thread 1\nFIXED - Renamed it.\n").build();
    page.locator("#new-task").click();
    page.locator("#ref").fill("ABC-9");
    page.locator("#project input[value=alpha]").check();
    page.locator("#launch button[type=submit]").click();

    assertThat(page.locator("#pane .round")).containsText("Renamed it.");
  }

  @Test
  void theJobsTableTurnsFailedJobRedWithoutReopeningTheReport() {
    breakable.breakIt();
    Page page = openLiveBoard();
    page.locator("#reports-toggle").click();
    page.locator("#show-jobs").click();
    Locator row = page.locator("table.jobs tr:has(button[data-job=breakable])");
    assertThat(row).not().hasClass(Pattern.compile("failed"));

    jobs.runNow("breakable");
    page.waitForCondition(breakable::failing);
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());

    assertThat(row).hasClass(Pattern.compile("failed"));
  }

  @Test
  void theJobGroupsOfTheJobsTableShareOneColumnGrid() {
    Page page = session.newPage();
    page.navigate("http://localhost:" + port + "/styleguide.html");

    var first = page.locator("table.jobs").nth(0);
    var second = page.locator("table.jobs").nth(1);

    org.assertj.core.api.Assertions.assertThat(
            Objects.requireNonNull(first.locator("th").nth(3).boundingBox()).x)
        .isEqualTo(Objects.requireNonNull(second.locator("th").nth(3).boundingBox()).x);
    org.assertj.core.api.Assertions.assertThat(
            Objects.requireNonNull(first.locator("td").nth(3).boundingBox()).x)
        .isEqualTo(Objects.requireNonNull(second.locator("td").nth(3).boundingBox()).x);
    org.assertj.core.api.Assertions.assertThat(first.locator("td").nth(3).boundingBox().width)
        .isEqualTo(second.locator("td").nth(3).boundingBox().width);
  }

  @Test
  void theArtifactsTabSaysWhatTheSessionJudgedOnEachSwitch() {
    state.putTask(
        "ABC-6",
        alphaTask("ABC-6", TaskStatus.IN_PROGRESS)
            .switches(java.util.Map.of("visualize", "no — config-only change"))
            .build());
    Page page = openBoard();

    page.locator("#pane .tabs button[data-tab=artifacts]").click();

    assertThat(page.locator("#pane .artifacts .switches"))
        .hasText("visualize: no — config-only change");
  }
}
