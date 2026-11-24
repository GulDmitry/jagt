package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * What {@code roles/code-reviewer/review.yml} over its {@code .dist} says, read on every call. The
 * role is on unless {@code jagt.yml} turns it off; a value that cannot be read leaves its default.
 */
@Component
@RequiredArgsConstructor
public class CodeReviewSettings {

  public static final String ROLE = "code-reviewer";

  /**
   * {@code maxDiffLines} is an estimate: a diff past it is skipped. {@code maxCostUsd} is what the
   * third-party reads of one request may cost together, the assistant's own ceiling.
   */
  public record Settings(boolean enabled, boolean postOthers, int maxDiffLines, double maxCostUsd) {

    public static Settings defaults() {
      return new Settings(true, false, 5000, AssistantProperties.DEFAULT_MAX_COST_USD);
    }
  }

  private final ConfigService configService;
  private final OrchestratorPaths paths;
  private final AssistantProperties assistant;

  public Settings load() {
    Settings read = fromFiles(paths.root().resolve("roles").resolve(ROLE));
    Boolean enabled = configService.load().role(ROLE).enabled();
    return new Settings(
        enabled == null || enabled, read.postOthers(), read.maxDiffLines(), assistant.maxCostUsd());
  }

  private static Settings fromFiles(Path folder) {
    Settings settings = Settings.defaults();
    for (String name : List.of("review.yml.dist", "review.yml")) {
      Path file = folder.resolve(name);
      if (Files.isRegularFile(file)) {
        settings = layered(settings, file);
      }
    }
    return settings;
  }

  private static Settings layered(Settings below, Path file) {
    Object tree;
    try {
      tree = new Yaml().load(Files.readString(file));
    } catch (IOException | YAMLException | ClassCastException unread) {
      return below;
    }
    Map<?, ?> document = tree instanceof Map<?, ?> found ? found : Map.of();
    Map<?, ?> post = document.get("post") instanceof Map<?, ?> named ? named : Map.of();
    return new Settings(
        below.enabled(),
        flag(post.get("others"), below.postOthers()),
        Stream.of(document.get("maxDiffLines"))
            .filter(Integer.class::isInstance)
            .map(Integer.class::cast)
            .filter(lines -> lines > 0)
            .findFirst()
            .orElse(below.maxDiffLines()),
        below.maxCostUsd());
  }

  private static boolean flag(@Nullable Object written, boolean standing) {
    return written instanceof Boolean value ? value : standing;
  }
}
