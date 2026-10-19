package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.Host;
import dev.jagt.orchestrator.port.Problem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformCheckTest {

    @ParameterizedTest
    @CsvSource({"macos, Linux, linux", "linux, Mac OS X, macos"})
    void refusesToStartWhenTheConfiguredPlatformIsNotTheOneTheMachineReports(String configured, String osName,
                                                                            String expected) {
        OrchestratorProperties properties =
                OrchestratorProperties.defaults().withPlatform(configured);

        Host host = mock(Host.class);
        when(host.osName()).thenReturn(osName);

        List<Problem> problems = new PlatformCheck(properties, host).problems();

        assertThat(problems).extracting(Problem::key).containsExactly("orchestrator.platform");
        assertThat(problems.getFirst().text()).contains("Set it to '" + expected + "'");
    }

    @Test
    void takesAnUnsetPlatformForMacosBecauseThatIsWhatEveryUnsetOneSelects() {
        OrchestratorProperties properties = OrchestratorProperties.defaults();

        Host host = mock(Host.class);
        when(host.osName()).thenReturn("Mac OS X");

        assertThat(new PlatformCheck(properties, host).problems()).isEmpty();
    }

    @Test
    void saysThereIsNoPlatformToSetOnAnOperatingSystemJagtDoesNotDrive() {
        OrchestratorProperties properties =
                OrchestratorProperties.defaults().withPlatform("macos");

        Host host = mock(Host.class);
        when(host.osName()).thenReturn("Windows 11");

        assertThat(new PlatformCheck(properties, host).problems()).extracting(Problem::key)
                .containsExactly("orchestrator.platform");
    }
}
