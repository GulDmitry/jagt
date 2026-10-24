package dev.jagt.orchestrator.eval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EvalStatsTest {

    @Test
    void eachCaseIsOneJsonLineAppendedToTheFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("eval-stats.jsonl");

        EvalStats.append(file, new EvalStats.Entry("2026-10-24T10:00:00Z", "masterEval", "a \"quoted\" case", true, 2, "m1"));
        EvalStats.append(file, new EvalStats.Entry("2026-10-24T10:01:00Z", "promptEval", "other", false, 3, "default"));

        assertThat(Files.readAllLines(file)).containsExactly(
                "{\"at\":\"2026-10-24T10:00:00Z\",\"suite\":\"masterEval\",\"case\":\"a \\\"quoted\\\" case\","
                        + "\"passed\":true,\"runs\":2,\"model\":\"m1\"}",
                "{\"at\":\"2026-10-24T10:01:00Z\",\"suite\":\"promptEval\",\"case\":\"other\","
                        + "\"passed\":false,\"runs\":3,\"model\":\"default\"}");
    }

    @Test
    void aFileAtOneMegabyteIsRolledToDotOneBeforeTheNextLine(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("eval-stats.jsonl");
        Files.write(file, new byte[1_048_576]);

        EvalStats.append(file, new EvalStats.Entry("2026-10-24T10:00:00Z", "masterEval", "fresh", true, 2, "m1"));

        assertThat(Files.size(dir.resolve("eval-stats.jsonl.1"))).isEqualTo(1_048_576);
        assertThat(Files.readAllLines(file)).hasSize(1).first().asString().contains("\"case\":\"fresh\"");
    }

    @Test
    void aFileBelowOneMegabyteIsNotRolled(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("eval-stats.jsonl");
        Files.write(file, new byte[1_048_575]);

        EvalStats.append(file, new EvalStats.Entry("2026-10-24T10:00:00Z", "masterEval", "fresh", true, 2, "m1"));

        assertThat(dir.resolve("eval-stats.jsonl.1")).doesNotExist();
    }
}
