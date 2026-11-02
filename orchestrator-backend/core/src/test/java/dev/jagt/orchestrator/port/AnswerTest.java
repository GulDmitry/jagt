package dev.jagt.orchestrator.port;

import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerTest {

    private final ReachLost lost = new ReachLost("glab", "git.example.invalid", List.of("glab", "auth", "login"),
            "glab not logged in to git.example.invalid");

    @Test
    void anUnreachedAnswerCarriesItsLostReachAndNoFacts() {
        Answer<String> answer = Answer.unreached(lost);

        assertThat(answer.reach()).contains(lost);
        assertThat(answer.facts()).isEmpty();
        assertThat(answer.failure()).isEqualTo("glab not logged in to git.example.invalid");
    }

    @Test
    void shapingTheFactsKeepsTheLostReach() {
        Answer<Integer> shaped = new Answer<String>(Optional.empty(), TokenUsage.NONE, "said", false, Optional.of(lost))
                .map(String::length);

        assertThat(shaped.reach()).contains(lost);
    }
}
