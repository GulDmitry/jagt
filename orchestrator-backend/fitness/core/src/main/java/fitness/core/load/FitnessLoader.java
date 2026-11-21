package fitness.core.load;

import fitness.core.Engine;
import fitness.core.Fitness;
import fitness.core.FitnessConfigException;
import fitness.core.FitnessFunction;
import fitness.core.Project;
import fitness.core.Reason;
import fitness.core.Section;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

public final class FitnessLoader {

  private static final Set<String> HEADER = Set.of("schema", "extends", "project");
  private static final Set<String> FUNCTION_KEYS = Set.of("max", "min", "engine", "fitness");
  private static final int TREE_DEPTH = 3;

  private final FitnessFunctionReader reader;
  private final boolean toolsOnly;

  public FitnessLoader(Collection<Engine> engines, Collection<Fitness> fitnesses) {
    this(engines, fitnesses, false);
  }

  private FitnessLoader(Collection<Engine> engines, Collection<Fitness> fitnesses, boolean toolsOnly) {
    reader =
        new FitnessFunctionReader(
            index(engines, Engine::name, Reason.DUPLICATE_ENGINE), index(fitnesses, Fitness::id, Reason.DUPLICATE_ID));
    this.toolsOnly = toolsOnly;
  }

  public static <T> Map<String, T> index(Collection<T> providers, Function<T, String> name, Reason duplicate) {
    Map<String, T> byName = new LinkedHashMap<>();
    for (T provider : providers) {
      if (byName.putIfAbsent(name.apply(provider), provider) != null) {
        throw new FitnessConfigException(duplicate, name.apply(provider));
      }
    }
    return byName;
  }

  /** Reads only the functions a build tool measures, so a build holding no engine and no project class can load the file. */
  public static FitnessLoader forTools() {
    return new FitnessLoader(List.of(), List.of(), true);
  }

  public FitnessConfig load(Path file) {
    try {
      return parse(Files.readString(file), file.toAbsolutePath().getParent());
    } catch (IOException e) {
      throw new FitnessConfigException(Reason.UNREADABLE, file.toString(), e);
    }
  }

  public FitnessConfig parse(String yaml, Path directory) {
    Map<String, Object> document = withDefaults(read(yaml, "fitness.yml"));
    if (!Integer.valueOf(1).equals(document.get("schema"))) {
      throw new FitnessConfigException(Reason.UNSUPPORTED_SCHEMA, "schema");
    }
    Project project = project(document, directory);
    List<FitnessFunction> functions = functions(document);
    functions.forEach(function -> validate(function, project));
    return new FitnessConfig(project, functions);
  }

  private void validate(FitnessFunction function, Project project) {
    Engine engine = reader.engine(function.engine());
    if (engine != null) {
      engine.validate(function, project);
    }
  }

  private static Map<String, Object> withDefaults(Map<String, Object> document) {
    if (!document.containsKey("extends")) {
      return document;
    }
    String name = Types.string("extends", document.get("extends"));
    return merge(read(resource(name), name), document, TREE_DEPTH);
  }

