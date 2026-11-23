package dev.jagt.gradle.checkscope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.diffplug.gradle.spotless.SpotlessExtension;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckScopeTest {

  @Test
  void reportsNothingWhenSourceSetHasEveryCheck(@TempDir File dir) throws IOException {
    Files.createDirectories(Path.of(dir.getPath(), "src", "main", "java"));
    Files.writeString(Path.of(dir.getPath(), "src", "main", "java", "A.java"), "class A {}");
    Project project = ProjectBuilder.builder().withProjectDir(dir).build();
    project.getPlugins().apply("java");
    project.getPlugins().apply("checkstyle");
    project.getPlugins().apply("com.diffplug.spotless");
    project
        .getExtensions()
        .getByType(SpotlessExtension.class)
        .java(java -> java.target("src/**/*.java"));
    project
        .getTasks()
        .withType(JavaCompile.class)
        .configureEach(
            compile ->
                compile
                    .getOptions()
                    .getCompilerArgumentProviders()
                    .add(() -> List.of("-Xplugin:ErrorProne -XepOpt:X=Y -Xep:NullAway:ERROR")));
    project
        .getTasks()
        .named("test")
        .configure(test -> test.dependsOn(project.getTasks().named("checkstyleMain")));
    ((ProjectInternal) project).evaluate();

    assertThat(CheckScope.gaps(project)).isEmpty();
  }

  @Test
  void reportsEachCheckSourceSetWithSourcesLacks(@TempDir File dir) throws IOException {
    Files.createDirectories(Path.of(dir.getPath(), "src", "main", "java"));
    Project project = ProjectBuilder.builder().withProjectDir(dir).build();
    project.getPlugins().apply("java");
    ((ProjectInternal) project).evaluate();

    assertThat(CheckScope.gaps(project))
        .containsExactly(
            ": main: checkstyle",
            ": main: checkstyle gated by test",
            ": main: errorprone",
            ": main: google-java-format");
  }

  @Test
  void reportsNullAwayBelowErrorWhenErrorProneRuns(@TempDir File dir) throws IOException {
    Files.createDirectories(Path.of(dir.getPath(), "src", "main", "java"));
    Project project = ProjectBuilder.builder().withProjectDir(dir).build();
    project.getPlugins().apply("java");
    project
        .getTasks()
        .withType(JavaCompile.class)
        .configureEach(
            compile ->
                compile
                    .getOptions()
                    .getCompilerArgumentProviders()
                    .add(() -> List.of("-Xplugin:ErrorProne -Xep:NullAway:WARN")));
    ((ProjectInternal) project).evaluate();

    assertThat(CheckScope.gaps(project)).contains(": main: nullaway at error");
  }

  @Test
  void ignoresSourceSetWithoutSources(@TempDir File dir) {
    Project project = ProjectBuilder.builder().withProjectDir(dir).build();
    project.getPlugins().apply("java");
    ((ProjectInternal) project).evaluate();

    assertThat(CheckScope.gaps(project)).isEmpty();
  }

  @Test
  void failsNamingEveryGap() {
    assertThatThrownBy(() -> CheckScope.verify(List.of(":core boardTest: checkstyle")))
        .isInstanceOf(GradleException.class)
        .hasMessageContaining(":core boardTest: checkstyle");
  }

  @Test
  void passesWithoutGaps() {
    CheckScope.verify(List.of());
  }

  @Test
  void isRegisteredUnderItsName() {
    Project root = ProjectBuilder.builder().build();

    CheckScope.registerOn(root);

    Task task = root.getTasks().getByName("checkScope");
    assertThat(task.getGroup()).isEqualTo("verification");
  }
}
