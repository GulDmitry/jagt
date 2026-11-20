package fitness.gradle;

import fitness.core.FitnessFunction;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.plugins.quality.CheckstyleExtension;
import org.gradle.api.plugins.quality.CheckstylePlugin;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.testing.Test;
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification;

/** The rows whose tool enforces the number itself: the build hands the tool the row's threshold. */
final class Limits {

  private Limits() {}

  static Set<String> tasks(FitnessFunction function) {
    Object names = function.selectors().get("task");
    return names == null
        ? Set.of()
        : Arrays.stream(names.toString().split(",")).map(String::trim).collect(Collectors.toSet());
  }

  /** The suite tasks a minutes row limits, by name: the gate tasks it names, else every test task. */
  static String suites(FitnessFunction function) {
    return tasks(function).isEmpty() ? "test" : String.join(",", tasks(function).stream().sorted().toList());
  }

  static void checkstyle(Project root, FitnessFunction function) {
    root.getAllprojects()
        .forEach(
            project ->
                project
                    .getPlugins()
                    .withType(
                        CheckstylePlugin.class,
                        plugin ->
                            project
                                .getExtensions()
                                .getByType(CheckstyleExtension.class)
                                .setMaxWarnings((int) function.threshold().limit())));
  }

  static void coverage(Project root, FitnessFunction function) {
    BigDecimal ratio = BigDecimal.valueOf(function.threshold().limit()).movePointLeft(2);
    root.getAllprojects()
        .forEach(
            project ->
                project
                    .getTasks()
                    .withType(JacocoCoverageVerification.class)
                    .configureEach(
                        task ->
                            task.getViolationRules()
                                .rule(
                                    violation ->
                                        violation.limit(
                                            limit -> {
                                              limit.setCounter("LINE");
                                              limit.setValue("COVEREDRATIO");
                                              limit.setMinimum(ratio);
                                            }))));
  }

  /** A gate row limits the tasks it names; the suite row limits every other test task. */
  static void minutes(
      Project root,
      Property<String> property,
      List<FitnessFunction> functions, MapProperty<String, String> overruns) {
    Provider<Long> override =
        property.flatMap(name -> root.getProviders().gradleProperty(name)).map(Long::parseLong);
    FitnessFunction suite =
        functions.stream().filter(function -> tasks(function).isEmpty()).findFirst().orElse(null);
    root.getAllprojects()
        .forEach(
            project -> {
              project
                  .getTasks()
                  .matching(task -> functions.stream().anyMatch(function -> tasks(function).contains(task.getName())))
                  .configureEach(
                      task ->
                          limit(
                              task,
                              override,
                              overruns,
                              functions.stream()
                                  .filter(function -> tasks(function).contains(task.getName()))
                                  .findFirst()
                                  .orElseThrow()));
              if (suite != null) {
                project
                    .getTasks()
                    .withType(Test.class)
                    .matching(task -> functions.stream().noneMatch(function -> tasks(function).contains(task.getName())))
                    .configureEach(task -> limit(task, override, overruns, suite));
              }
            });
  }

  private static void limit(
      org.gradle.api.Task task,
      Provider<Long> override,
      MapProperty<String, String> overruns,
      FitnessFunction function) {
    Provider<Long> minutes = override.orElse(function.threshold().limit());
    task.getTimeout().set(minutes.map(Duration::ofMinutes));
    overruns.put(task.getPath(), minutes.map(value -> function.id() + "|" + value));
  }
}
