package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.port.LineChange;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiffUtilsLineDiffTest {

    @Test
    void marksNothingWhereThePlanIsUnchanged() {
        LineChange change = new DiffUtilsLineDiff().between("# Plan\n- a\n- b\n", "# Plan\n- a\n- b\n");

        assertThat(change.changed()).isEmpty();
        assertThat(change.removed()).isEmpty();
    }

    @Test
    void marksAnAddedLineAndNothingElse() {
        LineChange change = new DiffUtilsLineDiff().between("# Plan\n- a\n", "# Plan\n- a\n- b\n");

        assertThat(change.changed()).containsExactly(2);
        assertThat(change.removed()).isEmpty();
    }

    @Test
    void marksARewrittenLineAndKeepsTheOldOneAsRemovedBeforeIt() {
        LineChange change = new DiffUtilsLineDiff().between("# Plan\n- retry once\n- ship\n", "# Plan\n- retry twice\n- ship\n");

        assertThat(change.changed()).containsExactly(1);
        assertThat(change.removed()).containsEntry(1, List.of("- retry once"));
    }

    @Test
    void keepsADeletedLineWithoutMarkingAnythingChanged() {
        LineChange change = new DiffUtilsLineDiff().between("# Plan\n- a\n- b\n", "# Plan\n- b\n");

        assertThat(change.changed()).isEmpty();
        assertThat(change.removed()).containsEntry(1, List.of("- a"));
    }
}
