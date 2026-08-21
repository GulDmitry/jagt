package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TicketFactsTest {

    @ParameterizedTest
    @CsvSource({"false,ABC-42,Widget layout is off,https://tracker/ABC-42",
            "true,,Widget layout is off,https://tracker/ABC-42",
            "true,ABC-42,,https://tracker/ABC-42",
            "true,ABC-42,'  ',https://tracker/ABC-42",
            "true,ABC-42,Widget layout is off,",
            "true,ABC-42,Widget layout is off,'  '"})
    void isNotUsableWhenTheReadLeftOutSomethingAnExistingItemMustHave(boolean exists, String key, String title,
                                                                     String url) {
        TicketFacts read = new TicketFacts(exists, key, title, "ABC", List.of(), url);

        assertThat(read.usable()).isFalse();
    }

    @Test
    void isUsableWhenTheReadNamedTheItemItsTitleAndItsLink() {
        TicketFacts read = new TicketFacts(true, "ABC-42", "Widget layout is off", "ABC", List.of(),
                "https://tracker/ABC-42");

        assertThat(read.usable()).isTrue();
    }
}
