package dev.jagt.orchestrator;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionIndexTest {

    @Test
    void indexesEveryRecordedDecision() throws Exception {
        String index = Files.readString(Path.of("../docs/decisions/README.md"));
        try (Stream<Path> files = Files.list(Path.of("../docs/decisions"))) {
            assertThat(files.map(file -> file.getFileName().toString()).filter(name -> name.matches("\\d{4}-.*\\.md")))
                    .isNotEmpty()
                    .allSatisfy(record -> assertThat(index).contains("(" + record + ")"));
        }
    }
}
