package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.RoundReviewer.Finding;
import dev.jagt.orchestrator.port.RoundReviewer.Judgement;
import dev.jagt.orchestrator.port.RoundReviewer.Premise;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.port.RoundReviewer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
        String shared = MasterPanel.shared("judge hard", "never commit unasked", false);

        assertThat(shared).contains("never commit unasked");
    }

    @Test
    void letsAReviewerStandingInForTheHumanDecideWhatItWouldHaveAsked() {
        String shared = MasterPanel.shared("judge hard", "never commit unasked", true);

        assertThat(shared).contains("never answer question");
    }

    @Test
    void quotesTheTicketReadForTheRoundToEachReviewer() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.REVIEW_PENDING).build();

        String prompt = MasterPanel.prompt("ABC-1", task, new MasterPanel.Role("QA", "is it tested right", ""),
                "Summary: accept v3 calls", "", "", List.of());

        assertThat(prompt).contains("<ticket>\nSummary: accept v3 calls\n</ticket>");
    }

    @Test
    void quotesTheDiffReadForTheRoundToEachReviewer() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.REVIEW_PENDING).build();

        String prompt = MasterPanel.prompt("ABC-1", task, new MasterPanel.Role("QA", "is it tested right", ""), "",
                "+int x;\n", "", List.of());

        assertThat(prompt).contains("<diff>\n+int x;\n</diff>");
    }

    @Test
    void opensEveryRolesPromptWithTheSameRoundSoItIsReadFromCache() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.REVIEW_PENDING).build();

        String qa = MasterPanel.prompt("ABC-1", task, new MasterPanel.Role("QA", "is it tested right", ""),
                "Summary: accept v3 calls", "+int x;\n", "", List.of());
        String developer = MasterPanel.prompt("ABC-1", task, new MasterPanel.Role("developer", "is the code right",
                ""), "Summary: accept v3 calls", "+int x;\n", "", List.of());

        assertThat(qa.substring(0, qa.indexOf("</diff>"))).isEqualTo(developer.substring(0, developer.indexOf("</diff>")));
    }

    @Test
    void asksTheMasterToDecideTheSessionsQuestionRatherThanDeferIt() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build();

        String prompt = MasterPanel.answerPrompt("ABC-1", task, "judge hard", "never commit unasked",
                "outcome=question — keep v2?", "", List.of());

        assertThat(prompt).contains("outcome=question — keep v2?").contains("Never answer question");
    }

    @Test
    void holdsEveryReviewerToWhatEarlierRoundsSettled() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.REVIEW_PENDING).build();

        String prompt = MasterPanel.prompt("ABC-1", task, new MasterPanel.Role("QA", "is it tested right", ""), "",
                "", "- keep the extraction per the human", List.of());

        assertThat(prompt).contains("binding").contains("- keep the extraction per the human");
    }

    @Test
    void quotesWhatTheHumanTypedToTheSessionToEachReviewer() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.REVIEW_PENDING).build();

        String prompt = MasterPanel.prompt("ABC-1", task, new MasterPanel.Role("QA", "is it tested right", ""), "",
                "", "- keep the old field name", List.of("rename it everywhere"));

        assertThat(prompt).contains("<human_said>\nrename it everywhere\n</human_said>");
    }

    @Test
    void quotesWhatTheHumanTypedToTheSessionWhenAnsweringInTheirStead() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build();

        String prompt = MasterPanel.answerPrompt("ABC-1", task, "judge hard", "never commit unasked",
                "outcome=question — rename the field?", "", List.of("rename it everywhere"));

        assertThat(prompt).contains("<human_said>\nrename it everywhere\n</human_said>");
    }

    @Test
    void asksNoReviewerWhereWhatTheHumanTypedCouldNotBeRead(@TempDir Path worktree) throws Exception {
        RoundReviewer reviewer = mock(RoundReviewer.class);
        MasterBriefs briefs = mock(MasterBriefs.class);
        RoundFacts facts = mock(RoundFacts.class);
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();
        when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("| QA | is it tested right |"));
        when(briefs.author()).thenReturn("");
        when(facts.humanSaid(task)).thenReturn(Optional.empty());

        new MasterPanel(reviewer, mock(UsageTracker.class), briefs, mock(MasterDecisions.class), facts)
                .review("ABC-1", task, new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null));

        verify(reviewer, never()).review(any());
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
