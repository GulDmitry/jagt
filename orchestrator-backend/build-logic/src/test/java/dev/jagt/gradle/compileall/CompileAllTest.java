package dev.jagt.gradle.compileall;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

class CompileAllTest {

  @Test
  void dependsOnTheCompileTaskOfEverySourceSetOfEveryModule() {
    Project root = ProjectBuilder.builder().build();
    Project module = ProjectBuilder.builder().withName("core").withParent(root).build();
    for (Project project : List.of(root, module)) {
      project.getPlugins().apply("java");
      project
          .getExtensions()
          .getByType(JavaPluginExtension.class)
          .getSourceSets()
          .create("boardTest");
    }

    Task compileAll = CompileAll.registerOn(root);

    Set<String> compiled =
        compileAll.getTaskDependencies().getDependencies(compileAll).stream()
            .map(Task::getPath)
            .collect(Collectors.toSet());
    assertThat(compiled)
        .containsExactlyInAnyOrder(
            ":compileJava",
            ":compileTestJava",
            ":compileBoardTestJava",
            ":core:compileJava",
            ":core:compileTestJava",
            ":core:compileBoardTestJava");
  }

  @Test
  void isRegisteredUnderItsName() {
    Project root = ProjectBuilder.builder().build();

    CompileAll.registerOn(root);

    assertThat(root.getTasks().getNames()).contains("compileAll");
  }
}
