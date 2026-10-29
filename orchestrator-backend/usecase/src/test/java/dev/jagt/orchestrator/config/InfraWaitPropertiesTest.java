package dev.jagt.orchestrator.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InfraWaitPropertiesTest {

    @Test
    void waitsFiveFifteenAndFortyFiveMinutesWhenNothingIsConfigured() {
        InfraWaitProperties properties = new Binder(new MapConfigurationPropertySource(Map.of()))
                .bind("orchestrator.infra-wait", InfraWaitProperties.class)
                .orElseGet(() -> new InfraWaitProperties(null));

        assertThat(properties.backoff()).containsExactly(Duration.ofMinutes(5), Duration.ofMinutes(15),
                Duration.ofMinutes(45));
    }

    @Test
    void waitsForNothingWhenTheConfiguredListIsEmpty() {
        InfraWaitProperties properties = new Binder(new MapConfigurationPropertySource(
                Map.of("orchestrator.infra-wait.backoff", ""))).bind("orchestrator.infra-wait", InfraWaitProperties.class).get();

        assertThat(properties.waits()).isFalse();
    }
}
