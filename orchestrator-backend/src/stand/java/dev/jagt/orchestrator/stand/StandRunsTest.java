package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class StandRunsTest {

    @Test
    void repeatsAScenarioInEveryModeAsOftenAsAsked() {
        List<StandRuns.Run> runs = StandRuns.selected("plan", "", "", 2);

        assertThat(runs).containsExactly(new StandRuns.Run(StandMode.JUDGE, 1), new StandRuns.Run(StandMode.JUDGE, 2),
                new StandRuns.Run(StandMode.ACT, 1), new StandRuns.Run(StandMode.ACT, 2));
    }

    @Test
    void runsNothingOfAScenarioTheSelectionLeavesOut() {
        List<StandRuns.Run> runs = StandRuns.selected("plan", "deploy-conflict,wrong-thread", "", 1);

        assertThat(runs).isEmpty();
    }

    @Test
    void runsOnlyTheModeAskedFor() {
        List<StandRuns.Run> runs = StandRuns.selected("plan", "plan", "act", 1);

        assertThat(runs).containsExactly(new StandRuns.Run(StandMode.ACT, 1));
    }
}
