package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
        TicketFacts read = TicketFacts.defaults().withExists(exists).withKey(key).withTitle(title).withUrl(url);

        assertThat(read.usable()).isFalse();
    }

    @Test
    void isUsableWhenTheReadNamedTheItemItsTitleAndItsLink() {
        TicketFacts read = TicketFacts.defaults().withExists(true).withKey("ABC-42")
                .withTitle("Widget layout is off").withUrl("https://tracker/ABC-42");

        assertThat(read.usable()).isTrue();
    }
}
