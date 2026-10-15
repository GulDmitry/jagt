package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.Problem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformCheckTest {

    @ParameterizedTest
    @CsvSource({"macos, Linux, linux", "linux, Mac OS X, macos"})
    void refusesToStartWhenTheConfiguredPlatformIsNotTheOneTheMachineReports(String configured, String osName,
                                                                            String expected) {
        OrchestratorProperties properties =
                OrchestratorProperties.defaults().withPlatform(configured);

        List<Problem> problems = new PlatformCheck(properties, osName).problems();

        assertThat(problems).extracting(Problem::key).containsExactly("orchestrator.platform");
        assertThat(problems.getFirst().text()).contains("Set it to '" + expected + "'");
    }

    @Test
    void takesAnUnsetPlatformForMacosBecauseThatIsWhatEveryUnsetOneSelects() {
        OrchestratorProperties properties = OrchestratorProperties.defaults();

        assertThat(new PlatformCheck(properties, "Mac OS X").problems()).isEmpty();
    }

    @Test
    void saysThereIsNoPlatformToSetOnAnOperatingSystemJagtDoesNotDrive() {
        OrchestratorProperties properties =
                OrchestratorProperties.defaults().withPlatform("macos");

        assertThat(new PlatformCheck(properties, "Windows 11").problems()).extracting(Problem::key)
                .containsExactly("orchestrator.platform");
    }
}
