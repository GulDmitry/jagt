package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.adapter.host.SystemHost;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * The lowest-priority source of {@code orchestrator.platform}: any configured value outranks it.
 */
public class DetectedPlatform implements EnvironmentPostProcessor {

  private static final String KEY = "orchestrator.platform";

  public static Optional<String> of(String osName) {
    String name = osName.toLowerCase(Locale.ROOT);
    if (name.contains("linux")) {
      return Optional.of("linux");
    }
    return name.contains("mac") ? Optional.of("macos") : Optional.empty();
  }

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    of(new SystemHost().osName())
        .ifPresent(platform ->
            environment
                .getPropertySources()
                .addLast(new MapPropertySource("detectedPlatform", Map.of(KEY, platform))));
  }
}
