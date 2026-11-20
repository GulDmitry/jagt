package fitness.gradle;

import fitness.core.FitnessFunction;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;
import org.gradle.testing.jacoco.tasks.JacocoReport;

/** Registers the task that measures a function at the end of a run and puts it after the task it judges. */
final class Checks {

  private static final String WHOLE_BUILD_TEST = "test";

  private Checks() {}

  static TaskProvider<ItemsCheck> warnings(Project root, FitnessFunction function) {
    TaskProvider<ItemsCheck> check = register(root, ItemsCheck.class, "fitnessWarnings", function);
    check.configure(task -> task.getItems().set(root.provider(() -> Settings.warningsNotFailing(root))));
    root.getTasks().named(WHOLE_BUILD_TEST).configure(test -> test.finalizedBy(check));
    return check;
  }

  static TaskProvider<ItemsCheck> unlimited(Project root, FitnessFunction function) {
    TaskProvider<ItemsCheck> check = register(root, ItemsCheck.class, "fitnessUnlimitedRuns", function);
    check.configure(task -> task.getItems().set(root.provider(() -> Settings.runsWithoutLimit(root, function))));
    root.getTasks().named(WHOLE_BUILD_TEST).configure(test -> test.finalizedBy(check));
    return check;
  }

  static void contexts(Project root, FitnessExtension extension, FitnessFunction function) {
    Object suite = function.selectors().get("task");
    if (suite == null || root.getTasks().findByName(suite.toString()) == null) {
      throw new GradleException("fitness: " + function.id() + " names no suite task: " + suite);
    }
    TaskProvider<ContextsCheck> check =
        register(root, ContextsCheck.class, suite + "ContextCeiling", function);
    check.configure(
        task -> {
          task.getReports().set(root.getLayout().getBuildDirectory().dir("test-results/" + suite));
          task.getPattern().set(extension.getContextsPattern());
        });
    root.getTasks()
        .named(suite.toString(), Test.class)
        .configure(
            test -> {
              test.finalizedBy(check);
              extension.getContextsSystemProperties().get().forEach(test::systemProperty);
            });
  }

  static void reach(Project root, FitnessFunction function) {
    List<Project> modules = List.copyOf(root.getAllprojects());
    List<File> executions =
        modules.stream()
            .map(module -> module.getLayout().getBuildDirectory().file("jacoco/test.exec").get().getAsFile())
            .toList();
    Map<String, String> classDirectories = new LinkedHashMap<>();
    modules.forEach(
        module ->
            classDirectories.put(
                module.getPath(),
                module.getLayout().getBuildDirectory().dir("classes/java/main").get().getAsFile().getPath()));
    boolean filtered = root.getGradle().getStartParameter().getTaskNames().contains("--tests");
    org.gradle.api.specs.Spec<Task> wholeBuild =
        task -> !filtered && executions.stream().allMatch(File::exists);
    var xml = root.getLayout().getBuildDirectory().file("reports/test-reach/reach.xml");
    TaskProvider<JacocoReport> report =
        root.getTasks()
            .register(
                "testReachReport",
                JacocoReport.class,
                task -> {
                  task.getExecutionData().from(executions);
                  task.getClassDirectories().from(classDirectories.values());
                  task.getSourceDirectories()
                      .from(
                          modules.stream()
                              .map(module -> module.getLayout().getProjectDirectory().dir("src/main/java"))
                              .toList());
                  task.getReports().getXml().getRequired().set(true);
                  task.getReports().getHtml().getRequired().set(false);
                  task.getReports().getXml().getOutputLocation().set(xml);
                  task.onlyIf(wholeBuild);
                  task.mustRunAfter(modules.stream().map(module -> module.getTasks().named(WHOLE_BUILD_TEST)).toList());
                });
    TaskProvider<ReachCheck> check = register(root, ReachCheck.class, "testReachCheck", function);
    check.configure(
        task -> {
          task.dependsOn(report);
          task.onlyIf(wholeBuild);
          task.getReport().set(xml);
          task.getClassDirectories().set(classDirectories);
          task.mustRunAfter(modules.stream().map(module -> module.getTasks().named("jacocoTestReport")).toList());
        });
    root.getTasks().named(WHOLE_BUILD_TEST).configure(test -> test.finalizedBy(check));
  }

  static TaskProvider<VulnerabilityCheck> vulnerabilities(Project root, FitnessFunction function) {
    TaskProvider<VulnerabilityCheck> check =
        register(root, VulnerabilityCheck.class, "fitnessVulnerabilities", function);
    check.configure(task -> task.getExecutable().set(function.selectors().get("tool").toString()));
    return check;
  }

  /** The warnings the Checkstyle tasks of every module reported, counted after they ran. */
  static TaskProvider<CheckstyleCheck> checkstyle(Project root, FitnessFunction function) {
    TaskProvider<CheckstyleCheck> check =
        register(root, CheckstyleCheck.class, "fitnessCheckstyle", function);
    List<Checkstyle> tasks = new ArrayList<>();
    root.getAllprojects()
        .forEach(project -> project.getTasks().withType(Checkstyle.class).forEach(tasks::add));
    check.configure(
        measure -> {
          measure.dependsOn(tasks);
          tasks.forEach(
              task ->
                  measure
                      .getReports()
                      .from(
                          root.provider(
                              () -> task.getReports().getXml().getOutputLocation().get().getAsFile())));
        });
    return check;
  }

  /** A row whose tool measures it inside the run of a suite: the task names where, for the report. */
  static TaskProvider<RunMeasured> runMeasured(Project root, FitnessFunction function, String measuredBy) {
    TaskProvider<RunMeasured> check =
        register(root, RunMeasured.class, "fitness-" + function.id(), function);
    check.configure(task -> task.getMeasuredBy().set(measuredBy));
    return check;
  }

  private static <T extends FitnessFunctionCheck> TaskProvider<T> register(
      Project root, Class<T> type, String name, FitnessFunction function) {
    return root.getTasks()
        .register(
            name,
            type,
            task -> {
              task.setGroup("verification");
              task.setDescription("Measures function " + function.id() + " against its threshold in fitness.yml.");
              task.getFitnessFunctionId().set(function.id());
              task.getOptional().set(function.optional());
              task.getDirection().set(function.threshold().direction().name());
              task.getLimit().set(function.threshold().limit());
              Object hint = function.selectors().get("hint");
              if (hint != null) {
                task.getHint().set(hint.toString());
              }
            });
  }
}
