package dev.jagt.orchestrator.stand.mutation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.jagt.orchestrator.stand.StandMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class MutationRunsTest {

    @Test
    void runsTheMutationTheSeedPicksWhenNoneIsNamed() {
        List<MutationRuns.Run> runs = MutationRuns.selected(MutationBook.shipped(), "", 15, "act", 1);

        assertThat(runs).extracting(run -> run.mutation().name(), MutationRuns.Run::mode)
                .containsExactly(tuple("contradicting-ticket", StandMode.ACT));
    }

    @Test
    void runsEveryNamedMutationInEveryModeWhateverTheSeed() {
        List<MutationRuns.Run> runs = MutationRuns.selected(MutationBook.shipped(), "deploy-conflict, self-deploy", 15, "", 1);

        assertThat(runs).extracting(run -> run.mutation().name() + "/" + run.mode().id()).containsExactly(
                "deploy-conflict/judge", "deploy-conflict/act", "self-deploy/judge", "self-deploy/act");
    }
}
