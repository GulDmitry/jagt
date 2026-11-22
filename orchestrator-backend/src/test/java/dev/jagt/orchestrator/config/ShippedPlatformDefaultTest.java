package dev.jagt.orchestrator.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

class ShippedPlatformDefaultTest {

  @Test
  void leavesThePlatformToDetectionWhenTheStockConfigIsUsed() throws IOException {
    var shipped =
        new YamlPropertySourceLoader()
            .load("application.yml", new ClassPathResource("application.yml"))
            .getFirst();

    assertThat(shipped.containsProperty("orchestrator.platform")).isFalse();
  }
}
