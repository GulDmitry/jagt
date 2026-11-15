package dev.jagt.orchestrator.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class DetectedPlatformTest {

  @ParameterizedTest
  @CsvSource({"Linux, linux", "Mac OS X, macos"})
  void namesThePlatformTheMachineReports(String osName, String expected) {
    assertThat(DetectedPlatform.of(osName)).contains(expected);
  }

  @Test
  void namesNoPlatformForAnOperatingSystemJagtDoesNotDrive() {
    assertThat(DetectedPlatform.of("Windows 11")).isEmpty();
  }

  @Test
  void letsTheConfiguredPlatformOutrankTheDetectedOne() {
    StandardEnvironment environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .addFirst(new MapPropertySource("jagt.yml", Map.of("orchestrator.platform", "overridden")));

    new DetectedPlatform().postProcessEnvironment(environment, new SpringApplication());

    assertThat(environment.getProperty("orchestrator.platform")).isEqualTo("overridden");
  }
}
