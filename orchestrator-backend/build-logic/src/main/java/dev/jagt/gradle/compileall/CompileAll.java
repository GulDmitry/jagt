package dev.jagt.gradle.compileall;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.compile.AbstractCompile;

/** The `compileAll` task: every compile task of every source set of every project. */
public final class CompileAll {

  public static final String NAME = "compileAll";

  private CompileAll() {}

  public static Task registerOn(Project root) {
    return root.getTasks()
        .register(
            NAME,
            task -> {
              task.setGroup("verification");
              task.setDescription("Compiles every source set of every module.");
              for (Project project : root.getAllprojects()) {
                task.dependsOn(project.getTasks().withType(AbstractCompile.class));
              }
            })
        .get();
  }
}
