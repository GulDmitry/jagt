package dev.jagt.orchestrator.adapter.values;

import dev.jagt.orchestrator.config.source.RenamedKeys;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Binds a renamed key's old name to its new one where the new one is not set. Runs before {@link
 * ConfiguredValues}, so a {@code ${file:...}} value is expanded as any other.
 */
public class RenamedKeysAlias implements EnvironmentPostProcessor, Ordered {

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    Binder binder = Binder.get(environment);
    Map<String, Object> aliased = new LinkedHashMap<>();
    for (RenamedKeys.Rename rename : RenamedKeys.all()) {
      if (binder.bind(rename.newProperty(), String.class).isBound()) {
        continue;
      }
      binder
          .bind(rename.oldProperty(), String.class)
          .ifBound(value -> aliased.put(rename.newProperty(), value));
    }
    if (!aliased.isEmpty()) {
      environment.getPropertySources().addLast(new MapPropertySource("renamedKeysAlias", aliased));
    }
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE - 1;
  }
}
