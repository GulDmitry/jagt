package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModesTest {

    @ParameterizedTest
    @CsvSource({"OFF,false,false", "TAKE,true,false", "CLOSE,false,true", "BOTH,true,true"})
    void drivesTheEndsOfTheLoopTheTrackerModeNames(TrackerMode mode, boolean takes, boolean closes) {
        assertThat(mode.takes()).isEqualTo(takes);
        assertThat(mode.closes()).isEqualTo(closes);
    }

    @ParameterizedTest
    @CsvSource({"take,TAKE", "' Both ',BOTH", "CLOSE,CLOSE"})
    void readsATrackerModeByItsWord(String word, TrackerMode mode) {
        assertThat(TrackerMode.of(word)).contains(mode);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void leavesTheTrackerOffWhenNoModeWasWritten(String word) {
        assertThat(TrackerMode.of(word)).contains(TrackerMode.OFF);
    }

    @Test
    void refusesATrackerModeNoneOfTheWordsMean() {
        assertThat(TrackerMode.of("sometimes")).isEmpty();
        assertThat(TrackerMode.ids()).containsExactly("off", "take", "close", "both");
    }

    @ParameterizedTest
    @CsvSource({"act,ACT", "' Judge ',JUDGE", "OFF,OFF"})
    void readsAMasterModeByItsWord(String word, MasterMode mode) {
        assertThat(MasterMode.of(word)).contains(mode);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void judgesWhenNoMasterModeWasWritten(String word) {
        assertThat(MasterMode.of(word)).contains(MasterMode.JUDGE);
    }

    @Test
    void refusesAMasterModeNoneOfTheWordsMean() {
        assertThat(MasterMode.of("sometimes")).isEmpty();
        assertThat(MasterMode.ids()).containsExactly("off", "judge", "act");
    }

    @ParameterizedTest
    @CsvSource({"deploy,DEPLOY", "' Reply ',REPLY", "PLAN,PLAN", "repo,REPO"})
    void readsAMasterRightByItsWord(String word, MasterRight right) {
        assertThat(MasterRight.of(word)).contains(right);
    }

    @Test
    void refusesAMasterRightNoneOfTheWordsMean() {
        assertThat(MasterRight.of("sometimes")).isEmpty();
        assertThat(MasterRight.of(null)).isEmpty();
        assertThat(MasterRight.ids()).containsExactly("ship", "deploy", "revert", "reply", "answer", "plan", "repo");
    }

    @ParameterizedTest
    @CsvSource({"recreate,RECREATE", "' Resume ',RESUME", "FRESH,FRESH"})
    void readsABranchStrategyByItsWord(String word, BranchStrategy strategy) {
        assertThat(BranchStrategy.of(word)).isEqualTo(strategy);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void startsFreshWhenNoBranchStrategyWasWritten(String word) {
        assertThat(BranchStrategy.of(word)).isEqualTo(BranchStrategy.FRESH);
    }

    @Test
    void refusesABranchStrategyNoneOfTheWordsMeanAndListsTheAllowedOnes() {
        assertThatThrownBy(() -> BranchStrategy.of("sometimes"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown branchStrategy 'sometimes'. Allowed: fresh, recreate, resume");
    }

    @Test
    void offersEveryBranchStrategyWithTheHintItIsDescribedBy() {
        assertThat(BranchStrategy.choices()).containsExactly(
                new BranchStrategy.Choice("fresh", "stop if the branch exists"),
                new BranchStrategy.Choice("recreate", "delete the branch, start over from the base"),
                new BranchStrategy.Choice("resume", "continue the branch and its commits"));
    }
}
