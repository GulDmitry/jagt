package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.port.CheckerReader;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReplayCheckerReaderTest {

    private final CheckerReader.Run run = new CheckerReader.Run("", "prompt", Path.of("/wt"), "abc123", "",
            List.of(), "{}", "qa");

    @Test
    void saysTheRecordingHoldsNoQaCheckWhenAskedToRead() {
        var answer = new ReplayCheckerReader().read(run);

        assertThat(answer.facts().orElseThrow().toString()).contains("the recording holds no QA check");
    }

    @Test
    void saysTheRecordingHoldsNoQaCheckWhenAskedToReconsider() {
        var answer = new ReplayCheckerReader().reconsider(run);

        assertThat(answer.facts().orElseThrow().toString()).contains("the recording holds no QA check");
    }
}
