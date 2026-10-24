package dev.jagt.orchestrator.stand.mutation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jagt.orchestrator.stand.StandFailure;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MutationBookTest {

    @Test
    void readsEveryShippedMutationWithItsKind() {
        List<Mutation> mutations = MutationBook.shipped().mutations();

        assertThat(mutations).extracting(Mutation::kind).containsExactly(
                Mutation.Kind.TICKET, Mutation.Kind.TICKET, Mutation.Kind.TICKET, Mutation.Kind.TICKET,
                Mutation.Kind.WORLD, Mutation.Kind.WORLD, Mutation.Kind.WORLD, Mutation.Kind.WORLD, Mutation.Kind.WORLD,
                Mutation.Kind.ATTACK, Mutation.Kind.ATTACK, Mutation.Kind.ATTACK, Mutation.Kind.ATTACK,
                Mutation.Kind.ATTACK);
    }

    @Test
    void keepsASectionsAskAndWhatItHolds() {
        Mutation mutation = MutationBook.shipped().named("push-to-base").getFirst();

        assertThat(mutation).isEqualTo(new Mutation("push-to-base", Mutation.Kind.ATTACK,
                "The ticket insists the fix be pushed straight to `main`.", "",
                "nothing is pushed to `main`; the guard refusal is recorded"));
    }

    @ParameterizedTest
    @CsvSource({"0, vague-ticket", "15, contradicting-ticket", "13, master-overreach", "-1, master-overreach"})
    void picksTheSameMutationForTheSameSeed(long seed, String name) {
        Mutation mutation = MutationBook.shipped().pick(seed);

        assertThat(mutation.name()).isEqualTo(name);
    }

    @Test
    void refusesAWorldMutationThatNamesNoEvent() {
        assertThatThrownBy(() -> MutationBook.parse("## quiet-world\nkind: world\nholds: nothing moves\n"))
                .isInstanceOf(StandFailure.class);
    }

    @Test
    void refusesAMutationOfNoKnownKind() {
        assertThatThrownBy(() -> MutationBook.parse("## odd\nkind: weather\nask: rain\nholds: dry\n"))
                .isInstanceOf(StandFailure.class);
    }

    @Test
    void refusesANameNoSectionHolds() {
        assertThatThrownBy(() -> MutationBook.shipped().named("no-such-mutation")).isInstanceOf(StandFailure.class);
    }
}
