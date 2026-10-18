package dev.jagt.orchestrator.flow;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

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

    @Test
    void noVerbTakesAKeyTheBoardKeepsForItself() {
        List<String> keys = Arrays.stream(TaskAction.values()).map(TaskAction::key).toList();

        assertThat(keys).doesNotContainAnyElementsOf(List.of("t", "j", "k", "n", "/"));
    }
}
