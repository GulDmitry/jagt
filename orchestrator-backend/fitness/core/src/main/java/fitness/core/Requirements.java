package fitness.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** What a fitness.yml needs from its runner: an engine, the library behind it, a tool; a missing one fails before any function runs. */
public final class Requirements {

  static final Set<String> ENGINES = Set.of("archunit", "scan");
  private static final String GRADLE = "gradle";
  private static final Map<String, String> LIBRARIES =
      Map.of(
          "archunit", "com.tngtech.archunit.core.importer.ClassFileImporter",
          "pmd", "net.sourceforge.pmd.PMDConfiguration",
          "junit", "org.junit.jupiter.api.DynamicTest");

  private Requirements() {}

  /** The libraries this classpath holds; a name it knows no class for counts as present. */
  public static Presence onClasspath() {
    return need -> !LIBRARIES.containsKey(need) || loadable(LIBRARIES.get(need));
  }

  private static boolean loadable(String type) {
    try {
      Class.forName(type, false, Requirements.class.getClassLoader());
      return true;
    } catch (ClassNotFoundException | LinkageError e) {
      return false;
    }
  }

  /** What each function lacks: a tool row needs its tool, any other function the runner's libraries and its engine's. */
  public static List<Missing> missing(
      Collection<FitnessFunction> functions, Map<String, Engine> engines, Presence presence, Set<String> runner) {
    List<Missing> missing = new ArrayList<>();
    for (FitnessFunction function : functions) {
      for (String need : needs(function, engines, runner)) {
        if (!presence.has(need)) {
          missing.add(new Missing(function, need));
        }
      }
    }
    return missing;
  }

  /** Fails on the first missing need of a required function; returns the needs of optional functions, which are skipped. */
  public static List<Missing> check(
      Collection<FitnessFunction> functions, Map<String, Engine> engines, Presence presence, Set<String> runner) {
    List<Missing> skipped = new ArrayList<>();
    for (Missing missing : missing(functions, engines, presence, runner)) {
      if (!missing.function().optional()) {
        throw new FitnessConfigException(Reason.MISSING_TOOL, missing.key());
      }
      skipped.add(missing);
    }
    return skipped;
  }

  private static Set<String> needs(FitnessFunction function, Map<String, Engine> engines, Set<String> runner) {
    Set<String> needs = new LinkedHashSet<>();
    if (function.measuredByTool()) {
      String tool = String.valueOf(function.selectors().getOrDefault("tool", GRADLE));
      if (!GRADLE.equals(tool)) {
        needs.add(tool);
      }
      return needs;
    }
    needs.addAll(runner);
    Engine engine = engines.get(function.engine());
    if (engine != null) {
      needs.addAll(engine.needs(function));
    }
    return needs;
  }
}
