package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.StartupCheck;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class StartupValidationTest {

    @Test
    void startsWhenEveryPartAnswersThatItIsThere() {
        assertThatCode(new StartupValidation(List.of(List::of, List::of), true)::refuseToRunWithoutWhatItNeeds)
                .doesNotThrowAnyException();
    }

    @Test
    void reportsEveryMissingPartAtOnceSoTheHumanFixesTheListOnce() {
        StartupValidation validation = new StartupValidation(List.of(
                () -> List.of(new Problem("orchestrator.tmux-command", "tmux is not on PATH")),
                () -> List.of(new Problem("orchestrator.projects", "config.json defines no projects"),
                        new Problem("orchestrator.kitty-command", "kitty is not on PATH"))), true);

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
        }), true);

        assertThatThrownBy(validation::refuseToRunWithoutWhatItNeeds)
                .isInstanceOf(Misconfigured.class)
                .hasMessageContaining("could not run")
                .hasMessageContaining("no such directory");
    }

    @Test
    void skipsWhatItAsksOfTheMachineWhenTheChecksAreOff() {
        StartupValidation validation = new StartupValidation(List.of(
                () -> List.of(new Problem("orchestrator.tmux-command", "tmux is not on PATH"))), false);

        assertThatCode(validation::refuseToRunWithoutWhatItNeeds).doesNotThrowAnyException();
    }

    @Test
    void refusesAnEmptyCaseBookEvenWithTheChecksOff() {
        StartupValidation validation = new StartupValidation(List.of(new StartupCheck() {
            @Override
            public List<Problem> problems() {
                return List.of(new Problem("session-cases.md", "no case sends step ship"));
            }

            @Override
            public boolean machine() {
                return false;
            }
        }), false);

        assertThatThrownBy(validation::refuseToRunWithoutWhatItNeeds).isInstanceOf(Misconfigured.class);
    }
}
