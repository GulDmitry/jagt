package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;

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
}
