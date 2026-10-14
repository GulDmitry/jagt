package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.UsageTracker;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MasterCommandTest {

    private final ConfigService configService = mock(ConfigService.class);
    private final MasterRecord record = mock(MasterRecord.class);

    @Test
    void offersALessonForTheTaskYouRevertedAfterItPassed() {
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null)));
        when(record.seen()).thenReturn(new MasterRecord.Seen(1, 1, 0, 0, 1, 0));
        when(record.latestDisagreement()).thenReturn(Optional.of(new MasterRecord.Disagreement("ABC-42", true)));

        String report = new MasterCommand(configService, mock(UsageTracker.class), record).run("");

        assertThat(report).contains("\n  lesson:    ABC-42 passed, then you reverted it — to teach the Master, run"
                + " /master-lesson with its master-review.md, what you said and why");
    }
}
