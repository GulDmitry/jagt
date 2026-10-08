package dev.jagt.orchestrator.flow;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskActionTest {

    @Test
    void resolvesARetiredSpellingTypedInAnyCase() {
        assertThat(TaskAction.byRetiredVerb("REVIEW")).contains(TaskAction.SWEEP);
    }

    @Test
    void answersNothingForAWireIdThatIsNoLongerAVerb() {
        assertThat(TaskAction.byId("review")).isEmpty();
    }

    @Test
    void answersNothingForARetiredLookupOfACurrentVerb() {
        assertThat(TaskAction.byRetiredVerb("sweep")).isEmpty();
    }
}
