package dev.jagt.orchestrator.job;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.With;

@With
public record TestRolesRootBuilder(Path root) {

  public static TestRolesRootBuilder rolesRoot() {
    return new TestRolesRootBuilder(Path.of("/state"));
  }

  public Path build() throws IOException {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
    Files.createDirectories(root.resolve("roles/master"));
    Files.copy(
        Path.of("../../roles/master/cases.md.dist"), root.resolve("roles/master/cases.md.dist"));
    return root;
  }
}
