package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.port.RoundReviewer;
import dev.jagt.orchestrator.port.RoundReviewer.Finding;
import dev.jagt.orchestrator.port.RoundReviewer.Judgement;
import dev.jagt.orchestrator.port.RoundReviewer.Premise;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MasterPanelTest {

    private static final List<MasterPanel.Role> TWO = List.of(new MasterPanel.Role("chaplain", "should it exist", ""),
            new MasterPanel.Role("developer", "is the code right", ""));

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

        String prompt = MasterPanel.prompt("ABC-1", task, new MasterPanel.Role("QA", "is it tested right", ""),
                new MasterPanel.RoundRead("Summary: accept v3 calls", "", "", List.of(), ""));

        assertThat(prompt).contains("<ticket>\nSummary: accept v3 calls\n</ticket>");
    }

    @Test
    void judgesThePlanAgainstTheTicketItQuotes() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.PLAN_PENDING).build();

        String prompt = MasterPanel.planPrompt("ABC-1", task, "judge hard", "1. add the v3 route",
                new MasterPanel.RoundRead("Summary: accept v3 calls", "", "", List.of(), ""), false, true);

        assertThat(prompt).contains("<ticket>\nSummary: accept v3 calls\n</ticket>",
                "<plan>\n1. add the v3 route\n</plan>", "whether this plan does what the ticket asks");
    }

    @Test
    void tellsAPlanReaderLoadingNoServerThatTheTicketWasNeverRead() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.PLAN_PENDING).build();

        String prompt = MasterPanel.planPrompt("ABC-1", task, "judge hard", "1. add the v3 route",
                new MasterPanel.RoundRead("", "", "", List.of(), ""), false, false);

        assertThat(prompt).doesNotContain("tracker tools").contains("The ticket was not read");
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

        new MasterPanel(reviews, briefs, quotes, AssistantProperties.empty())
                .review("ABC-1", task, new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null));

        ArgumentCaptor<RoundReviewer.Round> round = ArgumentCaptor.captor();
        verify(reviews).review(eq("ABC-1"), round.capture());
        assertThat(round.getValue().shared()).doesNotContain("tracker tools")
                .contains("call any premise resting on the ticket unproven");
    }

    @Test
    void quotesTheDiffReadForTheRoundToEachReviewer() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.REVIEW_PENDING).build();

        String prompt = MasterPanel.prompt("ABC-1", task, new MasterPanel.Role("QA", "is it tested right", ""),
                new MasterPanel.RoundRead("", "+int x;\n", "", List.of(), ""));

        assertThat(prompt).contains("<diff>\n+int x;\n</diff>");
    }

    @Test
    void opensEveryRolesPromptWithTheSameRoundSoItIsReadFromCache() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.REVIEW_PENDING).build();

        String qa = MasterPanel.prompt("ABC-1", task, new MasterPanel.Role("QA", "is it tested right", ""),
                new MasterPanel.RoundRead("Summary: accept v3 calls", "+int x;\n", "", List.of(), ""));
        String developer = MasterPanel.prompt("ABC-1", task, new MasterPanel.Role("developer", "is the code right",
                ""), new MasterPanel.RoundRead("Summary: accept v3 calls", "+int x;\n", "", List.of(), ""));

        assertThat(qa.substring(0, qa.indexOf("</diff>"))).isEqualTo(developer.substring(0, developer.indexOf("</diff>")));
    }

    @Test
    void asksTheMasterToDecideTheSessionsQuestionRatherThanDeferIt() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build();

        String prompt = MasterPanel.answerPrompt("ABC-1", task, "judge hard", "never commit unasked",
                "outcome=question — keep v2?", "", List.of(), false);

        assertThat(prompt).contains("outcome=question — keep v2?").contains("Never answer question");
    }

    @Test
    void asksTheMasterForTheChangeThatTurnsARedCheckGreenRatherThanAnOverride() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.REVIEW_PENDING).build();

        String prompt = MasterPanel.answerPrompt("ABC-1", task, "judge hard", "never commit unasked",
                "outcome=question — refactor, or override the gate?", "", List.of(), false);

        assertThat(prompt).contains("an override, an exception or a human's action is no answer while a change in"
                + " the worktrees can pass it");
    }

    @Test
    void asksTheMasterToTakeTheBestOptionOnceItsAnswersChangedNothing() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.REVIEW_PENDING).build();

        String prompt = MasterPanel.answerPrompt("ABC-1", task, "judge hard", "never commit unasked",
                "outcome=question — override the gate?", "", List.of(), true);

        assertThat(prompt).contains("where options remain, recommend the best and take it");
    }

    @Test
    void opensEveryMasterReadOnTheTaskFinishedGreenRatherThanOnJudging() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.REVIEW_PENDING).build();

        String answer = MasterPanel.answerPrompt("ABC-1", task, "judge hard", "never commit unasked",
                "outcome=question — keep v2?", "", List.of(), false);
        String review = MasterPanel.shared("judge hard", "never commit unasked", true, true);

        assertThat(answer).startsWith("Your goal is the task finished: ready to merge, its request's checks green");
        assertThat(review).startsWith("Your goal is the task finished: ready to merge, its request's checks green");
    }

    @Test
    void holdsEveryReviewerToWhatEarlierRoundsSettled() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.REVIEW_PENDING).build();

        String prompt = MasterPanel.prompt("ABC-1", task, new MasterPanel.Role("QA", "is it tested right", ""),
                new MasterPanel.RoundRead("", "", "- keep the extraction per the human", List.of(), ""));

        assertThat(prompt).contains("binding").contains("- keep the extraction per the human");
    }

    @Test
    void quotesWhatTheHumanTypedToTheSessionToEachReviewer() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.REVIEW_PENDING).build();

        String prompt = MasterPanel.prompt("ABC-1", task, new MasterPanel.Role("QA", "is it tested right", ""),
                new MasterPanel.RoundRead("", "", "- keep the old field name", List.of("rename it everywhere"), ""));

        assertThat(prompt).contains("<human_said>\nrename it everywhere\n</human_said>");
    }

    @Test
    void quotesTheSessionsNotesSoAReviewerCanCheckWhatItDisputed() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.REVIEW_PENDING).build();

        String prompt = MasterPanel.prompt("ABC-1", task, new MasterPanel.Role("QA", "is it tested right", ""),
                new MasterPanel.RoundRead("", "", "", List.of(), "disputed: drop v2 — Api.java:12 has no caller"));

        assertThat(prompt).contains("<session_notes>\ndisputed: drop v2 — Api.java:12 has no caller\n</session_notes>");
    }

    @Test
    void asksTheSessionForEvidenceWhereARoleCouldNotProveItsFinding() {
        String file = MasterPanel.verdictFile("ABC-42", TWO, List.of(
                new Judgement("", "not ready", List.of(new Finding("Api.java", "which caller still reads v2",
                        "evidence needed", "unproven")), "", List.of()),
                new Judgement("", "ready", List.of(), "", List.of())));

        assertThat(file).contains("- [chaplain] Api.java — show: which caller still reads v2 (evidence needed)");
    }

    @Test
    void quotesWhatTheHumanTypedToTheSessionWhenAnsweringInTheirStead() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build();

        String prompt = MasterPanel.answerPrompt("ABC-1", task, "judge hard", "never commit unasked",
                "outcome=question — rename the field?", "", List.of("rename it everywhere"), false);

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

        new MasterPanel(reviews, briefs, quotes, AssistantProperties.empty())
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
    void passesARoundOnlyWhereEveryRoleFoundNothing() {
        String file = MasterPanel.verdictFile("ABC-42", TWO, List.of(
                new Judgement("", "ready", List.of(), "", List.of(new Premise("tests pass", "gradle test: 2 passed"))),
                new Judgement("", "ready", List.of(), "", List.of())));

        assertThat(file).endsWith("VERDICT: ready\n");
    }

    @Test
    void sendsBackARoundOneRoleFoundFaultWithWhateverTheOthersSaid() {
        String file = MasterPanel.verdictFile("ABC-42", TWO, List.of(
                new Judgement("", "not ready", List.of(new Finding("QuoteMock.java", "v2 dropped", "removed contract", "blocking")),
                        "", List.of()),
                new Judgement("", "ready", List.of(), "", List.of())));

        assertThat(file).contains("- [chaplain] QuoteMock.java — v2 dropped (removed contract)")
                .endsWith("VERDICT: not ready\n");
    }

    @Test
    void passesARoundWhoseOnlyDoubtIsAClaimTheReviewerDidNotProve() {
        String file = MasterPanel.verdictFile("ABC-42", TWO, List.of(
                new Judgement("", "ready", List.of(), "", List.of(new Premise("v2 and v3 cannot be told apart", ""))),
                new Judgement("", "ready", List.of(), "", List.of())));

        assertThat(file).contains("# unproven [chaplain] v2 and v3 cannot be told apart")
                .endsWith("VERDICT: ready\n");
    }

    @Test
    void passesARoundWhoseOnlyFindingsAreAdvice() {
        String file = MasterPanel.verdictFile("ABC-42", TWO, List.of(
                new Judgement("", "not ready", List.of(new Finding("Foo.java", "rename present", "naming", "noise")),
                        "", List.of()),
                new Judgement("", "ready", List.of(), "", List.of())));

        assertThat(file).contains("# advice [chaplain] Foo.java — rename present").endsWith("VERDICT: ready\n");
    }

    @Test
    void putsAnyRolesQuestionToTheHumanOnTheLineAboveTheVerdict() {
        String file = MasterPanel.verdictFile("ABC-42", TWO, List.of(
                new Judgement("", "question", List.of(), "Add v3 beside v2, or replace it?", List.of()),
                new Judgement("", "ready", List.of(), "", List.of())));

        assertThat(file).endsWith("Add v3 beside v2, or replace it?\n\nVERDICT: question\n");
    }

    @Test
    void asksTheHumanWhenARoleCouldNotReadTheRound() {
        String file = MasterPanel.verdictFile("ABC-42", TWO, List.of(
                Judgement.failed("no tracker tool in this session"),
                new Judgement("", "ready", List.of(), "", List.of())));

        assertThat(file).endsWith("The chaplain could not read the round: no tracker tool in this session\n\n"
                + "VERDICT: question\n");
    }
}
