package dev.jagt.orchestrator.config.home;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class InstalledJarTest {

  @Test
  void jarUnderVersionsNamesTheInstallHomeAndItsVersion() {
    InstalledJar installed = InstalledJar.at(Path.of("/h/me/.jagt/versions/0.24.1/jagt.jar")).get();

    assertThat(installed.root()).isEqualTo(Path.of("/h/me/.jagt"));
    assertThat(installed.version()).isEqualTo("0.24.1");
    assertThat(installed.versions()).isEqualTo(Path.of("/h/me/.jagt/versions"));
  }

  @Test
  void jarOutsideVersionsIsNotInstalled() {
    assertThat(InstalledJar.at(Path.of("/work/jagt/build/libs/jagt.jar"))).isEmpty();
  }

  @Test
  void jarNamedOtherwiseUnderVersionsIsNotInstalled() {
    assertThat(InstalledJar.at(Path.of("/h/me/.jagt/versions/0.24.1/jagt-run.jar"))).isEmpty();
  }

  @Test
  void singleInstalledJarOnTheClassPathIsTheRunningOne() {
    InstalledJar installed = InstalledJar.onClassPath("/h/me/.jagt/versions/0.24.1/jagt.jar").get();

    assertThat(installed.root()).isEqualTo(Path.of("/h/me/.jagt"));
  }

  @Test
  void severalClassPathEntriesAreNoInstalledJar() {
    String classPath =
        "/h/me/.jagt/versions/0.24.1/jagt.jar" + File.pathSeparator + "/other/lib.jar";

    assertThat(InstalledJar.onClassPath(classPath)).isEmpty();
  }

  @Test
  void blankClassPathIsNoInstalledJar() {
    assertThat(InstalledJar.onClassPath(" ")).isEmpty();
  }

  @Test
  void jarWithNoGrandparentIsNotInstalled() {
    assertThat(InstalledJar.at(Path.of("/jagt.jar"))).isEmpty();
  }
}
