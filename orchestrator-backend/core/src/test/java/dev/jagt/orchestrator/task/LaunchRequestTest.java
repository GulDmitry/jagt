package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LaunchRequestTest {

    @Test
    void treatsFreeTextAfterPlanAsNotesNotAProject() {
        LaunchRequest typed = LaunchRequest.ofLine("ABC-42 plan walk me through it", Set.of("demo", "widgets"));

        assertThat(typed.ref()).isEqualTo("ABC-42");
        assertThat(typed.project()).isNull();
        assertThat(typed.mode()).isEqualTo("plan");
        assertThat(typed.notes()).isEqualTo("walk me through it");
    }

    @Test
    void readsTheBaseBranchAfterFromAndKeepsTheRestAsNotes() {
        LaunchRequest typed = LaunchRequest.ofLine("ABC-1 from feature/parent demo keep the API stable",
                Set.of("demo"));

        assertThat(typed.baseBranch()).isEqualTo("feature/parent");
        assertThat(typed.project()).isEqualTo("demo");
        assertThat(typed.notes()).isEqualTo("keep the API stable");
    }

    @Test
    void refusesFromWithoutABranchInsteadOfSwallowingTheNextWord() {
        assertThatThrownBy(() -> LaunchRequest.ofLine("ABC-1 from", Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("`from` needs the branch");
    }

    @Test
    void readsSeveralProjectsFromOneTokenForWorkThatSpansThem() {
        LaunchRequest typed = LaunchRequest.ofLine("ABC-1 demo,widgets keep them in step",
                Set.of("demo", "widgets"));

        assertThat(typed.project()).isEqualTo("demo,widgets");
        assertThat(typed.notes()).isEqualTo("keep them in step");
    }

    @Test
    void keepsATokenAsNotesWhenOnlySomeOfItsCommaPartsAreProjects() {
        LaunchRequest typed = LaunchRequest.ofLine("ABC-1 demo,whatever", Set.of("demo"));

        assertThat(typed.project()).isNull();
        assertThat(typed.notes()).isEqualTo("demo,whatever");
    }

    @Test
    void readsTheBranchStrategyAsAModifierRatherThanAsNotes() {
        LaunchRequest typed = LaunchRequest.ofLine("ABC-1 recreate", Set.of("demo"));

        assertThat(typed.strategy()).isEqualTo("recreate");
        assertThat(typed.notes()).isNull();
    }

    @Test
    void namesTheUsageWhenNoTicketWasTyped() {
        assertThatThrownBy(() -> LaunchRequest.ofLine("", Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("usage: do <ticket|url>");
    }
}
