package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.port.KeepAwake;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.AutoReviewConfig;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.MasterConfig;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.TrackerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KeepAwakeJobTest {

    private final ConfigService config = mock(ConfigService.class);
    private final KeepAwake keepAwake = mock(KeepAwake.class);

    static Stream<ConfigFile> unattendedWork() {
        return Stream.of(
                ConfigFile.defaults().withMaster(MasterConfig.defaults().withMode("act")),
                ConfigFile.defaults().withAutoReview(AutoReviewConfig.defaults().withEnabled(true)),
                ConfigFile.defaults().withTracker(TrackerConfig.defaults().withMode("take")));
    }

    @ParameterizedTest
    @MethodSource("unattendedWork")
    void holdsTheMachineAwakeWhileUnattendedWorkIsOn(ConfigFile file) {
        when(config.load()).thenReturn(file);

        new KeepAwakeJob(config, keepAwake).run();

        verify(keepAwake).hold();
    }

    @Test
    void releasesTheMachineOnceNoUnattendedWorkIsOn() {
        when(config.load()).thenReturn(ConfigFile.defaults());

        new KeepAwakeJob(config, keepAwake).run();

        verify(keepAwake).release();
        verify(keepAwake, never()).hold();
    }

    @Test
    void holdsNothingWhenKeepAwakeIsOff() {
        when(config.load()).thenReturn(ConfigFile.defaults().withMaster(MasterConfig.defaults().withMode("act")));

        new ApplicationContextRunner()
                .withBean(ConfigService.class, () -> config).withBean(KeepAwake.class, () -> keepAwake)
                .withUserConfiguration(KeepAwakeJob.class)
                .run(context -> assertThat(context).doesNotHaveBean(KeepAwakeJob.class));

        verifyNoInteractions(keepAwake);
    }
}
