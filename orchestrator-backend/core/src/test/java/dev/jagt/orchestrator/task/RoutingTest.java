package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingTest {

    @Test
    void answersAQuestionNamingAnItemAndAtLeastOneProject() {
        RoutingQuestion question = RoutingQuestion.defaults()
                .withItem(TicketFacts.defaults().withKey("ABC-42"))
                .withProjects(Map.of("api", "the backend"));

        assertThat(question.answerable()).isTrue();
    }

    @Test
    void cannotAnswerAQuestionWithNoProjectToChooseFrom() {
        RoutingQuestion question = RoutingQuestion.defaults().withItem(TicketFacts.defaults().withKey("ABC-42"));

        assertThat(question.answerable()).isFalse();
    }

    @Test
    void cannotAnswerAQuestionWhoseItemHasNoKey() {
        RoutingQuestion question = RoutingQuestion.defaults().withProjects(Map.of("api", "the backend"));

        assertThat(question.answerable()).isFalse();
    }

    @Test
    void treatsMissingEvidenceAsNone() {
        RoutingQuestion question = new RoutingQuestion(TicketFacts.defaults(), null, null, null);

        assertThat(question.projects()).isEmpty();
        assertThat(question.precedents()).isEmpty();
        assertThat(question.rules()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {"api,labels carry it,true", "api,'  ',false", "api,NULL,false"})
    void teachesOnlyWhenTheAnswerCameWithARule(String project, String rule, boolean teaches) {
        assertThat(new RoutingAnswer(project, rule).teaches()).isEqualTo(teaches);
    }

    @Test
    void stripsWhatTheRouterPaddedItsAnswerWith() {
        RoutingAnswer answer = new RoutingAnswer(" api ", " labels carry it ");

        assertThat(answer.project()).isEqualTo("api");
        assertThat(answer.rule()).isEqualTo("labels carry it");
    }

    @Test
    void readsAMissingProjectAsBlank() {
        assertThat(new RoutingAnswer(null, "x").project()).isEmpty();
    }

    @Test
    void readsAMissingRuleOfAPairAsBlankAndStripsTheOthers() {
        RulePair pair = new RulePair(" labels carry it ", null);

        assertThat(pair.kept()).isEqualTo("labels carry it");
        assertThat(pair.duplicate()).isEmpty();
    }
}
