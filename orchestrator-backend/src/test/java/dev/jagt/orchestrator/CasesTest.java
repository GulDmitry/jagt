package dev.jagt.orchestrator;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class CasesTest {

    @Test
    void everyCaseIdAppearsOnce() throws Exception {
        String cases = Files.readString(Path.of("../docs/cases.md"));
        var ids = Pattern.compile("(?m)^\\| ([A-M]\\d+) \\|").matcher(cases).results().map(match -> match.group(1)).toList();

        assertThat(ids).isNotEmpty().doesNotHaveDuplicates();
    }
}
