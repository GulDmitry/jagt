package fitness.core.load;

import fitness.core.Direction;
import fitness.core.Engine;
import fitness.core.Fitness;
import fitness.core.FitnessConfigException;
import fitness.core.FitnessFunction;
import fitness.core.Reason;
import fitness.core.Section;
import fitness.core.Threshold;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

public final class FitnessFunctionReader {

  private static final List<String> SELECTORS =
      List.of(
          "tool",
          "task",
          "set",
          "kind",
          "interface",
          "static",
          "mutable",
          "annotated",
          "assignableTo",
          "calls",
          "without",
          "except",
          "package",
          "glob",
          "requires",
          "regex",
          "machine",
          "per",
          "measure");
  private static final String HINT = "hint";
  private static final String OPTIONAL = "optional";
  private static final Set<String> BOOLEANS = Set.of("interface", "static", "mutable");
  private static final Set<String> KEYS = keys();
  private static final Map<String, Set<String>> TOOL_VOCABULARY =
      Map.of(
          "tool", Set.of(),
          "task", Set.of(),
          "set", Set.of(),
          "kind", Set.of(),
          "per", Set.of(),
          "measure", Set.of());
  private static final Map<String, Set<String>> AXES =
      Map.of(
          "scope", Set.of("atomic", "holistic"),
          "cadence", Set.of("triggered", "continual", "temporal"),
          "result", Set.of("static", "dynamic"),
          "invocation", Set.of("automated", "manual"),
          "proactivity", Set.of("intentional", "emergent"),
          "reach", Set.of("generic", "project"));

  private final Map<String, Engine> engines;
  private final Map<String, Fitness> fitnesses;

  FitnessFunctionReader(Map<String, Engine> engines, Map<String, Fitness> fitnesses) {
    this.engines = engines;
    this.fitnesses = fitnesses;
  }

  @Nullable
  Engine engine(String name) {
    return engines.get(name);
  }

  FitnessFunction read(Section section, String id, Object raw) {
    String key = section.path() + "." + id;
    Map<String, Object> map = Types.map(key, raw);
    for (String name : map.keySet()) {
      if (!KEYS.contains(name)) {
        throw new FitnessConfigException(Reason.UNKNOWN_KEY, key + "." + name);
      }
    }
    Threshold threshold = threshold(key, map);
    String governs = governs(key, map);
    String engine = engine(section, key, map);
    Map<String, Object> selectors = selectors(key, engine, map);
    String decision = map.containsKey("decision") ? Types.string(key + ".decision", map.get("decision")) : null;
    return new FitnessFunction(id, section.path(), engine, governs, threshold, selectors, decision, axes(key, map));
  }

  private static Set<String> keys() {
    Set<String> keys = new HashSet<>(SELECTORS);
    keys.addAll(List.of("engine", "fitness", "governs", "max", "min", "decision", "axes", OPTIONAL, HINT));
    return Set.copyOf(keys);
  }

  private static Threshold threshold(String key, Map<String, Object> map) {
    boolean max = map.containsKey("max");
    boolean min = map.containsKey("min");
    if (max && min) {
      throw new FitnessConfigException(Reason.BOTH_THRESHOLDS, key);
    }
    if (!max && !min) {
      throw new FitnessConfigException(Reason.MISSING_THRESHOLD, key);
    }
    String name = max ? "max" : "min";
    return new Threshold(max ? Direction.MAX : Direction.MIN, Types.count(key + "." + name, map.get(name)));
  }

  private static String governs(String key, Map<String, Object> map) {
    if (!map.containsKey("governs")) {
      throw new FitnessConfigException(Reason.MISSING_KEY, key + ".governs");
    }
    String governs = Types.string(key + ".governs", map.get("governs"));
    if (!ReservedTree.governs().contains(governs)) {
      throw new FitnessConfigException(Reason.UNKNOWN_GOVERNS, governs);
    }
    return governs;
  }

  private String engine(Section section, String key, Map<String, Object> map) {
    boolean fitness = map.containsKey("fitness");
    if (fitness == map.containsKey("engine")) {
      throw new FitnessConfigException(Reason.MISSING_KEY, key + ".engine");
    }
    if (fitness) {
      checkFitness(section, Types.string(key + ".fitness", map.get("fitness")));
      return FitnessFunction.FITNESS_ENGINE;
    }
    String engine = Types.string(key + ".engine", map.get("engine"));
    if (!FitnessFunction.TOOL_ENGINE.equals(engine) && !engines.containsKey(engine)) {
      throw new FitnessConfigException(Reason.MISSING_ENGINE, key + " needs " + engine);
    }
    return engine;
  }

  private void checkFitness(Section section, String id) {
    Fitness fitness = fitnesses.get(id);
    if (fitness == null) {
      throw new FitnessConfigException(Reason.UNKNOWN_ID, id);
    }
    if (!fitness.getClass().getPackageName().equals(section.packageName())) {
      throw new FitnessConfigException(Reason.WRONG_PACKAGE, fitness.getClass().getName());
    }
  }

  private Map<String, Object> selectors(String key, String engine, Map<String, Object> map) {
    Map<String, Set<String>> vocabulary = vocabulary(engine);
    Map<String, Object> selectors = new LinkedHashMap<>();
    for (String name : SELECTORS) {
      if (map.containsKey(name)) {
        String path = key + "." + name;
        Object value = BOOLEANS.contains(name) ? Types.bool(path, map.get(name)) : Types.string(path, map.get(name));
        checkSelector(path, vocabulary.get(name), value);
        selectors.put(name, value);
      }
    }
    if (map.containsKey("fitness")) {
      selectors.put("fitness", map.get("fitness"));
    }
    if (map.containsKey(OPTIONAL)) {
      selectors.put(OPTIONAL, Types.bool(key + "." + OPTIONAL, map.get(OPTIONAL)));
    }
    if (map.containsKey(HINT)) {
      selectors.put(HINT, Types.string(key + "." + HINT, map.get(HINT)));
    }
    return selectors;
  }

  private Map<String, Set<String>> vocabulary(String engine) {
    if (FitnessFunction.TOOL_ENGINE.equals(engine)) {
      return TOOL_VOCABULARY;
    }
    return FitnessFunction.FITNESS_ENGINE.equals(engine) ? Map.of() : engines.get(engine).vocabulary();
  }

  private static void checkSelector(String path, Set<String> allowed, Object value) {
    if (allowed == null || !allowed.isEmpty() && !allowed.contains(value.toString())) {
      throw new FitnessConfigException(Reason.UNKNOWN_SELECTOR, path);
    }
  }

  private static Map<String, String> axes(String key, Map<String, Object> map) {
    if (!map.containsKey("axes")) {
      return Map.of();
    }
    Map<String, Object> raw = Types.map(key + ".axes", map.get("axes"));
    return raw.entrySet().stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey, entry -> axis(key + ".axes." + entry.getKey(), entry)));
  }

  private static String axis(String path, Map.Entry<String, Object> entry) {
    Set<String> allowed = AXES.get(entry.getKey());
    if (allowed == null) {
      throw new FitnessConfigException(Reason.UNKNOWN_KEY, path);
    }
    String value = Types.string(path, entry.getValue());
    if (!allowed.contains(value)) {
      throw new FitnessConfigException(Reason.UNKNOWN_SELECTOR, path);
    }
    return value;
  }
}
