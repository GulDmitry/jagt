package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoundKeyTest {

    @Test
    void anUnknownComponentNeverEqualsAnother() {
        RoundKey left = RoundKey.of(List.of("l1 note_1", ""));
        RoundKey right = RoundKey.of(List.of("l1 note_1", ""));

        assertThat(left.sameAs(right)).isFalse();
    }

    @Test
    void anUnknownKeyIsNotTheSameAsItself() {
        RoundKey key = RoundKey.unknown();

        assertThat(key.sameAs(key)).isFalse();
    }

    @Test
    void knownKeysOfTheSameFactsAreTheSame() {
        RoundKey left = RoundKey.of(List.of("l1 note_1", "l2 note_2"));
        RoundKey right = RoundKey.of(List.of("l1 note_1", "l2 note_2"));

        assertThat(left.sameAs(right)).isTrue();
    }

    @Test
    void aStoredBlankTextReadsAsUnknown() {
        assertThat(RoundKey.stored("").known()).isFalse();
    }

    @Test
    void aStoredTextOfAnEarlierRunStaysTheSameAsItsKey() {
        RoundKey stored = RoundKey.stored("l1 note_1\nl2 note_2");
        RoundKey read = RoundKey.of(List.of("l1 note_1", "l2 note_2"));

        assertThat(stored.sameAs(read)).isTrue();
    }
}
