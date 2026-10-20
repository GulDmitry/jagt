package dev.jagt.orchestrator.service.master;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FindingMatchTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            - [qa] Foo.java — the delay is never pinned (unguarded delay)     | true
            - [developer] Foo.java — no test applies the delay (unguarded delay) | true
            - [qa] Foo.java — the cutover is automated again (settled decision overridden) | true
            - [qa] Bar.java — the delay is never pinned (unguarded delay)     | false
            - [qa] Foo.java — the lock is taken twice (double lock)           | false
            - asked: unguarded delay? — decided: pin it                        | false
            """)
    void repeatsAFindingOnlyOnTheSameFileUnderTheSamePattern(String finding, boolean repeats) {
        List<String> earlier = List.of("- [qa] Foo.java — every test injects zero (unguarded delay)",
                "- [chaplain] Foo.java — the cutover is automated (settled decision reopened)");

        assertThat(FindingMatch.repeats(finding, earlier)).isEqualTo(repeats);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            - [qa] Foo.java — the retry loop never stops on a closed socket | true
            - [qa] Foo.java — the parser drops the trailing newline       | false
            """)
    void matchesAFindingNamingNoPatternOnItsIssue(String finding, boolean repeats) {
        List<String> earlier = List.of("- [qa] Foo.java — the retry loop never stops on a closed socket");

        assertThat(FindingMatch.repeats(finding, earlier)).isEqualTo(repeats);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            - [qa] Foo.java — x (add key) | true
            - [qa] Foo.java — x (do it)   | false
            """)
    void countsOnlyWordsOfThreeLettersOrMoreWhenMatchingAPattern(String finding, boolean repeats) {
        List<String> earlier = List.of(finding);

        assertThat(FindingMatch.repeats(finding, earlier)).isEqualTo(repeats);
    }
}
