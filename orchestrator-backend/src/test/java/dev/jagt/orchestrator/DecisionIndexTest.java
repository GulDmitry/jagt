package dev.jagt.orchestrator;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    @Test
    void anAmendedOrSupersededRecordNamesItsAmenderInItsStatus() throws Exception {
        Pattern status = Pattern.compile("(?s)## Status\\s*(.*?)\\n## ");
        Pattern forward = Pattern.compile("(?i)\\b(?:amends|supersedes)\\b[^.:;]*");
        List<String> unnamed = new ArrayList<>();
        try (Stream<Path> files = Files.list(Path.of("../docs/decisions"))) {
            for (Path record : files.filter(file -> file.getFileName().toString().matches("\\d{4}-.*\\.md")).toList()) {
                String number = record.getFileName().toString().substring(0, 4);
                Matcher text = status.matcher(Files.readString(record));
                String own = text.find() ? text.group(1).replaceAll("\\[(\\d{4})]\\([^)]*\\)", "$1") : "";
                Matcher claim = forward.matcher(own);
                while (claim.find()) {
                    Matcher target = Pattern.compile("\\d{4}").matcher(claim.group());
                    while (target.find()) {
                        Path other = Files.list(Path.of("../docs/decisions"))
                                .filter(file -> file.getFileName().toString().startsWith(target.group() + "-"))
                                .findFirst().orElseThrow();
                        Matcher back = status.matcher(Files.readString(other));
                        if (!back.find() || !back.group(1).contains(number)) {
                            unnamed.add(target.group() + " does not name " + number);
                        }
                    }
                }
            }
        }
        assertThat(unnamed).isEmpty();
    }
}
