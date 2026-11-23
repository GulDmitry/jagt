package fitness.gradle;

import fitness.core.FitnessConfigException;
import fitness.core.FitnessFunction;
import fitness.core.load.FitnessLoader;
import fitness.core.run.Block;
import fitness.core.run.Requirements;
import fitness.gradle.check.Overruns;
import fitness.gradle.check.RequirementsCheck;
import fitness.gradle.tool.Installed;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.flow.FlowProviders;
import org.gradle.api.flow.FlowScope;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;

/**
 * Binds each tool row of fitness.yml to the build: the tool enforces its number, or a check measures it. The
 * `fitness` task runs every function of a block, `-Pfitness.block=<dimension>[.<characteristic>[.<subcharacteristic>]]`.
 */
public final class FitnessPlugin implements Plugin<Project> {

  private static final String BLOCK = "fitness.block";

  private static final Set<String> TIME_MEASURES = Set.of("minutes", "runs-without-time-limit");

  private final FlowScope flow;
  private final FlowProviders providers;

  @Inject
  public FitnessPlugin(FlowScope flow, FlowProviders providers) {
    this.flow = flow;
    this.providers = providers;
  }

  @Override
  public void apply(Project root) {
    FitnessExtension extension = root.getExtensions().create("fitness", FitnessExtension.class);
    extension.getMinutesProperty().convention("fitness.minutes");
    extension.getTimed().convention(true);
    root.getGradle().projectsEvaluated(gradle -> wire(root, extension));
  }

  private void wire(Project root, FitnessExtension extension) {
    if (!extension.getFile().isPresent()) {
      throw new GradleException("fitness { file = ... } names no fitness.yml");
    }
    Block block = block(root);
    List<Object> measured = new ArrayList<>();
    List<FitnessFunction> minutes = new ArrayList<>();
    boolean timed = extension.getTimed().get();
    List<FitnessFunction> toolFitnessFunctions =
        FitnessLoader.forTools().load(extension.getFile().get().getAsFile().toPath()).functions().stream()
            .filter(function -> timed || !TIME_MEASURES.contains(function.selectors().getOrDefault("measure", "").toString()))
            .toList();
    TaskProvider<RequirementsCheck> requirements = requirements(root, toolFitnessFunctions);
    for (FitnessFunction function : toolFitnessFunctions) {
      String tool = function.selectors().getOrDefault("tool", "").toString();
      String measure = function.selectors().getOrDefault("measure", "").toString();
      String key = tool + ":" + measure;
      TaskProvider<?> check = bind(root, extension, function, key, minutes);
      if (check != null && block.covers(function.section())) {
        check.configure(task -> task.mustRunAfter(requirements));
        measured.add(check);
      }
    }
    root.getTasks()
        .named("test")
        .configure(
            test ->
                test.getInputs()
                    .files(
                        root.getProviders()
                            .of(
                                TrackedFiles.class,
                                source ->
                                    source.getParameters().getDirectory().set(root.getProjectDir())))
                    .withPropertyName("trackedFiles")
                    .withPathSensitivity(PathSensitivity.RELATIVE));
    MapProperty<String, String> overruns = overruns(root);
    Limits.minutes(root, extension.getMinutesProperty(), minutes, overruns);
    registerFitness(root, extension, block, measured, minutes, requirements);
  }

  private static TaskProvider<?> bind(Project root, FitnessExtension extension, FitnessFunction function, String key, List<FitnessFunction> minutes) {
    switch (key) {
      case "checkstyle:warnings" -> {
        Limits.checkstyle(root, function);
        return Checks.checkstyle(root, function);
      }
      case "jacoco:line-coverage-percent" -> {
        Limits.coverage(root, function);
        Checks.coverage(root, function);
        return Checks.runMeasured(root, function, "test");
      }
      case "jacoco:unreached-classes-with-branch" -> {
        Checks.reach(root, function);
        return Checks.runMeasured(root, function, "test");
      }
      case "gradle:minutes" -> {
        minutes.add(function);
        return Checks.runMeasured(root, function, Limits.suites(function));
      }
      case "gradle:contexts" -> {
        Checks.contexts(root, extension, function);
        return Checks.runMeasured(root, function, function.selectors().get("task").toString());
      }
      case "gradle:runs-without-time-limit" -> {
        return Checks.unlimited(root, function);
      }
      case "gradle:warnings-not-failing" -> {
        return Checks.warnings(root, function);
      }
      case "osv-scanner:known-vulnerabilities" -> {
        return Checks.vulnerabilities(root, function);
      }
      default ->
          throw new GradleException("fitness: no measure for " + function.id() + " (" + key + ")");
    }
  }

