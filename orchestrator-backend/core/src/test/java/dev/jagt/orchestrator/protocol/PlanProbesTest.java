package dev.jagt.orchestrator.protocol;

import dev.jagt.orchestrator.task.Probe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanProbesTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "command ./gradlew test --tests FooTest | COMMAND | ./gradlew,test,--tests,FooTest | 0",
            "command exit=1 ./scripts/finds-nothing  | COMMAND | ./scripts/finds-nothing           | 1",
            "command sh -c 'git status'               | COMMAND | sh,-c,git status                  | 0",
            "ref refs/tags/v1                         | REF     | refs/tags/v1                      | 0",
            "artifact docs/guide.md                   | ARTIFACT| docs/guide.md                     | 0",
    })
    void readsEachProbeKindFromItsLine(String line, Probe.Kind kind, String words, int exit) {
        PlanProbes.Read read = PlanProbes.of("# Plan\n\n```probes\n" + line + "\n```\n");

        assertThat(read.violations()).isEmpty();
        assertThat(read.probes()).containsExactly(Probe.specified(kind, List.of(words.split(",")), exit));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "command ./gradlew test | tee out.log",
            "command echo $HOME",
            "command ./a && ./b",
            "command 'unclosed",
            "command exit=300 ./check",
            "command exit=1",
            "ref --all",
            "ref main extra",
            "artifact ../outside.md",
            "artifact /etc/hosts",
            "artifact ~/notes.md",
            "deploy now",
            "command",
    })
    void refusesALineNoProbeKindReads(String line) {
        PlanProbes.Read read = PlanProbes.of("```probes\n" + line + "\n```\n");

        assertThat(read.probes()).isEmpty();
        assertThat(read.violations()).singleElement().asString().contains(line);
    }

    @Test
    void readsNothingOutsideTheProbesBlock() {
        PlanProbes.Read read = PlanProbes.of("```\ncommand rm -rf build\n```\ncommand ./gradlew test\n");

        assertThat(read.probes()).isEmpty();
        assertThat(read.violations()).isEmpty();
    }

    @Test
    void refusesABlockNeverClosed() {
        PlanProbes.Read read = PlanProbes.of("```probes\nref main\n");

        assertThat(read.violations()).singleElement().asString().contains("close the ```probes block");
    }

    @Test
    void refusesMoreProbesThanTheCap() {
        PlanProbes.Read read = PlanProbes.of("```probes\n" + "ref main\n".repeat(11) + "```\n");

        assertThat(read.violations()).singleElement().asString().contains("at most 10 probes, not 11");
    }

    @Test
    void readsAQuotedWordWithSpacesAsOneWordWhicheverQuoteGroupsIt() {
        PlanProbes.Read read = PlanProbes.of("```probes\ncommand sh -c \"git status\"\ncommand echo 'a b' \"c d\"\n```\n");

        assertThat(read.violations()).isEmpty();
        assertThat(read.probes()).containsExactly(
                Probe.specified(Probe.Kind.COMMAND, List.of("sh", "-c", "git status"), 0),
                Probe.specified(Probe.Kind.COMMAND, List.of("echo", "a b", "c d"), 0));
    }

    @Test
    void refusesALineWithAnUnclosedDoubleQuote() {
        PlanProbes.Read read = PlanProbes.of("```probes\ncommand echo \"unclosed\n```\n");

        assertThat(read.probes()).isEmpty();
        assertThat(read.violations()).singleElement().asString().contains("command echo \"unclosed");
    }

    @Test
    void readsAnExitCodeUpToTheLargestAProcessCanGive() {
        PlanProbes.Read read = PlanProbes.of("```probes\ncommand exit=255 ./check\n```\n");

        assertThat(read.probes()).containsExactly(Probe.specified(Probe.Kind.COMMAND, List.of("./check"), 255));
    }

    @Test
    void acceptsExactlyTheCapOfProbes() {
        PlanProbes.Read read = PlanProbes.of("```probes\n" + "ref main\n".repeat(10) + "```\n");

        assertThat(read.violations()).isEmpty();
        assertThat(read.probes()).hasSize(10);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ref main", "ref main2", "command echo 'a' \"b\" c", "command echo 'a' \"b\""})
    void readsALineWhateverItsLengthSoLongAsItsQuotesAreClosed(String line) {
        PlanProbes.Read read = PlanProbes.of("```probes\n" + line + "\n```\n");

        assertThat(read.violations()).isEmpty();
        assertThat(read.probes()).hasSize(1);
    }
}
