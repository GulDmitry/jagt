package dev.jagt.orchestrator.adapter.values;

import dev.jagt.orchestrator.config.source.ConfigInputs;
import dev.jagt.orchestrator.config.source.ConfigResolver;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

/**
 * The keys Spring binds take {@code ${file:/path}} and {@code JAGT__a__b} as the rest of {@code
 * jagt.yml} does; Spring's own {@code ${VAR}} stays Spring's.
 */
final class SpringValues {

  private final ConfigResolver resolver;

  SpringValues(ConfigInputs inputs) {
    this.resolver = new ConfigResolver(inputs);
  }

  void apply(ConfigurableEnvironment environment) {
    for (PropertySource<?> source : environment.getPropertySources()) {
      if (source instanceof EnumerablePropertySource<?> file) {
        Map<String, Object> read = filesRead(file);
        if (!read.isEmpty()) {
          environment
              .getPropertySources()
              .addBefore(
                  file.getName(), new MapPropertySource("jagtFiles:" + file.getName(), read));
        }
      }
    }
    Map<String, Object> overrides = new LinkedHashMap<>(resolver.dottedOverrides());
    if (!overrides.isEmpty()) {
      environment
          .getPropertySources()
          .addFirst(new MapPropertySource("jagtEnvironment", overrides));
    }
  }

  private Map<String, Object> filesRead(EnumerablePropertySource<?> file) {
    Map<String, Object> read = new LinkedHashMap<>();
    for (String name : file.getPropertyNames()) {
      if (file.getProperty(name) instanceof String text && text.contains("${file:")) {
        read.put(name, resolver.expandFiles(name, text));
      }
    }
    return read;
  }
}
