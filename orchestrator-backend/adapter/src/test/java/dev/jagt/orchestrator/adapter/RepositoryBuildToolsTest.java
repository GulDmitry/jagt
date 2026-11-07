package dev.jagt.orchestrator.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RepositoryBuildToolsTest {

  @TempDir Path repo;

  @Test
  void suggestsTheGradleWrapperWhereTheRepositoryCarriesOne() throws Exception {
    Files.writeString(repo.resolve("gradlew"), "");

    assertThat(new RepositoryBuildTools().testCommand(repo)).containsExactly("./gradlew", "test");
  }

  @Test
  void suggestsNpmWhereThereIsAPackageFile() throws Exception {
    Files.writeString(repo.resolve("package.json"), "{}");

    assertThat(new RepositoryBuildTools().testCommand(repo)).containsExactly("npm", "test");
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "mvnw|#!/bin/sh|./mvnw test",
        "Makefile|test:\\n\\tgo test ./...|make test",
        "Makefile|build:\\n\\tgo build|''"
      })
  void suggestsTheVerifyCommandTheRepositoryItselfCarries(
      String file, String content, String expected) throws Exception {
    Files.writeString(repo.resolve(file), content.replace("\\n", "\n").replace("\\t", "\t"));

    assertThat(String.join(" ", new RepositoryBuildTools().testCommand(repo))).isEqualTo(expected);
  }

  @Test
  void suggestsNothingWhereTheRepositoryCarriesNoBuildFile() {
    assertThat(new RepositoryBuildTools().testCommand(repo)).isEmpty();
  }
}
