package dev.jagt.orchestrator.service.master;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.port.assistant.RoundReviewer.Finding;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Judgement;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Premise;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Press;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Verdict;
import java.util.List;
import org.junit.jupiter.api.Test;

class MasterVerdictFileTest {

  private final Judgement ready =
      new Judgement("", Verdict.READY, List.of(), "", List.of(), "", Press.NONE);

  private static final List<MasterPanel.Role> TWO =
      List.of(
          new MasterPanel.Role("chaplain", "should it exist", "", List.of(), ""),
          new MasterPanel.Role("developer", "is the code right", "", List.of(), ""));

  @Test
  void asksTheSessionForEvidenceWhereRoleCouldNotProveItsFinding() {
    String file =
        MasterVerdictFile.of(
            "ABC-42",
            MasterVerdictFile.reads(
                TWO,
                List.of(
                    new Judgement(
                        "",
                        Verdict.NOT_READY,
                        List.of(
                            new Finding(
                                "Api.java",
                                "which caller still reads v2",
                                "evidence needed",
                                "unproven",
                                "",
                                "")),
                        "",
                        List.of(),
                        "",
                        Press.NONE),
                    ready)),
            List.of());

    assertThat(file)
        .contains("- [chaplain] Api.java — show: which caller still reads v2 (evidence needed)");
  }

  @Test
  void passesRoundOnlyWhereEveryRoleFoundNothing() {
    String file =
        MasterVerdictFile.of(
            "ABC-42",
            MasterVerdictFile.reads(
                TWO,
                List.of(
                    new Judgement(
                        "",
                        Verdict.READY,
                        List.of(),
                        "",
                        List.of(new Premise("tests pass", "gradle test: 2 passed")),
                        "",
                        Press.NONE),
                    ready)),
            List.of());

    assertThat(file).doesNotContain("unproven").endsWith("VERDICT: ready\n");
  }

  @Test
  void sendsBackRoundOneRoleFoundFaultWithWhateverTheOthersSaid() {
    String file =
        MasterVerdictFile.of(
            "ABC-42",
            MasterVerdictFile.reads(
                TWO,
                List.of(
                    new Judgement(
                        "",
                        Verdict.NOT_READY,
                        List.of(
                            new Finding(
                                "QuoteMock.java",
                                "v2 dropped",
                                "removed contract",
                                "blocking",
                                "",
                                "")),
                        "",
                        List.of(),
                        "",
                        Press.NONE),
                    ready)),
            List.of());

    assertThat(file)
        .contains("- [chaplain] QuoteMock.java — v2 dropped (removed contract)")
        .endsWith("VERDICT: not ready\n");
  }

  @Test
  void passesRoundWhoseOnlyDoubtIsClaimTheReviewerDidNotProve() {
    String file =
        MasterVerdictFile.of(
            "ABC-42",
            MasterVerdictFile.reads(
                TWO,
                List.of(
                    new Judgement(
                        "",
                        Verdict.READY,
                        List.of(),
                        "",
                        List.of(new Premise("v2 and v3 cannot be told apart", "")),
                        "",
                        Press.NONE),
                    ready)),
            List.of());

    assertThat(file)
        .contains("# unproven [chaplain] v2 and v3 cannot be told apart")
        .endsWith("VERDICT: ready\n");
  }

  @Test
  void passesRoundWhoseOnlyFindingsAreAdvice() {
    String file =
        MasterVerdictFile.of(
            "ABC-42",
            MasterVerdictFile.reads(
                TWO,
                List.of(
                    new Judgement(
                        "",
                        Verdict.NOT_READY,
                        List.of(
                            new Finding("Foo.java", "rename present", "naming", "noise", "", "")),
                        "",
                        List.of(),
                        "",
                        Press.NONE),
                    ready)),
            List.of());

    assertThat(file)
        .contains("# advice [chaplain] Foo.java — rename present")
        .endsWith("VERDICT: ready\n");
  }

  @Test
  void putsAnyRolesQuestionToTheHumanOnTheLineAboveTheVerdict() {
    String file =
        MasterVerdictFile.of(
            "ABC-42",
            MasterVerdictFile.reads(
                TWO,
                List.of(
                    new Judgement(
                        "",
                        Verdict.QUESTION,
                        List.of(),
                        "Add v3 beside v2, or replace it?",
                        List.of(),
                        "",
                        Press.NONE),
                    ready)),
            List.of());

    assertThat(file).endsWith("Add v3 beside v2, or replace it?\n\nVERDICT: question\n");
  }

  @Test
  void asksTheHumanWhenRoleCouldNotReadTheRound() {
    String file =
        MasterVerdictFile.of(
            "ABC-42",
            MasterVerdictFile.reads(
                TWO, List.of(Judgement.failed("no tracker tool in this session"), ready)),
            List.of());

    assertThat(file)
        .endsWith(
            """
            The chaplain could not read the round: no tracker tool in this session

            VERDICT: question
            """);
  }

  @Test
  void namesEveryQuestionWhereSeveralRolesAsk() {
    String file =
        MasterVerdictFile.of(
            "ABC-42",
            MasterVerdictFile.reads(
                TWO,
                List.of(
                    new Judgement(
                        "",
                        Verdict.QUESTION,
                        List.of(),
                        "Add v3 beside v2?",
                        List.of(),
                        "",
                        Press.NONE),
                    new Judgement(
                        "",
                        Verdict.QUESTION,
                        List.of(),
                        "Drop the old keys?",
                        List.of(),
                        "",
                        Press.NONE))),
            List.of());

    assertThat(file)
        .endsWith("- also asked: Drop the old keys?\nAdd v3 beside v2?\n\nVERDICT: question\n");
  }

  @Test
  void listsNoUnprovenPremiseOfRoleWhoseReadFailed() {
    String file =
        MasterVerdictFile.of(
            "ABC-42",
            MasterVerdictFile.reads(
                TWO,
                List.of(
                    new Judgement(
                        "read failed",
                        Verdict.NOT_READY,
                        List.of(),
                        "",
                        List.of(new Premise("tests pass", "")),
                        "",
                        Press.NONE),
                    ready)),
            List.of());

    assertThat(file).doesNotContain("unproven [chaplain]");
  }
}