  private static String resource(String name) {
    try (InputStream stream = FitnessLoader.class.getResourceAsStream("/" + name + ".yml")) {
      if (stream == null) {
        throw new FitnessConfigException(Reason.UNREADABLE, "extends");
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new FitnessConfigException(Reason.UNREADABLE, "extends", e);
    }
  }

  private static Map<String, Object> read(String yaml, String name) {
    LoaderOptions options = new LoaderOptions();
    options.setAllowDuplicateKeys(false);
    try {
      Object parsed = new Yaml(new SafeConstructor(options)).load(yaml);
      return parsed == null ? new LinkedHashMap<>() : Types.map(name, parsed);
    } catch (YAMLException e) {
      throw new FitnessConfigException(Reason.UNREADABLE, name, e);
    }
  }

  private static Map<String, Object> merge(
      Map<String, Object> base, Map<String, Object> overlay, int levels) {
    Map<String, Object> merged = new LinkedHashMap<>(base);
    overlay.forEach(
        (key, value) -> {
          Object existing = merged.get(key);
          if (levels > 0 && existing instanceof Map<?, ?> && value instanceof Map<?, ?>) {
            merged.put(key, merge(Types.map(key, existing), Types.map(key, value), levels - 1));
          } else {
            merged.put(key, value);
          }
        });
    return merged;
  }

  private static Project project(Map<String, Object> document, Path directory) {
    Map<String, Object> project =
        document.containsKey("project")
            ? Types.map("project", document.get("project"))
            : Map.of();
    for (String key : project.keySet()) {
      if (!Set.of("basePackage", "root", "sourceSets", "modules").contains(key)) {
        throw new FitnessConfigException(Reason.UNKNOWN_KEY, "project." + key);
      }
    }
    String root = Types.string("project.root", project.getOrDefault("root", "."));
    String basePackage = Types.string("project.basePackage", project.getOrDefault("basePackage", ""));
    return new Project(directory.resolve(root).normalize(), basePackage, sourceSets(project), Modules.read(project.get("modules")));
  }

  private static List<String> sourceSets(Map<String, Object> project) {
    Object value = project.getOrDefault("sourceSets", "all");
    if (value instanceof String text) {
      return List.of(text);
    }
    if (!(value instanceof List<?> list)) {
      throw new FitnessConfigException(Reason.WRONG_TYPE, "project.sourceSets");
    }
    return list.stream().map(item -> Types.string("project.sourceSets", item)).toList();
  }

  private List<FitnessFunction> functions(Map<String, Object> document) {
    List<FitnessFunction> functions = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    checkTree(document);
    for (Section section : ReservedTree.sections()) {
      Map<String, Object> declared = declared(document, section);
      declared.forEach(
          (id, raw) -> {
            if (toolsOnly && !measuredByTool(raw)) {
              return;
            }
            if (!ids.add(id)) {
              throw new FitnessConfigException(Reason.DUPLICATE_ID, id);
            }
            functions.add(reader.read(section, id, raw));
          });
    }
    return functions;
  }

  private static boolean measuredByTool(Object raw) {
    return raw instanceof Map<?, ?> map && FitnessFunction.TOOL_ENGINE.equals(map.get("engine"));
  }

  private static void checkTree(Map<String, Object> document) {
    for (Map.Entry<String, Object> entry : document.entrySet()) {
      String dimension = entry.getKey();
      if (HEADER.contains(dimension)) {
        continue;
      }
      if (!ReservedTree.dimensions().contains(dimension)) {
        throw misplaced(dimension, entry.getValue());
      }
      checkCharacteristics(dimension, Types.map(dimension, entry.getValue()));
    }
  }

  private static void checkCharacteristics(String dimension, Map<String, Object> characteristics) {
    for (Map.Entry<String, Object> entry : characteristics.entrySet()) {
      String path = dimension + "." + entry.getKey();
      if (!ReservedTree.characteristics(dimension).contains(entry.getKey())) {
        throw misplaced(path, entry.getValue());
      }
      for (Map.Entry<String, Object> sub : Types.map(path, entry.getValue()).entrySet()) {
        String subPath = path + "." + sub.getKey();
        if (!ReservedTree.subcharacteristics(dimension, entry.getKey()).contains(sub.getKey())) {
          throw misplaced(subPath, sub.getValue());
        }
        Types.map(subPath, sub.getValue());
      }
    }
  }

  private static FitnessConfigException misplaced(String path, Object value) {
    boolean functionLike = value instanceof Map<?, ?> map && map.keySet().stream().anyMatch(FUNCTION_KEYS::contains);
    return new FitnessConfigException(functionLike ? Reason.MISPLACED_FUNCTION : Reason.UNKNOWN_SECTION, path);
  }

  private static Map<String, Object> declared(Map<String, Object> document, Section section) {
    Object dimension = document.get(section.dimension());
    if (dimension == null) {
      return Map.of();
    }
    Object characteristic = Types.map(section.dimension(), dimension).get(section.characteristic());
    if (characteristic == null) {
      return Map.of();
    }
    Object sub = Types.map(section.dimension() + "." + section.characteristic(), characteristic).get(section.subcharacteristic());
    return sub == null ? Map.of() : Types.map(section.path(), sub);
  }
}
