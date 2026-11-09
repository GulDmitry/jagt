package dev.jagt.orchestrator.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AcceptanceVerdictTest {

  @Test
  void passesOnlyWhereEveryRowIsMet() {
    AcceptanceVerdict verdict =
        new AcceptanceVerdict(
            "abc",
            "",
            List.of(
                new AcceptanceVerdict.Row(
                    "login works", AcceptanceVerdict.Status.MET, "GET /me 200")),
            "",
            "",
            AcceptanceVerdict.Flows.none());

    assertThat(verdict.ending()).isEqualTo(AcceptanceEnding.PASSED);
  }

  @Test
  void sendsARowNotMetBackBeforeAskingAboutOneUnprovable() {
    AcceptanceVerdict verdict =
        new AcceptanceVerdict(
            "abc",
            "",
            List.of(
                new AcceptanceVerdict.Row(
                    "login works", AcceptanceVerdict.Status.UNPROVABLE, "no login"),
                new AcceptanceVerdict.Row(
                    "list is sorted", AcceptanceVerdict.Status.NOT_MET, "GET /list unsorted")),
            "",
            "",
            AcceptanceVerdict.Flows.none());

    assertThat(verdict.ending()).isEqualTo(AcceptanceEnding.NOT_MET);
  }

  @Test
  void asksTheHumanWhereARowIsUnprovable() {
    AcceptanceVerdict verdict =
        new AcceptanceVerdict(
            "abc",
            "",
            List.of(
                new AcceptanceVerdict.Row(
                    "login works", AcceptanceVerdict.Status.UNPROVABLE, "no login")),
            "",
            "",
            AcceptanceVerdict.Flows.none());

    assertThat(verdict.ending()).isEqualTo(AcceptanceEnding.UNPROVABLE);
  }

  @Test
  void provesNothingWhereNoRowWasWritten() {
    assertThat(
            new AcceptanceVerdict("abc", "", List.of(), "", "", AcceptanceVerdict.Flows.none())
                .ending())
        .isEqualTo(AcceptanceEnding.UNPROVABLE);
  }

  @Test
  void waitsWhereTheEnvironmentDidNotAnswer() {
    assertThat(
            new AcceptanceVerdict(
                    "abc",
                    "",
                    List.of(),
                    "",
                    "GET https://stage.example.com/health",
                    AcceptanceVerdict.Flows.none())
                .ending())
        .isEqualTo(AcceptanceEnding.WAITING);
  }

  @Test
  void failsWhereTheRunSaidItFailedWhateverRowsItHolds() {
    AcceptanceVerdict verdict =
        new AcceptanceVerdict(
            "abc",
            "spend ceiling",
            List.of(new AcceptanceVerdict.Row("login works", AcceptanceVerdict.Status.MET, "ok")),
            "",
            "",
            AcceptanceVerdict.Flows.none());

    assertThat(verdict.ending()).isEqualTo(AcceptanceEnding.FAILED);
  }

  @ParameterizedTest
  @ValueSource(strings = {"accept", "decline", "again"})
  void takesEachBriefStepAsAStep(String step) {
    assertThat(AcceptanceVerdict.isStep(step)).isTrue();
  }

  @Test
  void takesAnyOtherWordAsNoStep() {
    assertThat(AcceptanceVerdict.isStep("rounds")).isFalse();
  }

  @Test
  void readsAStatusWrittenWithSpacesAndCapitals() {
    assertThat(AcceptanceVerdict.Status.of(" Not Met ")).contains(AcceptanceVerdict.Status.NOT_MET);
    assertThat(AcceptanceVerdict.Status.of("maybe")).isEmpty();
  }

  @Test
  void writesARowAsOneLineNamingItsStatusInWords() {
    AcceptanceVerdict.Row row =
        new AcceptanceVerdict.Row("login works", AcceptanceVerdict.Status.NOT_MET, "GET /me 404");

    assertThat(row.line()).isEqualTo("- login works — not met: GET /me 404");
  }

  @Test
  void failsWithTheFailureAndNoRows() {
    AcceptanceVerdict verdict = AcceptanceVerdict.failed("abc", "spend ceiling");

    assertThat(verdict.failure()).isEqualTo("spend ceiling");
    assertThat(verdict.rows()).isEmpty();
  }

  @Test
  void listsOnlyTheRowsNotMet() {
    AcceptanceVerdict.Row met = new AcceptanceVerdict.Row("a", AcceptanceVerdict.Status.MET, "ok");
    AcceptanceVerdict.Row notMet =
        new AcceptanceVerdict.Row("b", AcceptanceVerdict.Status.NOT_MET, "no");
    AcceptanceVerdict verdict =
        new AcceptanceVerdict(
            "abc", "", List.of(met, notMet), "", "", AcceptanceVerdict.Flows.none());

    assertThat(verdict.notMet()).containsExactly(notMet);
  }
}
