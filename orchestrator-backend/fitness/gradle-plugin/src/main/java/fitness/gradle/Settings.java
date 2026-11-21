package fitness.gradle;

import fitness.core.FitnessFunction;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.logging.configuration.WarningMode;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;

/** What the build is set to, listed as the items of the rows that count it. */
final class Settings {

  private Settings() {}

  /** A compile task that lets a warning pass, or a Gradle run that does not fail on one. */
  static List<String> warningsNotFailing(Project root) {
    List<String> gaps = new ArrayList<>();
    if (root.getGradle().getStartParameter().getWarningMode() != WarningMode.Fail) {
      gaps.add("Gradle warning mode is not fail");
    }
    for (Project project : root.getAllprojects()) {
      for (JavaCompile compile : project.getTasks().withType(JavaCompile.class)) {
        List<String> args = compile.getOptions().getCompilerArgs();
        if (args.stream().noneMatch(arg -> arg.startsWith("-Xlint:all"))) {
          gaps.add(compile.getPath() + " lacks -Xlint:all");
        }
        if (!args.contains("-Werror")) {
          gaps.add(compile.getPath() + " lacks -Werror");
        }
      }
    }
    return gaps;
  }

  /** A test task, or a gate the row names, that declares no time limit. */
  static List<String> runsWithoutLimit(Project root, FitnessFunction function) {
    List<String> unlimited = new ArrayList<>();
    Set<String> gates = new LinkedHashSet<>(Limits.tasks(function));
    for (Project project : root.getAllprojects()) {
      Set<Task> run = new LinkedHashSet<>(project.getTasks().withType(Test.class));
      gates.stream()
          .filter(project.getTasks().getNames()::contains)
          .forEach(name -> run.add(project.getTasks().getByName(name)));
      run.stream().filter(task -> !task.getTimeout().isPresent()).forEach(task -> unlimited.add(task.getPath()));
    }
    return unlimited;
  }
}
