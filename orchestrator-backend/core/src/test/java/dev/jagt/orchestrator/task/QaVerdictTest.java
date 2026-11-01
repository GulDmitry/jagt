package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QaVerdictTest {

    @Test
    void passesOnlyWhereEveryRowIsMet() {
        QaVerdict verdict = new QaVerdict("abc", "", List.of(
                new QaVerdict.Row("login works", QaVerdict.Status.MET, "GET /me 200")), "", "");

        assertThat(verdict.ending()).isEqualTo(QaEnding.PASSED);
    }

    @Test
    void sendsARowNotMetBackBeforeAskingAboutOneUnprovable() {
        QaVerdict verdict = new QaVerdict("abc", "", List.of(
                new QaVerdict.Row("login works", QaVerdict.Status.UNPROVABLE, "no login"),
                new QaVerdict.Row("list is sorted", QaVerdict.Status.NOT_MET, "GET /list unsorted")), "", "");

        assertThat(verdict.ending()).isEqualTo(QaEnding.NOT_MET);
    }

    @Test
    void asksTheHumanWhereARowIsUnprovable() {
        QaVerdict verdict = new QaVerdict("abc", "", List.of(
                new QaVerdict.Row("login works", QaVerdict.Status.UNPROVABLE, "no login")), "", "");

        assertThat(verdict.ending()).isEqualTo(QaEnding.UNPROVABLE);
    }

    @Test
    void provesNothingWhereNoRowWasWritten() {
        assertThat(new QaVerdict("abc", "", List.of(), "", "").ending()).isEqualTo(QaEnding.UNPROVABLE);
    }

    @Test
    void waitsWhereTheEnvironmentDidNotAnswer() {
        assertThat(new QaVerdict("abc", "", List.of(), "", "GET https://stage.example.com/health").ending())
                .isEqualTo(QaEnding.WAITING);
    }

    @Test
    void failsWhereTheRunSaidItFailedWhateverRowsItHolds() {
        QaVerdict verdict = new QaVerdict("abc", "spend ceiling", List.of(
                new QaVerdict.Row("login works", QaVerdict.Status.MET, "ok")), "", "");

        assertThat(verdict.ending()).isEqualTo(QaEnding.FAILED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"accept", "decline", "again"})
    void takesEachBriefStepAsAStep(String step) {
        assertThat(QaVerdict.isStep(step)).isTrue();
    }

    @Test
    void takesAnyOtherWordAsNoStep() {
        assertThat(QaVerdict.isStep("rounds")).isFalse();
    }

    @Test
    void readsAStatusWrittenWithSpacesAndCapitals() {
        assertThat(QaVerdict.Status.of(" Not Met ")).contains(QaVerdict.Status.NOT_MET);
        assertThat(QaVerdict.Status.of("maybe")).isEmpty();
    }

    @Test
    void writesARowAsOneLineNamingItsStatusInWords() {
        QaVerdict.Row row = new QaVerdict.Row("login works", QaVerdict.Status.NOT_MET, "GET /me 404");

        assertThat(row.line()).isEqualTo("- login works — not met: GET /me 404");
    }

    @Test
    void failsWithTheFailureAndNoRows() {
        QaVerdict verdict = QaVerdict.failed("abc", "spend ceiling");

        assertThat(verdict.failure()).isEqualTo("spend ceiling");
        assertThat(verdict.rows()).isEmpty();
    }

    @Test
    void listsOnlyTheRowsNotMet() {
        QaVerdict.Row met = new QaVerdict.Row("a", QaVerdict.Status.MET, "ok");
        QaVerdict.Row notMet = new QaVerdict.Row("b", QaVerdict.Status.NOT_MET, "no");
        QaVerdict verdict = new QaVerdict("abc", "", List.of(met, notMet), "", "");

        assertThat(verdict.notMet()).containsExactly(notMet);
    }
}