  /** The tools the rows name, checked before any function runs; a missing tool of a required row fails the `fitness` task. */
  private static TaskProvider<RequirementsCheck> requirements(Project root, List<FitnessFunction> toolFitnessFunctions) {
    Installed installed = Installed.of(root);
    Map<String, String> missing = new LinkedHashMap<>();
    Requirements.missing(toolFitnessFunctions, Map.of(), installed, Set.of()).stream()
        .filter(gap -> !gap.function().optional())
        .forEach(gap -> missing.put(gap.key(), gap.need()));
    return root.getTasks()
        .register(
            "fitnessRequirements",
            RequirementsCheck.class,
            task -> {
              task.setGroup("verification");
              task.setDescription("Fails when a tool a required row of fitness.yml names is missing.");
              task.getMissing().set(missing);
            });
  }

  private static Block block(Project root) {
    String path = root.getProviders().gradleProperty(BLOCK).getOrElse("");
    try {
      return Block.of(path);
    } catch (FitnessConfigException e) {
      throw new GradleException("-P" + BLOCK + " names no part of the reserved tree: " + path, e);
    }
  }

  private MapProperty<String, String> overruns(Project root) {
    AtomicReference<MapProperty<String, String>> limits = new AtomicReference<>();
    flow.always(
        Overruns.class,
        spec -> {
          spec.getParameters().getResult().set(providers.getBuildWorkResult());
          limits.set(spec.getParameters().getLimits());
        });
    return limits.get();
  }

  private static List<Object> classesOfEverySourceSet(Project root) {
    List<Object> classes = new ArrayList<>();
    for (Project project : root.getAllprojects()) {
      SourceSetContainer sets = project.getExtensions().findByType(SourceSetContainer.class);
      if (sets != null) {
        sets.forEach(set -> classes.add(project.getTasks().named(set.getClassesTaskName())));
      }
    }
    return classes;
  }

  private static void registerFitness(
      Project root,
      FitnessExtension extension,
      Block block,
      List<Object> measured,
      List<FitnessFunction> minutes,
      TaskProvider<RequirementsCheck> requirements) {
    TaskProvider<JavaExec> functions =
        root.getTasks()
            .register(
                "fitnessFitnessFunctions",
                JavaExec.class,
                task -> {
                  task.setGroup("verification");
                  task.setDescription("Measures every engine and project function of fitness.yml in the block.");
                  task.setClasspath(extension.getClasspath());
                  task.getMainClass().set("fitness.core.run.FitnessRun");
                  task.setWorkingDir(root.getProjectDir());
                  task.setMaxHeapSize("2g");
                  task.args(extension.getFile().get().getAsFile().getAbsolutePath(), block.path());
                  minutes.stream()
                      .filter(function -> Limits.tasks(function).isEmpty())
                      .findFirst()
                      .ifPresent(
                          function -> task.getTimeout().set(Duration.ofMinutes(function.threshold().limit())));
                  task.dependsOn(requirements);
                  task.dependsOn(classesOfEverySourceSet(root));
                });
    root.getTasks()
        .register(
            "fitness",
            task -> {
              task.setGroup("verification");
              task.setDescription("Runs every fitness function and nothing else; -Pfitness.block=<part of the tree> runs one block.");
              task.dependsOn(requirements);
              task.dependsOn(functions);
              measured.forEach(task::dependsOn);
            });
  }
}
