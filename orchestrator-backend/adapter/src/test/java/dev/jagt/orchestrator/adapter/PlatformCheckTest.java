package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.config.OrchestratorProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

class PlatformCheckTest {

    @ParameterizedTest
    @CsvSource({"macos, Linux, linux", "linux, Mac OS X, macos"})
    void refusesToStartWhenTheConfiguredPlatformIsNotTheOneTheMachineReports(String configured, String osName,
                                                                            String expected) {
        OrchestratorProperties properties =
                OrchestratorProperties.defaults().withPlatform(configured).withTerminal("kitty");

        assertThat(new PlatformCheck(properties, osName).problems())
                .singleElement(STRING)
                .contains("orchestrator.platform", "Set it to '" + expected + "'");
    }

    @Test
    void takesAnUnsetPlatformForMacosBecauseThatIsWhatEveryUnsetOneSelects() {
        OrchestratorProperties properties = OrchestratorProperties.defaults().withTerminal("kitty");

        assertThat(new PlatformCheck(properties, "Mac OS X").problems()).isEmpty();
    }

    @Test
    void saysThereIsNoPlatformToSetOnAnOperatingSystemJagtDoesNotDrive() {
        OrchestratorProperties properties =
                OrchestratorProperties.defaults().withPlatform("macos").withTerminal("kitty");

        assertThat(new PlatformCheck(properties, "Windows 11").problems())
                .singleElement(STRING)
                .contains("macOS and Linux", "Windows 11");
    }

    @Test
    void refusesTheWarpTerminalWhereItsUriSchemeReachesNothing() {
        OrchestratorProperties properties =
                OrchestratorProperties.defaults().withPlatform("linux").withTerminal("warp");

        assertThat(new PlatformCheck(properties, "Linux").problems())
                .singleElement(STRING)
                .contains("orchestrator.terminal", "'kitty'");
    }

    @Test
    void keepsQuietAboutTheTerminalTheMachineCanOpen() {
        OrchestratorProperties properties =
                OrchestratorProperties.defaults().withPlatform("macos").withTerminal("warp");

        assertThat(new PlatformCheck(properties, "Mac OS X").problems()).isEmpty();
    }
}
