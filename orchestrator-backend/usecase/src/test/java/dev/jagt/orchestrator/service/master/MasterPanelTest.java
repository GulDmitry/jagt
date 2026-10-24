package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.port.RoundReviewer;
import dev.jagt.orchestrator.port.RoundReviewer.Finding;
import dev.jagt.orchestrator.port.RoundReviewer.Judgement;
import dev.jagt.orchestrator.port.RoundReviewer.Premise;
import dev.jagt.orchestrator.task.Probe;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.Turn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MasterPanelTest {

    private static final List<MasterPanel.Role> TWO = List.of(
            new MasterPanel.Role("chaplain", "should it exist", "", List.of()),
            new MasterPanel.Role("developer", "is the code right", "", List.of()));

    @Test
    void showsEachReviewerTheBriefTheAuthorWorkedTo() {
        String shared = MasterPanel.shared("judge hard", "never commit unasked", false, true);

        assertThat(shared).contains("never commit unasked");
    }

    @Test
    void tellsEachReviewerTheFlagsItsGitReadsMustCarry() {
        String shared = MasterPanel.shared("judge hard", "never commit unasked", false, true);

        assertThat(shared).contains("`--no-ext-diff --no-textconv`");
    }

    @Test
    void tellsEachReviewerHowToReadAWorktreePastTheFirst() {
        String shared = MasterPanel.shared("judge hard", "never commit unasked", false, true);

        assertThat(shared).contains("`git -C <worktree>`");
    }

    @Test
    void letsAReviewerStandingInForTheHumanDecideWhatItWouldHaveAsked() {
        String shared = MasterPanel.shared("judge hard", "never commit unasked", true, true);

        assertThat(shared).contains("never answer question");
    }

    @Test
    void quotesTheTicketReadForTheRoundToEachReviewer() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.REVIEW_PENDING).build();

        String prompt = MasterPanel.quote("ABC-1", task,
                new MasterPanel.RoundRead("Summary: accept v3 calls", "", "", List.of(), ""));

        assertThat(prompt).contains("<ticket>\nSummary: accept v3 calls\n</ticket>");
    }

    @Test
    void quotesTheProbesJagtRanToEachReviewerAsFacts() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.REVIEW_PENDING)
                .probes(List.of(Probe.specified(Probe.Kind.COMMAND, List.of("./gradlew", "test"), 0)
                        .probed(true, "exit 0"))).build();

        String prompt = MasterPanel.quote("ABC-1", task, new MasterPanel.RoundRead("", "", "", List.of(), ""));

        assertThat(prompt).contains("<probes>\ncommand ./gradlew test — passed\nexit 0\n</probes>");
    }

    @Test
    void showsThePlanReaderTheProbesJagtWillRun() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.PLAN_PENDING)
                .probes(List.of(Probe.specified(Probe.Kind.REF, List.of("refs/tags/v1"), 0))).build();

        String prompt = MasterPanel.planPrompt("ABC-1", task, "judge hard", "1. tag v1",
                new MasterPanel.RoundRead("", "", "", List.of(), ""), false, true);

        assertThat(prompt).contains("<probes>\nref refs/tags/v1\n</probes>");
    }

    @Test
    void judgesThePlanAgainstTheTicketItQuotes() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.PLAN_PENDING).build();

        String prompt = MasterPanel.planPrompt("ABC-1", task, "judge hard", "1. add the v3 route",
                new MasterPanel.RoundRead("Summary: accept v3 calls", "", "", List.of(), ""), false, true);

        assertThat(prompt).contains("<ticket>\nSummary: accept v3 calls\n</ticket>",
                "<plan>\n1. add the v3 route\n</plan>", "whether this plan does what the ticket asks")
                .doesNotContain("<session_notes>", "wrote no plan.md", "never answer question");
    }

    @Test
    void tellsAPlanReaderLoadingNoServerThatTheTicketWasNeverRead() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.PLAN_PENDING).build();

        String prompt = MasterPanel.planPrompt("ABC-1", task, "judge hard", "1. add the v3 route",
                new MasterPanel.RoundRead("", "", "", List.of(), ""), false, false);

        assertThat(prompt).doesNotContain("tracker tools").contains("The ticket was not read");
    }

    @Test
    void sendsAPlanReaderLoadingAServerToItsMcpToolsWhicheverServerThatIs() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.PLAN_PENDING).build();

        String prompt = MasterPanel.planPrompt("ABC-1", task, "judge hard", "1. add the v3 route",
                new MasterPanel.RoundRead("", "", "", List.of(), ""), false, true);

        assertThat(prompt).contains("Read the ticket with your MCP tools").doesNotContain("tracker tools");
    }

    @Test
    void tellsAPlanReaderHowToWriteItsGitReads() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.PLAN_PENDING).build();

        String prompt = MasterPanel.planPrompt("ABC-1", task, "judge hard", "1. add the v3 route",
                new MasterPanel.RoundRead("", "", "", List.of(), ""), false, true);

        assertThat(prompt).contains("`--no-ext-diff --no-textconv`", "`git -C <worktree>`");
    }

    @Test
    void tellsTheMasterAnsweringAQuestionHowToWriteItsGitReads() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build();

        String prompt = MasterPanel.answerPrompt("ABC-1", task, "judge hard", "never commit unasked",
                "outcome=question — keep v2?", "", List.of(), false, true);

        assertThat(prompt).contains("`--no-ext-diff --no-textconv`", "`git -C <worktree>`");
    }

    @Test
    void tellsTheMasterAnsweringAQuestionWithNoServerThatTheTicketWasNeverRead() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build();

        String prompt = MasterPanel.answerPrompt("ABC-1", task, "judge hard", "never commit unasked",
                "outcome=question — keep v2?", "", List.of(), false, false);

        assertThat(prompt).contains("The ticket was not read").doesNotContain("Read the ticket");
    }

    @Test
    void tellsAReviewerLoadingNoServerThatATicketTheRoundDoesNotQuoteWasNeverRead(@TempDir Path worktree) {
        ChargedReviews reviews = mock(ChargedReviews.class);
        MasterBriefs briefs = mock(MasterBriefs.class);
        RoundQuotes quotes = mock(RoundQuotes.class);
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();
        when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("| QA | is it tested right |"));
        when(briefs.author()).thenReturn("");
        when(quotes.round("ABC-1", task)).thenReturn(Optional.of(new MasterPanel.RoundRead("", "", "", List.of(), "")));
        when(reviews.review(eq("ABC-1"), any())).thenReturn(Judgement.failed("stopped"));

        new MasterPanel(reviews, briefs, quotes, new MasterReview())
                .review("ABC-1", task, new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null));

        ArgumentCaptor<RoundReviewer.Round> round = ArgumentCaptor.captor();
        verify(reviews).review(eq("ABC-1"), round.capture());
        assertThat(round.getValue().shared()).doesNotContain("tracker tools")
                .contains("call any premise resting on the ticket unproven");
    }

    @Test
    void sendsAReviewerWhoseReadLoadsAServerToItsMcpToolsForTheTicket(@TempDir Path worktree) {
        ChargedReviews reviews = mock(ChargedReviews.class);
        MasterBriefs briefs = mock(MasterBriefs.class);
        RoundQuotes quotes = mock(RoundQuotes.class);
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();
        when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("| QA | is it tested right |"));
        when(briefs.author()).thenReturn("");
        when(quotes.round("ABC-1", task)).thenReturn(Optional.of(new MasterPanel.RoundRead("", "", "", List.of(), "")));
        when(reviews.loadsMcpServer()).thenReturn(true);
        when(reviews.review(eq("ABC-1"), any())).thenReturn(Judgement.failed("stopped"));

        new MasterPanel(reviews, briefs, quotes, new MasterReview())
                .review("ABC-1", task, new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null));

        ArgumentCaptor<RoundReviewer.Round> round = ArgumentCaptor.captor();
        verify(reviews).review(eq("ABC-1"), round.capture());
        assertThat(round.getValue().shared()).contains("Read the ticket with your MCP tools");
    }

    @Test
    void quotesTheDiffReadForTheRoundToEachReviewer() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.REVIEW_PENDING).build();

        String prompt = MasterPanel.quote("ABC-1", task,
                new MasterPanel.RoundRead("", "+int x;\n", "", List.of(), ""));

        assertThat(prompt).contains("<diff>\n+int x;\n</diff>");
    }

    @Test
    void givesEveryRoleTheRoundAsTheSamePrefixSoItIsReadFromCache(@TempDir Path worktree) {
        ChargedReviews reviews = mock(ChargedReviews.class);
        MasterBriefs briefs = mock(MasterBriefs.class);
        RoundQuotes quotes = mock(RoundQuotes.class);
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();
        when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of(
                "| role | question |\n| chaplain | should it exist |\n| developer | is the code right |"));
        when(briefs.author()).thenReturn("");
        when(quotes.round("ABC-1", task)).thenReturn(Optional.of(
                new MasterPanel.RoundRead("", "+int x;\n", "", List.of(), "")));
        when(reviews.review(eq("ABC-1"), any())).thenReturn(Judgement.failed("stopped"));

        new MasterPanel(reviews, briefs, quotes, new MasterReview())
                .review("ABC-1", task, new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null));

        ArgumentCaptor<RoundReviewer.Round> round = ArgumentCaptor.captor();
        verify(reviews, times(2)).review(eq("ABC-1"), round.capture());
        assertThat(round.getAllValues()).extracting(RoundReviewer.Round::shared).containsOnly(
                round.getAllValues().getFirst().shared()).allMatch(shared -> shared.contains("<diff>\n+int x;\n</diff>"));
    }

    @Test
    void startsTheOtherRolesOnlyOnceTheFirstHasAnswered(@TempDir Path worktree) {
        ChargedReviews reviews = mock(ChargedReviews.class);
        MasterBriefs briefs = mock(MasterBriefs.class);
        RoundQuotes quotes = mock(RoundQuotes.class);
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();
        when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of(
                "| role | question |\n| chaplain | should it exist |\n| developer | is the code right |"));
        when(briefs.author()).thenReturn("");
        when(quotes.round("ABC-1", task)).thenReturn(Optional.of(new MasterPanel.RoundRead("", "", "", List.of(), "")));
        CountDownLatch developerStarted = new CountDownLatch(1);
        when(reviews.review(eq("ABC-1"), argThat(round -> round.prompt().contains("chaplain")))).thenAnswer(call ->
                Judgement.failed("developer started: " + developerStarted.await(300, TimeUnit.MILLISECONDS)));
        when(reviews.review(eq("ABC-1"), argThat(round -> round.prompt().contains("developer")))).thenAnswer(call -> {
            developerStarted.countDown();
            return Judgement.failed("stopped");
        });

        new MasterPanel(reviews, briefs, quotes, new MasterReview())
                .review("ABC-1", task, new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null));

        assertThat(worktree.resolve(MasterReview.FILE)).content().contains("developer started: false");
    }

    @Test
    void asksTheMasterToDecideTheSessionsQuestionRatherThanDeferIt() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build();

        String prompt = MasterPanel.answerPrompt("ABC-1", task, "judge hard", "never commit unasked",
                "outcome=question — keep v2?", "", List.of(), false, false);

        assertThat(prompt).contains("outcome=question — keep v2?").contains("Never answer question");
    }

    @Test
    void asksTheMasterForTheChangeThatTurnsARedCheckGreenRatherThanAnOverride() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.REVIEW_PENDING).build();

        String prompt = MasterPanel.answerPrompt("ABC-1", task, "judge hard", "never commit unasked",
                "outcome=question — refactor, or override the gate?", "", List.of(), false, false);

        assertThat(prompt).contains("an override, an exception or a human's action is no answer while a change in"
                + " the worktrees can pass it");
    }

    @Test
    void asksTheMasterToTakeTheBestOptionOnceItsAnswersChangedNothing() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.REVIEW_PENDING).build();

        String prompt = MasterPanel.answerPrompt("ABC-1", task, "judge hard", "never commit unasked",
                "outcome=question — override the gate?", "", List.of(), true, false);

        assertThat(prompt).contains("where options remain, recommend the best and take it");
    }

    @Test
    void opensEveryMasterReadOnTheTaskFinishedGreenRatherThanOnJudging() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.REVIEW_PENDING).build();

        String answer = MasterPanel.answerPrompt("ABC-1", task, "judge hard", "never commit unasked",
                "outcome=question — keep v2?", "", List.of(), false, false);
        String review = MasterPanel.shared("judge hard", "never commit unasked", true, true);

        assertThat(answer).startsWith("<goal>\nYour goal is the task finished: ready to merge, its request's checks green");
        assertThat(review).startsWith("<goal>\nYour goal is the task finished: ready to merge, its request's checks green");
    }

    @Test
    void definesAllFiveSeveritiesToThePlanReaderAsToEveryRoundReader() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.PLAN_PENDING).build();

        String prompt = MasterPanel.planPrompt("ABC-1", task, "judge hard", "1. add the v3 route",
                new MasterPanel.RoundRead("", "", "", List.of(), ""), false, true);

        assertThat(prompt).contains("- blocking: someone relying on it today breaks.",
                "- wrong: not what the ticket asks.", "- unguarded: right, but nothing fails when it breaks.",
                "- unproven: you would decide it", "- noise: style or taste.")
                .doesNotContain("Only noise lets the plan pass");
    }

    @Test
    void rulesWordingGlossaryAndStyleNeverStopTheRound() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.PLAN_PENDING).build();

        String prompt = MasterPanel.planPrompt("ABC-1", task, "judge hard", "1. add the v3 route",
                new MasterPanel.RoundRead("", "", "", List.of(), ""), false, true);

        assertThat(prompt).contains("only for correctness or a stated requirement",
                "wording, glossary and style are noise");
    }

    @Test
    void tagsTheRoleAndForbidsAPreambleInEveryMasterPrompt() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.PLAN_PENDING).build();
        MasterPanel.RoundRead read = new MasterPanel.RoundRead("", "", "", List.of(), "");

        assertThat(MasterPanel.prompt(TWO.getFirst())).startsWith("<role>You are the chaplain")
                .endsWith("</role>\nRespond directly, no preamble.");
        assertThat(MasterPanel.planPrompt("ABC-1", task, "judge hard", "", read, false, true))
                .contains("<role>You judge the plan of task ABC-1").endsWith("Respond directly, no preamble.");
        assertThat(MasterPanel.answerPrompt("ABC-1", task, "judge hard", "author", "keep v2?", "", List.of(), false,
                true)).contains("<role>You stand in for the human on task ABC-1.</role>")
                .endsWith("Respond directly, no preamble.");
    }

    @Test
    void holdsEveryReviewerToWhatEarlierRoundsSettled() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.REVIEW_PENDING).build();

        String prompt = MasterPanel.quote("ABC-1", task,
                new MasterPanel.RoundRead("", "", "- keep the extraction per the human", List.of(), ""));

        assertThat(prompt).contains("binding").contains("- keep the extraction per the human");
    }

    @Test
    void quotesWhatTheHumanTypedToTheSessionToEachReviewer() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.REVIEW_PENDING).build();

        String prompt = MasterPanel.quote("ABC-1", task,
                new MasterPanel.RoundRead("", "", "- keep the old field name", List.of("rename it everywhere"), ""));

        assertThat(prompt).contains("<human_said>\nrename it everywhere\n</human_said>");
    }

    @Test
    void quotesTheSessionsNotesSoAReviewerCanCheckWhatItDisputed() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.REVIEW_PENDING).build();

        String prompt = MasterPanel.quote("ABC-1", task,
                new MasterPanel.RoundRead("", "", "", List.of(), "disputed: drop v2 — Api.java:12 has no caller"));

        assertThat(prompt).contains("<session_notes>\ndisputed: drop v2 — Api.java:12 has no caller\n</session_notes>");
    }

    @Test
    void asksTheSessionForEvidenceWhereARoleCouldNotProveItsFinding() {
        String file = MasterPanel.verdictFile("ABC-42", MasterPanel.reads(TWO, List.of(
                new Judgement("", "not ready", List.of(new Finding("Api.java", "which caller still reads v2",
                        "evidence needed", "unproven", "", "")), "", List.of(), ""),
                new Judgement("", "ready", List.of(), "", List.of(), ""))), List.of());

        assertThat(file).contains("- [chaplain] Api.java — show: which caller still reads v2 (evidence needed)");
    }

    @Test
    void quotesWhatTheHumanTypedToTheSessionWhenAnsweringInTheirStead() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build();

        String prompt = MasterPanel.answerPrompt("ABC-1", task, "judge hard", "never commit unasked",
                "outcome=question — rename the field?", "", List.of("rename it everywhere"), false, false);

        assertThat(prompt).contains("<human_said>\nrename it everywhere\n</human_said>");
    }

    @Test
    void asksNoReviewerWhereWhatTheHumanTypedCouldNotBeRead(@TempDir Path worktree) throws Exception {
        ChargedReviews reviews = mock(ChargedReviews.class);
        MasterBriefs briefs = mock(MasterBriefs.class);
        RoundQuotes quotes = mock(RoundQuotes.class);
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();
        when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("| QA | is it tested right |"));
        when(briefs.author()).thenReturn("");
        when(quotes.round("ABC-1", task)).thenReturn(Optional.empty());

        new MasterPanel(reviews, briefs, quotes, new MasterReview())
                .review("ABC-1", task, new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null));

        verify(reviews, never()).review(any(), any());
        assertThat(Files.readString(worktree.resolve(MasterReview.FILE)))
                .contains("could not read the round: what the human typed to the session could not be read");
    }

    @Test
    void readsTheRoundByTheAuthorsRolesWhereTheMastersBriefNamesNone() {
        String authorBrief = """
                | role | the question it asks |
                |------|----------------------|
                | developer | is the code right |
                """;

        assertThat(MasterPanel.roles("# Brief\n\nJudge hard.\n", authorBrief))
                .extracting(MasterPanel.Role::name).containsExactly("developer");
    }

    @Test
    void readsEveryRowOfTheBriefsRoleTableAsItsOwnReviewer() {
        String brief = """
                # Brief

                | role | the question it asks |
                |------|----------------------|
                | chaplain | should this exist at all |
                | QA | is it tested right |

                Afterwards.
                """;

        assertThat(MasterPanel.roles(brief, "")).extracting(MasterPanel.Role::name).containsExactly("chaplain", "QA");
    }

    @Test
    void readsARoleWithTheModelItsRowNames() {
        String brief = """
                | role | the question it asks | model |
                |------|----------------------|-------|
                | architect | is the architecture held | opus |
                | QA | is it tested right | |
                """;

        assertThat(MasterPanel.roles(brief, "")).extracting(role -> role.modelOr("sonnet"))
                .containsExactly("opus", "sonnet");
    }

    @Test
    void readsTheModelAndTheAppliesGlobsByTheirColumnsNamesInAnyOrder() {
        String brief = """
                | role | the question it asks | applies | model |
                |------|----------------------|---------|-------|
                | designer | is the ideology intact | `static/**`, docs/{rules,usage}.md | opus |
                """;

        assertThat(MasterPanel.roles(brief, "")).containsExactly(new MasterPanel.Role("designer",
                "is the ideology intact", "opus", List.of("static/**", "docs/{rules,usage}.md")));
    }

    @Test
    void skipsTheReadOfARoleWhoseGlobsMatchNoChangedPathAndSaysSoInTheReviewFile(@TempDir Path worktree) {
        ChargedReviews reviews = mock(ChargedReviews.class);
        MasterBriefs briefs = mock(MasterBriefs.class);
        RoundQuotes quotes = mock(RoundQuotes.class);
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();
        when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of(
                "| role | question | applies |\n| designer | is the mark worth it | static/** |\n| QA | is it tested |"));
        when(briefs.author()).thenReturn("");
        when(quotes.round("ABC-1", task)).thenReturn(Optional.of(new MasterPanel.RoundRead("", "", "", List.of(), "")));
        when(quotes.paths(task)).thenReturn(Optional.of(List.of("src/Main.java")));
        when(reviews.review(eq("ABC-1"), any())).thenReturn(new Judgement("", "ready", List.of(), "", List.of(), ""));

        new MasterPanel(reviews, briefs, quotes, new MasterReview())
                .review("ABC-1", task, new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null));

        verify(reviews, times(1)).review(eq("ABC-1"), argThat(round -> round.prompt().contains("QA")));
        verify(reviews, never()).review(eq("ABC-1"), argThat(round -> round.prompt().contains("designer")));
        assertThat(worktree.resolve(MasterReview.FILE)).content()
                .contains("# skipped [designer] no changed path matches static/**\n").doesNotContain("skipped [QA]")
                .endsWith("VERDICT: ready\n");
    }

    @Test
    void readsARoundByARoleWithGlobsWhereTheChangedPathsCouldNotBeRead(@TempDir Path worktree) {
        ChargedReviews reviews = mock(ChargedReviews.class);
        MasterBriefs briefs = mock(MasterBriefs.class);
        RoundQuotes quotes = mock(RoundQuotes.class);
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();
        when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of(
                "| role | question | applies |\n| designer | is the mark worth it | static/** |\n| QA | is it tested |"));
        when(briefs.author()).thenReturn("");
        when(quotes.round("ABC-1", task)).thenReturn(Optional.of(new MasterPanel.RoundRead("", "", "", List.of(), "")));
        when(quotes.paths(task)).thenReturn(Optional.empty());
        when(reviews.review(eq("ABC-1"), any())).thenReturn(new Judgement("", "ready", List.of(), "", List.of(), ""));

        new MasterPanel(reviews, briefs, quotes, new MasterReview())
                .review("ABC-1", task, new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null));

        verify(reviews).review(eq("ABC-1"), argThat(round -> round.prompt().contains("designer")));
    }

    @Test
    void readsARoundByEveryRoleWhereNoRolesGlobsMatchIt(@TempDir Path worktree) {
        ChargedReviews reviews = mock(ChargedReviews.class);
        MasterBriefs briefs = mock(MasterBriefs.class);
        RoundQuotes quotes = mock(RoundQuotes.class);
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();
        when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of(
                "| role | question | applies |\n| designer | is the mark worth it | static/** |"));
        when(briefs.author()).thenReturn("");
        when(quotes.round("ABC-1", task)).thenReturn(Optional.of(new MasterPanel.RoundRead("", "", "", List.of(), "")));
        when(quotes.paths(task)).thenReturn(Optional.of(List.of("src/Main.java")));
        when(reviews.review(eq("ABC-1"), any())).thenReturn(new Judgement("", "ready", List.of(), "", List.of(), ""));

        new MasterPanel(reviews, briefs, quotes, new MasterReview())
                .review("ABC-1", task, new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null));

        verify(reviews).review(eq("ABC-1"), argThat(round -> round.prompt().contains("designer")));
    }

    @Test
    void passesARoundOnlyWhereEveryRoleFoundNothing() {
        String file = MasterPanel.verdictFile("ABC-42", MasterPanel.reads(TWO, List.of(
                new Judgement("", "ready", List.of(), "", List.of(new Premise("tests pass", "gradle test: 2 passed")), ""),
                new Judgement("", "ready", List.of(), "", List.of(), ""))), List.of());

        assertThat(file).doesNotContain("unproven").endsWith("VERDICT: ready\n");
    }

    @Test
    void sendsBackARoundOneRoleFoundFaultWithWhateverTheOthersSaid() {
        String file = MasterPanel.verdictFile("ABC-42", MasterPanel.reads(TWO, List.of(
                new Judgement("", "not ready", List.of(new Finding("QuoteMock.java", "v2 dropped", "removed contract", "blocking", "", "")),
                        "", List.of(), ""),
                new Judgement("", "ready", List.of(), "", List.of(), ""))), List.of());

        assertThat(file).contains("- [chaplain] QuoteMock.java — v2 dropped (removed contract)")
                .endsWith("VERDICT: not ready\n");
    }

    @Test
    void passesARoundWhoseOnlyDoubtIsAClaimTheReviewerDidNotProve() {
        String file = MasterPanel.verdictFile("ABC-42", MasterPanel.reads(TWO, List.of(
                new Judgement("", "ready", List.of(), "", List.of(new Premise("v2 and v3 cannot be told apart", "")), ""),
                new Judgement("", "ready", List.of(), "", List.of(), ""))), List.of());

        assertThat(file).contains("# unproven [chaplain] v2 and v3 cannot be told apart")
                .endsWith("VERDICT: ready\n");
    }

    @Test
    void passesARoundWhoseOnlyFindingsAreAdvice() {
        String file = MasterPanel.verdictFile("ABC-42", MasterPanel.reads(TWO, List.of(
                new Judgement("", "not ready", List.of(new Finding("Foo.java", "rename present", "naming", "noise", "", "")),
                        "", List.of(), ""),
                new Judgement("", "ready", List.of(), "", List.of(), ""))), List.of());

        assertThat(file).contains("# advice [chaplain] Foo.java — rename present").endsWith("VERDICT: ready\n");
    }

    @Test
    void putsAnyRolesQuestionToTheHumanOnTheLineAboveTheVerdict() {
        String file = MasterPanel.verdictFile("ABC-42", MasterPanel.reads(TWO, List.of(
                new Judgement("", "question", List.of(), "Add v3 beside v2, or replace it?", List.of(), ""),
                new Judgement("", "ready", List.of(), "", List.of(), ""))), List.of());

        assertThat(file).endsWith("Add v3 beside v2, or replace it?\n\nVERDICT: question\n");
    }

    @Test
    void asksTheHumanWhenARoleCouldNotReadTheRound() {
        String file = MasterPanel.verdictFile("ABC-42", MasterPanel.reads(TWO, List.of(
                Judgement.failed("no tracker tool in this session"),
                new Judgement("", "ready", List.of(), "", List.of(), ""))), List.of());

        assertThat(file).endsWith("The chaplain could not read the round: no tracker tool in this session\n\n"
                + "VERDICT: question\n");
    }

    @Test
    void writesNothingAndAsksNoReviewerWhereTheBriefCouldNotBeRead(@TempDir Path worktree) {
        ChargedReviews reviews = mock(ChargedReviews.class);
        MasterBriefs briefs = mock(MasterBriefs.class);
        RoundQuotes quotes = mock(RoundQuotes.class);
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();
        when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.empty());

        boolean written = new MasterPanel(reviews, briefs, quotes, new MasterReview()).review("ABC-1", task, new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null));

        assertThat(written).isFalse();
        verify(reviews, never()).review(any(), any());
    }

    @Test
    void saysSoAfterWritingTheReviewFileIntoTheWorktree(@TempDir Path worktree) {
        ChargedReviews reviews = mock(ChargedReviews.class);
        MasterBriefs briefs = mock(MasterBriefs.class);
        RoundQuotes quotes = mock(RoundQuotes.class);
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();
        when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("| QA | is it tested right |"));
        when(briefs.author()).thenReturn("");
        when(quotes.round("ABC-1", task)).thenReturn(Optional.empty());

        boolean written = new MasterPanel(reviews, briefs, quotes, new MasterReview()).review("ABC-1", task, new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null));

        assertThat(written).isTrue();
        assertThat(worktree.resolve(MasterReview.FILE)).exists();
    }

    @Test
    void saysNothingWasWrittenWhereTheWorktreeCannotTakeTheReviewFile(@TempDir Path worktree) {
        ChargedReviews reviews = mock(ChargedReviews.class);
        MasterBriefs briefs = mock(MasterBriefs.class);
        RoundQuotes quotes = mock(RoundQuotes.class);
        TaskState task = TaskState.builder("proj", worktree.resolve("gone").toString(), TaskStatus.REVIEW_PENDING).build();
        when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("| QA | is it tested right |"));
        when(briefs.author()).thenReturn("");
        when(quotes.round("ABC-1", task)).thenReturn(Optional.empty());

        boolean written = new MasterPanel(reviews, briefs, quotes, new MasterReview()).review("ABC-1", task, new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null));

        assertThat(written).isFalse();
    }

    @Test
    void writesNothingForAPlanWhereTheBriefCouldNotBeRead(@TempDir Path worktree) {
        ChargedReviews reviews = mock(ChargedReviews.class);
        MasterBriefs briefs = mock(MasterBriefs.class);
        RoundQuotes quotes = mock(RoundQuotes.class);
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.PLAN_PENDING).build();
        when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.empty());

        boolean written = new MasterPanel(reviews, briefs, quotes, new MasterReview()).plan("ABC-1", task, new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null));

        assertThat(written).isFalse();
        assertThat(worktree.resolve(MasterReview.FILE)).doesNotExist();
    }

    @Test
    void writesAReadyVerdictForAPlanThePlannerFoundNothingWrongWith(@TempDir Path worktree) throws Exception {
        ChargedReviews reviews = mock(ChargedReviews.class);
        MasterBriefs briefs = mock(MasterBriefs.class);
        RoundQuotes quotes = mock(RoundQuotes.class);
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.PLAN_PENDING).build();
        when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("judge hard"));
        when(quotes.plan("ABC-1", task)).thenReturn(Optional.of(new MasterPanel.RoundRead("", "", "", List.of(), "")));
        when(quotes.planFile(task)).thenReturn("1. add the v3 route");
        when(reviews.review(eq("ABC-1"), any())).thenReturn(new Judgement("", "ready", List.of(), "", List.of(), ""));

        boolean written = new MasterPanel(reviews, briefs, quotes, new MasterReview()).plan("ABC-1", task, new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null));

        assertThat(written).isTrue();
        assertThat(Files.readString(worktree.resolve(MasterReview.FILE)))
                .startsWith("# ABC-1 — master review").endsWith("VERDICT: ready\n");
    }

    @Test
    void asksNoPlannerWhereWhatTheHumanTypedCouldNotBeRead(@TempDir Path worktree) throws Exception {
        ChargedReviews reviews = mock(ChargedReviews.class);
        MasterBriefs briefs = mock(MasterBriefs.class);
        RoundQuotes quotes = mock(RoundQuotes.class);
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.PLAN_PENDING).build();
        when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("judge hard"));
        when(quotes.plan("ABC-1", task)).thenReturn(Optional.empty());

        new MasterPanel(reviews, briefs, quotes, new MasterReview()).plan("ABC-1", task, new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null));

        verify(reviews, never()).review(any(), any());
        assertThat(Files.readString(worktree.resolve(MasterReview.FILE)))
                .contains("could not read the round: what the human typed to the session could not be read");
    }

    @Test
    void answersAQuestionWithTheFindingsTheReviewerReturned(@TempDir Path worktree) {
        ChargedReviews reviews = mock(ChargedReviews.class);
        MasterBriefs briefs = mock(MasterBriefs.class);
        RoundQuotes quotes = mock(RoundQuotes.class);
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.IN_PROGRESS).build();
        when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("judge hard"));
        when(briefs.author()).thenReturn("");
        when(quotes.question(task)).thenReturn(Optional.of(new MasterPanel.RoundRead("", "", "", List.of(), "")));
        when(reviews.review(eq("ABC-1"), any())).thenReturn(new Judgement("", "answer", List.of(
                new Finding("", "keep v2 beside v3", "", "blocking", "", "")), "", List.of(), "do proj drop the old keys"));

        Optional<MasterPanel.Decision> decision = new MasterPanel(reviews, briefs, quotes, new MasterReview())
                .answer("ABC-1", task, "outcome=question — keep v2?", new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null), false);

        assertThat(decision).contains(new MasterPanel.Decision("keep v2 beside v3", "do proj drop the old keys"));
    }

    @Test
    void answersNothingWhereTheReviewerFailedOrNamedNothing(@TempDir Path worktree) {
        ChargedReviews reviews = mock(ChargedReviews.class);
        MasterBriefs briefs = mock(MasterBriefs.class);
        RoundQuotes quotes = mock(RoundQuotes.class);
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.IN_PROGRESS).build();
        when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("judge hard"));
        when(briefs.author()).thenReturn("");
        when(quotes.question(task)).thenReturn(Optional.of(new MasterPanel.RoundRead("", "", "", List.of(), "")));
        when(reviews.review(eq("ABC-1"), any())).thenReturn(new Judgement("", "answer", List.of(), "", List.of(), ""));

        Optional<MasterPanel.Decision> decision = new MasterPanel(reviews, briefs, quotes, new MasterReview())
                .answer("ABC-1", task, "outcome=question — keep v2?", new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null), false);

        assertThat(decision).isEmpty();
    }

    @Test
    void answersNothingWhereTheReviewerCouldNotReadTheRound(@TempDir Path worktree) {
        ChargedReviews reviews = mock(ChargedReviews.class);
        MasterBriefs briefs = mock(MasterBriefs.class);
        RoundQuotes quotes = mock(RoundQuotes.class);
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.IN_PROGRESS).build();
        when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("judge hard"));
        when(briefs.author()).thenReturn("");
        when(quotes.question(task)).thenReturn(Optional.of(new MasterPanel.RoundRead("", "", "", List.of(), "")));
        when(reviews.review(eq("ABC-1"), any())).thenReturn(new Judgement("stopped", "", List.of(
                new Finding("", "keep v2", "", "blocking", "", "")), "", List.of(), ""));

        Optional<MasterPanel.Decision> decision = new MasterPanel(reviews, briefs, quotes, new MasterReview())
                .answer("ABC-1", task, "outcome=question — keep v2?", new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null), false);

        assertThat(decision).isEmpty();
    }

    @Test
    void answersNothingWhereTheBriefOrWhatTheHumanSaidCouldNotBeRead(@TempDir Path worktree) {
        ChargedReviews reviews = mock(ChargedReviews.class);
        MasterBriefs briefs = mock(MasterBriefs.class);
        RoundQuotes quotes = mock(RoundQuotes.class);
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.IN_PROGRESS).build();
        when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("judge hard"));
        when(quotes.question(task)).thenReturn(Optional.empty());

        Optional<MasterPanel.Decision> decision = new MasterPanel(reviews, briefs, quotes, new MasterReview())
                .answer("ABC-1", task, "outcome=question — keep v2?", new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null), false);

        assertThat(decision).isEmpty();
        verify(reviews, never()).review(any(), any());
    }

    @Test
    void quotesTheSessionsNotesToThePlanReader() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.PLAN_PENDING).build();

        String prompt = MasterPanel.planPrompt("ABC-1", task, "judge hard", "1. add the v3 route",
                new MasterPanel.RoundRead("", "", "", List.of(), "disputed: the ticket line 2"), false, true);

        assertThat(prompt).contains("<session_notes>\ndisputed: the ticket line 2\n</session_notes>");
    }

    @Test
    void holdsAPlanReaderToTheFactThatTheSessionWroteNoPlan() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.PLAN_PENDING).build();

        String prompt = MasterPanel.planPrompt("ABC-1", task, "judge hard", "",
                new MasterPanel.RoundRead("", "", "", List.of(), ""), false, true);

        assertThat(prompt).contains("The session wrote no plan.md: that alone is not ready.")
                .doesNotContain("<plan>");
    }

    @Test
    void letsAPlanReaderStandingInForTheHumanDecideWhatTheTicketLeavesOpen() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.PLAN_PENDING).build();

        String prompt = MasterPanel.planPrompt("ABC-1", task, "judge hard", "1. add the v3 route",
                new MasterPanel.RoundRead("", "", "", List.of(), ""), true, true);

        assertThat(prompt).contains("never answer question");
    }

    @Test
    void namesTheTaskItsWorktreesAndItsTicketToTheMasterAnsweringAQuestion() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS)
                .ticketUrl("https://tracker.example/ABC-1").build();

        String prompt = MasterPanel.answerPrompt("ABC-1", task, "judge hard", "never commit unasked",
                "outcome=question — keep v2?", "", List.of(), false, true);

        assertThat(prompt).contains("The task: ABC-1, worktrees [/wt/ABC-1-proj]",
                "ticket https://tracker.example/ABC-1.");
    }

    @Test
    void namesTheRoundsTicketToEachReviewerWhereTheTaskHasOne() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.REVIEW_PENDING)
                .ticketUrl("https://tracker.example/ABC-1").build();

        String prompt = MasterPanel.quote("ABC-1", task, new MasterPanel.RoundRead("", "", "", List.of(), ""));

        assertThat(prompt).contains("ticket https://tracker.example/ABC-1.").doesNotContain("ticket none");
    }

    @Test
    void doesNotReadAgainARoundWhoseReadyVerdictStillStands(@TempDir Path worktree) {
        ChargedReviews reviews = mock(ChargedReviews.class);
        MasterBriefs briefs = mock(MasterBriefs.class);
        RoundQuotes quotes = mock(RoundQuotes.class);
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();
        when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("| QA | is it tested right |"));
        when(quotes.carried(task)).thenReturn(true);

        boolean written = new MasterPanel(reviews, briefs, quotes, new MasterReview()).review("ABC-1", task, new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null));

        assertThat(written).isTrue();
        verify(reviews, never()).review(any(), any());
        assertThat(worktree.resolve(MasterReview.FILE)).doesNotExist();
    }

    @Test
    void asksTheHumanWhereAReviewerThrewInsteadOfAnswering(@TempDir Path worktree) throws Exception {
        ChargedReviews reviews = mock(ChargedReviews.class);
        MasterBriefs briefs = mock(MasterBriefs.class);
        RoundQuotes quotes = mock(RoundQuotes.class);
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();
        when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("| QA | is it tested right |"));
        when(briefs.author()).thenReturn("");
        when(quotes.round("ABC-1", task)).thenReturn(Optional.of(new MasterPanel.RoundRead("", "", "", List.of(), "")));
        when(reviews.review(eq("ABC-1"), any())).thenThrow(new IllegalStateException("cli gone"));

        new MasterPanel(reviews, briefs, quotes, new MasterReview()).review("ABC-1", task, new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null));

        assertThat(Files.readString(worktree.resolve(MasterReview.FILE)))
                .contains("The reviewer could not read the round: the review threw java.lang.IllegalStateException: cli gone")
                .endsWith("VERDICT: question\n");
    }

    @Test
    void storesTheOwnDiffAndTheHandBackItWasAskedAboutWhereEveryReviewerReadIt(@TempDir Path worktree) {
        ChargedReviews reviews = mock(ChargedReviews.class);
        MasterBriefs briefs = mock(MasterBriefs.class);
        RoundQuotes quotes = mock(RoundQuotes.class);
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build()
                .withTurn(new Turn(Turn.Holder.MASTER, "round-not-ready", 5, "", 5_000, Map.of(), List.of()));
        when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("| QA | is it tested right |"));
        when(briefs.author()).thenReturn("");
        when(quotes.round("ABC-1", task)).thenReturn(Optional.of(new MasterPanel.RoundRead("", "", "", List.of(), "")));
        when(quotes.ownDiff(task)).thenReturn(Optional.of("abc123"));
        when(reviews.review(eq("ABC-1"), any())).thenReturn(new Judgement("", "ready", List.of(), "", List.of(), ""));

        MasterReview stored = new MasterReview();

        new MasterPanel(reviews, briefs, quotes, stored).review("ABC-1", task, new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null));

        assertThat(stored.of(task)).get().returns("abc123", MasterReview.Verdict::ownDiff)
                .returns(5L, MasterReview.Verdict::round);
    }

    @Test
    void storesNoOwnDiffWhereAReviewerCouldNotReadTheRound(@TempDir Path worktree) {
        ChargedReviews reviews = mock(ChargedReviews.class);
        MasterBriefs briefs = mock(MasterBriefs.class);
        RoundQuotes quotes = mock(RoundQuotes.class);
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();
        when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("| QA | is it tested right |"));
        when(briefs.author()).thenReturn("");
        when(quotes.round("ABC-1", task)).thenReturn(Optional.of(new MasterPanel.RoundRead("", "", "", List.of(), "")));
        when(quotes.ownDiff(task)).thenReturn(Optional.of("abc123"));
        when(reviews.review(eq("ABC-1"), any())).thenReturn(Judgement.failed("no tracker tool"));

        MasterReview stored = new MasterReview();

        new MasterPanel(reviews, briefs, quotes, stored).review("ABC-1", task, new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null));

        assertThat(stored.of(task)).get().returns("", MasterReview.Verdict::ownDiff);
    }

    @Test
    void stopsAndSaysNothingWasWrittenWhereTheCallerIsInterruptedMidRound(@TempDir Path worktree) {
        ChargedReviews reviews = mock(ChargedReviews.class);
        MasterBriefs briefs = mock(MasterBriefs.class);
        RoundQuotes quotes = mock(RoundQuotes.class);
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();
        when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("| QA | is it tested right |"));
        when(briefs.author()).thenReturn("");
        when(quotes.round("ABC-1", task)).thenReturn(Optional.of(new MasterPanel.RoundRead("", "", "", List.of(), "")));
        CountDownLatch stillReviewing = new CountDownLatch(1);
        when(reviews.review(eq("ABC-1"), any())).thenAnswer(call -> {
            stillReviewing.await(2, TimeUnit.SECONDS);
            return new Judgement("", "ready", List.of(), "", List.of(), "");
        });
        Thread.currentThread().interrupt();

        boolean written = new MasterPanel(reviews, briefs, quotes, new MasterReview()).review("ABC-1", task, new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null));

        assertThat(Thread.interrupted()).isTrue();
        assertThat(written).isFalse();
        assertThat(worktree.resolve(MasterReview.FILE)).doesNotExist();
    }

    @Test
    void saysNothingWasWrittenForAPlanWhereTheWorktreeCannotTakeTheFile(@TempDir Path worktree) {
        ChargedReviews reviews = mock(ChargedReviews.class);
        MasterBriefs briefs = mock(MasterBriefs.class);
        RoundQuotes quotes = mock(RoundQuotes.class);
        TaskState task = TaskState.builder("proj", worktree.resolve("gone").toString(), TaskStatus.PLAN_PENDING).build();
        when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("judge hard"));
        when(quotes.plan("ABC-1", task)).thenReturn(Optional.empty());

        boolean written = new MasterPanel(reviews, briefs, quotes, new MasterReview()).plan("ABC-1", task, new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null));

        assertThat(written).isFalse();
    }

    @Test
    void namesEveryQuestionWhereSeveralRolesAsk() {
        String file = MasterPanel.verdictFile("ABC-42", MasterPanel.reads(TWO, List.of(
                new Judgement("", "question", List.of(), "Add v3 beside v2?", List.of(), ""),
                new Judgement("", "question", List.of(), "Drop the old keys?", List.of(), ""))), List.of());

        assertThat(file).endsWith("- also asked: Drop the old keys?\nAdd v3 beside v2?\n\nVERDICT: question\n");
    }
}
