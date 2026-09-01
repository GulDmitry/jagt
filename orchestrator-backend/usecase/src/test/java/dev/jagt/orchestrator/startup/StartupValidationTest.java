package dev.jagt.orchestrator.startup;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class StartupValidationTest {

    @Test
    void startsWhenEveryPartAnswersThatItIsThere() {
        assertThatCode(new StartupValidation(List.of(List::of, List::of))::refuseToRunWithoutWhatItNeeds)
                .doesNotThrowAnyException();
    }

    @Test
    void reportsEveryMissingPartAtOnceSoTheHumanFixesTheListOnce() {
        StartupValidation validation = new StartupValidation(List.of(
                () -> List.of("tmux is not on PATH"),
                () -> List.of("config.json defines no projects", "kitty is not on PATH")));

        assertThatThrownBy(validation::refuseToRunWithoutWhatItNeeds)
                .isInstanceOf(Misconfigured.class)
                .hasMessageContaining("3 problems:")
                .hasMessageContaining("1. tmux is not on PATH")
                .hasMessageContaining("2. config.json defines no projects")
                .hasMessageContaining("3. kitty is not on PATH");
    }

    @Test
    void treatsACheckThatCannotAnswerAsAPartThatIsNotThere() {
        StartupValidation validation = new StartupValidation(List.of(() -> {
            throw new IllegalStateException("no such directory");
        }));

        assertThatThrownBy(validation::refuseToRunWithoutWhatItNeeds)
                .isInstanceOf(Misconfigured.class)
                .hasMessageContaining("could not run")
                .hasMessageContaining("no such directory");
    }
}
