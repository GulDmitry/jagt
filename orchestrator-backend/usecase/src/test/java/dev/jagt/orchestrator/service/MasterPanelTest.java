package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.RoundReviewer.Finding;
import dev.jagt.orchestrator.port.RoundReviewer.Judgement;
import dev.jagt.orchestrator.port.RoundReviewer.Premise;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MasterPanelTest {

    private static final List<MasterPanel.Role> TWO = List.of(new MasterPanel.Role("chaplain", "should it exist"),
            new MasterPanel.Role("developer", "is the code right"));

    @Test
    void showsEachReviewerTheBriefTheAuthorWorkedTo() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.REVIEW_PENDING).build();

        String prompt = MasterPanel.prompt("ABC-1", task, "judge hard", "never commit unasked",
                new MasterPanel.Role("QA", "is it tested right"), false, "");

        assertThat(prompt).contains("never commit unasked");
    }

    @Test
    void letsAReviewerStandingInForTheHumanDecideWhatItWouldHaveAsked() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.REVIEW_PENDING).build();

        String prompt = MasterPanel.prompt("ABC-1", task, "judge hard", "never commit unasked",
                new MasterPanel.Role("QA", "is it tested right"), true, "");

        assertThat(prompt).contains("never answer question");
    }

    @Test
    void asksTheMasterToDecideTheSessionsQuestionRatherThanDeferIt() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build();

        String prompt = MasterPanel.answerPrompt("ABC-1", task, "judge hard", "never commit unasked",
                "outcome=question — keep v2?", "");

        assertThat(prompt).contains("outcome=question — keep v2?").contains("Never answer question");
    }

    @Test
    void holdsEveryReviewerToWhatEarlierRoundsSettled() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.REVIEW_PENDING).build();

        String prompt = MasterPanel.prompt("ABC-1", task, "judge hard", "never commit unasked",
                new MasterPanel.Role("QA", "is it tested right"), true, "- keep the extraction per the human");

        assertThat(prompt).contains("binding").contains("- keep the extraction per the human");
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
