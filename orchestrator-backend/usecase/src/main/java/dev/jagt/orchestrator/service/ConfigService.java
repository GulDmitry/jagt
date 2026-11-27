package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.source.ConfigSource;
import dev.jagt.orchestrator.config.source.RenamedKeys;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.role.Arbiter;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.service.stop.ActHalt;
import dev.jagt.orchestrator.task.master.MasterMode;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import dev.jagt.orchestrator.task.review.LoopBreaker;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads {@code jagt.yml} on every access, so edits are picked up without restarting — which is why
 * this parses the file itself instead of taking Spring's binding of it, that one happening once at
 * startup. A whole section may be omitted: {@link ConfigFile}'s accessors coalesce a missing one to
 * its all-default instance.
 */
@Service
@RequiredArgsConstructor
public class ConfigService implements LoopBreaker.Current {

  private static final String MASTER = Arbiter.ID;

  private static final List<String> LEGACY_ROLE_KEYS = List.of(MASTER);

  private final JsonMapper mapper = new JsonMapper();
  private final ConfigSource source;
  private final AgentRuntime agentRuntime;
  private final ActHalt actStops;
  private final Checkers checkers;
  private final KeptNumbers kept;

  public boolean fileExists() {
    return source.fileExists();
  }

  public boolean anyCheckerEnabled() {
    return load().anyCheckerEnabled(checkers.withJobs());
  }

  /** The file as written, a number outside its range included: what the start check judges. */
  public ConfigFile loadAsWritten() {
    return read(section());
  }

  public ConfigFile load() {
    Object section = section();
    if (section == null) {
      return read(null);
    }
    ConfigFile written = read(section);
    return kept.of(section, written).map(this::read).orElse(written);
  }

  private ConfigFile read(@Nullable Object section) {
    if (section == null) {
      return masterByDefault(ConfigFile.defaults());
    }
    ConfigFile config = mapper.convertValue(layered(section), ConfigFile.class);
    return masterByDefault(
        config.projects() == null ? config.withProjects(Map.of()) : withClonePaths(config));
  }

  private ConfigFile withClonePaths(ConfigFile config) {
    Map<String, ProjectConfig> placed = new java.util.LinkedHashMap<>();
    config
        .projects()
        .forEach((key, project) ->
            placed.put(
                key,
                project.hasUrl() && !isDirectory(project.path())
                    ? project.withPath(source.clone(key).toString())
                    : project));
    return config.withProjects(placed);
  }

  private static boolean isDirectory(@Nullable String path) {
    return path != null && !path.isBlank() && Files.isDirectory(Path.of(path));
  }

  /**
   * Per role, key by key, {@code roles.<id>} wins over the old top-level key, which fills what it
   * leaves unwritten.
   */
  private static Object layered(Object asWritten) {
    Object section = RenamedKeys.applied(asWritten);
    if (!(section instanceof Map<?, ?> keys)) {
      return section;
    }
    Map<Object, Object> layered = new java.util.LinkedHashMap<>(keys);
    Map<Object, Object> roles =
        new java.util.LinkedHashMap<>(
            keys.get("roles") instanceof Map<?, ?> written ? written : Map.of());
    for (String id : LEGACY_ROLE_KEYS) {
      Map<Object, Object> merged = merge(keys.get(id), roles.get(id));
      if (!merged.isEmpty()) {
        layered.put(id, merged);
      }
      roles.remove(id);
    }
    if (keys.containsKey("roles")) {
      layered.put("roles", roles);
    }
    return layered;
  }

  private static Map<Object, Object> merge(@Nullable Object... layers) {
    Map<Object, Object> merged = new java.util.LinkedHashMap<>();
    Stream.of(layers)
        .filter(Map.class::isInstance)
        .map(layer -> (Map<?, ?>) layer)
        .forEach(layer ->
            layer.forEach((key, value) -> {
              if (value != null) {
                merged.put(key, value);
              }
            }));
    return merged;
  }

  /**
   * Unset, the Master acts where the runtime reads the human's words, else is off; a stopped act
   * judges.
   */
  private ConfigFile masterByDefault(ConfigFile config) {
    ConfigFile.ArbiterConfig written = config.arbiter();
    ConfigFile.ArbiterConfig master =
        written.mode() != null && !written.mode().isBlank()
            ? written
            : written.withMode(
                (agentRuntime.readsWhatTheHumanTyped() ? MasterMode.ACT : MasterMode.OFF).id());
    return config.withArbiter(
        master.acts() && actStops.halted() ? master.withMode(MasterMode.JUDGE.id()) : master);
  }

  @Override
  public LoopBreaker current() {
    return load().loopBreakerOrDefault();
  }

  /**
   * The names the human wrote under {@code orchestrator}, INCLUDING ones nothing binds — both
   * readers drop those.
   */
  public Set<String> declaredKeys(String... under) {
    @Nullable Object section = section();
    for (String key : under) {
      section = section instanceof Map<?, ?> keys ? keys.get(key) : null;
    }
    return section instanceof Map<?, ?> keys
        ? keys.keySet().stream().map(String::valueOf).collect(Collectors.toUnmodifiableSet())
        : Set.of();
  }

  /** The renamed keys the file still writes under their old name. */
  public List<RenamedKeys.Rename> renamedKeysWritten() {
    return RenamedKeys.written(section());
  }

  /**
   * Full dotted paths of written keys no config record reads: what a read-back of the parsed config
   * no longer holds. A written null is no value, so it never counts.
   */
  public List<String> unreadKeys() {
    if (!(section() instanceof Map<?, ?>)) {
      return List.of();
    }
    Object layered = layered(section());
    List<String> unread = new ArrayList<>();
    collectUnread(
        "orchestrator",
        mapper.valueToTree(layered),
        mapper.valueToTree(mapper.convertValue(layered, ConfigFile.class)),
        unread);
    return unread;
  }

  private static void collectUnread(
      String path, JsonNode written, JsonNode read, List<String> unread) {
    for (Map.Entry<String, JsonNode> entry : written.properties()) {
      JsonNode value = entry.getValue();
      String key = path + "." + entry.getKey();
      JsonNode kept = read.path(entry.getKey());
      if (value.isNull()) {
        continue;
      }
      if (kept.isMissingNode()) {
        unread.add(key);
      } else if (value.isObject() && kept.isObject()) {
        collectUnread(key, value, kept, unread);
      }
    }
  }

  private @Nullable Object section() {
    return source.section();
  }

  public ProjectConfig project(String projectKey) {
    Map<String, ProjectConfig> projects = load().projects();
    ProjectConfig project = projects.get(projectKey);
    if (project == null) {
      throw new IllegalArgumentException(
          "Unknown project '" + projectKey + "'. Known projects: " + projects.keySet());
    }
    return project;
  }
}
