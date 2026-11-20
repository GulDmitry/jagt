package dev.jagt.orchestrator.config.source;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.source.ConfigResolver.Resolution;
import dev.jagt.orchestrator.flow.role.Arbiter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

/**
 * {@code jagt.yml} read on every access, parsed again only when the file, a variable it names, a
 * {@code JAGT__} variable or a file it names changed. No file is an empty document: the defaults.
 */
@Component
@Slf4j
public class ConfigSource {

  /** The one root everything a human writes lives under, Spring's keys and jagt's own alike. */
  private static final String ROOT = "orchestrator";

  private final OrchestratorPaths paths;
  private final ConfigResolver resolver;
  private volatile @Nullable Parsed parsed;

  public ConfigSource(OrchestratorPaths paths, ConfigInputs inputs) {
    this.paths = paths;
    this.resolver = new ConfigResolver(inputs);
  }

  public Path clone(String projectKey) {
    return paths.clone(projectKey);
  }

  private record Parsed(FileVersion version, Resolution resolution, @Nullable Object section) {}

  public @Nullable Object section() {
    Path file = paths.configFile();
    if (!Files.exists(file)) {
      return rootOf(resolver.resolve(new java.util.LinkedHashMap<>()));
    }
    FileVersion version = FileVersion.of(file);
    Parsed last = parsed;
    if (last != null && last.version().equals(version) && resolver.holds(last.resolution())) {
      return last.section();
    }
    try {
      // SafeConstructor: the file is hand-edited, and a YAML tag naming a class is not a setting.
      Object tree =
          new Yaml(
                  new SafeConstructor(new LoaderOptions()),
                  new Representer(new DumperOptions()),
                  new DumperOptions(),
                  new WordsStayWords())
              .load(Files.readString(file));
      Resolution resolution = resolver.resolve(tree);
      Object section = rootOf(resolution);
      parsed = version == null ? null : new Parsed(version, resolution, section);
      report(resolution, section);
      return section;
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read " + file, e);
    } catch (ConfigValueRefused refused) {
      throw refused;
    } catch (RuntimeException malformed) {
      throw new IllegalStateException(
          "Cannot read " + file + ": " + malformed.getMessage(), malformed);
    }
  }

  private static @Nullable Object rootOf(Resolution resolution) {
    return resolution.document() instanceof Map<?, ?> document ? document.get(ROOT) : null;
  }

  private static void report(Resolution resolution, @Nullable Object section) {
    if (section instanceof Map<?, ?> keys && keys.containsKey(Arbiter.ID)) {
      log.atInfo()
          .setMessage("config key deprecated")
          .addKeyValue("key", "orchestrator.master")
          .addKeyValue("use", "orchestrator.roles.master")
          .log();
    }
    if (!resolution.overrides().isEmpty()) {
      log.atInfo()
          .setMessage("config overridden from the environment")
          .addKeyValue("variables", resolution.overrides().keySet())
          .log();
    }
  }

  /**
   * YAML 1.1 reads `off`, `on`, `yes` and `no` as booleans, and `mode: off` would arrive as
   * "false".
   */
  private static final class WordsStayWords extends Resolver {

    private static final Pattern WORD = Pattern.compile("(?i)on|off|yes|no|y|n");

    @Override
    public Tag resolve(NodeId kind, String value, boolean implicit) {
      return kind == NodeId.scalar && implicit && WORD.matcher(value).matches()
          ? Tag.STR
          : super.resolve(kind, value, implicit);
    }
  }
}
