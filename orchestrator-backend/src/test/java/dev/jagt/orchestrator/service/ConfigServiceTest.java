package dev.jagt.orchestrator.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigServiceTest {

    static Stream<Arguments> intervals() {
        return Stream.of(
                Arguments.of(null, 10),
                Arguments.of(-5, 0),
                Arguments.of(0, 0),
                Arguments.of(30, 30));
    }

    @ParameterizedTest
    @MethodSource("intervals")
    void resolvesTheDashboardRefreshInterval(Integer configured, int expected) {
        ConfigService.ConfigFile config = new ConfigService.ConfigFile(
                Map.of(), null, null, null, null, null, null, null, null, configured);

        assertThat(config.dashboardRefreshSecondsOrDefault()).isEqualTo(expected);
    }
}
