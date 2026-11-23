package dev.jagt.gradle.checkscope;

import com.diffplug.gradle.spotless.SpotlessTask;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.process.CommandLineArgumentProvider;
import org.jspecify.annotations.Nullable;

/** The `checkScope` task: every source set with sources has every check (0162). */
public final class CheckScope {

  public static final String NAME = "checkScope";

  private static final String NULLAWAY_ERROR = "(?s).*(^|\\s)-Xep:NullAway:ERROR(\\s|$).*";

  private CheckScope() {}

  public static void registerOn(Project root) {
    root.getTasks()
        .register(
            NAME,
            CheckScopeTask.class,
            task -> {
              task.setGroup("verification");
              task.setDescription("Fails when a source set with sources lacks a check.");
            });
    root.getGradle()
        .projectsEvaluated(
            gradle ->
                root.getTasks()
                    .named(NAME, CheckScopeTask.class)
                    .configure(task -> task.getGaps().set(gaps(root))));
  }

  public static List<String> gaps(Project root) {
    List<String> gaps = new ArrayList<>();
    for (Project project : root.getAllprojects()) {
      JavaPluginExtension java = project.getExtensions().findByType(JavaPluginExtension.class);
      if (java == null) {
        continue;
      }
      for (SourceSet set : java.getSourceSets()) {
        if (set.getAllJava().getSrcDirs().stream().anyMatch(File::exists)) {
          for (String check : missing(project, set)) {
            gaps.add(project.getPath() + " " + set.getName() + ": " + check);
          }
        }
      }
    }
    return gaps;
  }

  static void verify(List<String> gaps) {
    if (!gaps.isEmpty()) {
      throw new GradleException(
          "A source set lacks a check (docs/decisions/0162):\n" + String.join("\n", gaps));
    }
  }

  private static List<String> missing(Project project, SourceSet set) {
    List<String> missing = new ArrayList<>();
    Task checkstyle = project.getTasks().findByName(set.getTaskName("checkstyle", null));
    if (checkstyle == null || !checkstyle.getEnabled()) {
      missing.add("checkstyle");
    }
    if (!gatedByTest(project, checkstyle)) {
      missing.add("checkstyle gated by test");
    }
    List<String> javacArguments =
        javacArguments(project.getTasks().findByName(set.getCompileJavaTaskName()));
    if (javacArguments.stream().noneMatch(argument -> argument.startsWith("-Xplugin:ErrorProne"))) {
      missing.add("errorprone");
    } else if (javacArguments.stream().noneMatch(argument -> argument.matches(NULLAWAY_ERROR))) {
      missing.add("nullaway at error");
    }
    if (!formatted(project, set)) {
      missing.add("google-java-format");
    }
    return missing;
  }

  private static boolean gatedByTest(Project project, @Nullable Task checkstyle) {
    Task test = project.getTasks().findByName("test");
    return checkstyle != null
        && test != null
        && test.getTaskDependencies().getDependencies(test).contains(checkstyle);
  }

  private static List<String> javacArguments(@Nullable Task compile) {
    if (compile instanceof JavaCompile javaCompile) {
      return javaCompile.getOptions().getCompilerArgumentProviders().stream()
          .map(CommandLineArgumentProvider::asArguments)
          .flatMap(arguments -> StreamSupport.stream(arguments.spliterator(), false))
          .toList();
    }
    return List.of();
  }

  private static boolean formatted(Project project, SourceSet set) {
    if (project.getTasks().findByName("spotlessJava") instanceof SpotlessTask format) {
      FileCollection target = format.getTarget();
      return set.getAllJava().getFiles().stream().allMatch(target::contains);
    }
    return false;
  }
}
