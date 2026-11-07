package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PatternLinesTest {

  @Test
  void aPatternTheMasterStopsOnUsesNoScaleOrTasteWord() throws Exception {
    String brief = Files.readString(Path.of("../roles/master/brief.md.dist"));
    Matcher section =
        Pattern.compile("(?s)## Patterns that stop a round\\n(.*?)\\n## ").matcher(brief);
    Pattern scaleOrTaste =
        Pattern.compile(
            "\\b(appropriate|appropriately|properly|reasonable|reasonably|roughly"
                + "|approximately|clean|nice|good|polite|helpful|professional|expected)\\b",
            Pattern.CASE_INSENSITIVE);

    assertThat(section.find()).describedAs("the patterns section").isTrue();
    assertThat(
            Pattern.compile("\\n(?=- )")
                .splitAsStream(section.group(1))
                .filter(bullet -> bullet.startsWith("- "))
                .filter(bullet -> scaleOrTaste.matcher(bullet).find()))
        .isEmpty();
  }
}
