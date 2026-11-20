package fitness.core;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** A loaded fitness.yml with the engines and project functions found on the classpath, able to measure each function it holds. */
public final class Evaluation {

  private final FitnessConfig config;
  private final Map<String, Engine> engines;
  private final Map<String, Fitness> fitnesses;
  private final List<Missing> skipped;

  private Evaluation(
      FitnessConfig config,
      Map<String, Engine> engines,
      Map<String, Fitness> fitnesses,
      List<Missing> skipped) {
    this.config = config;
    this.engines = engines;
    this.fitnesses = fitnesses;
    this.skipped = skipped;
  }

  public static Evaluation load(Path file) {
    return load(file, Requirements.onClasspath(), Set.of());
  }

  /** Fails before any function runs when what a function needs is missing, unless the function is optional. */
  public static Evaluation load(Path file, Presence presence, Set<String> runner) {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    List<Engine> engines = providers(Engine.class, loader);
    List<Fitness> fitnesses = providers(Fitness.class, loader);
    Map<String, Engine> byName =
        engines.stream().collect(Collectors.toMap(Engine::name, Function.identity()));
    FitnessConfig loaded = new FitnessLoader(engines, fitnesses).load(file);
    List<FitnessFunction> measured = loaded.functions().stream().filter(function -> !function.measuredByTool()).toList();
    List<Missing> skipped = Requirements.check(measured, byName, presence, runner);
    Set<FitnessFunction> left = skipped.stream().map(Missing::function).collect(Collectors.toSet());
    FitnessConfig config =
        new FitnessConfig(
            loaded.project(), loaded.functions().stream().filter(function -> !left.contains(function)).toList());
    return new Evaluation(
        config,
        byName,
        fitnesses.stream().collect(Collectors.toMap(Fitness::id, Function.identity())),
        skipped);
  }

  /** The optional functions left out because what they need is missing. */
  public List<Missing> skipped() {
    return skipped;
  }

  private static <T> List<T> providers(Class<T> type, ClassLoader loader) {
    try {
      return ServiceLoader.load(type, loader).stream().map(ServiceLoader.Provider::get).toList();
    } catch (ServiceConfigurationError e) {
      throw new FitnessConfigException(Reason.MISSING_ENGINE, type.getName(), e);
    }
  }

  public FitnessConfig config() {
    return config;
  }

  /** The functions an engine or a project class measures; a build tool measures the others. */
  public List<FitnessFunction> functions(Block block) {
    return config.functions().stream()
        .filter(function -> block.covers(function.section()))
        .filter(function -> !function.measuredByTool())
        .toList();
  }

  public Measurement measure(FitnessFunction function) {
    if (function.engine().equals(FitnessFunction.FITNESS_ENGINE)) {
      return fitnesses.get((String) function.selectors().get("fitness")).measure(function, config.project());
    }
    return engines.get(function.engine()).measure(function, config.project());
  }
}
