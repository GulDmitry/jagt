package dev.jagt.orchestrator.eval;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public final class ShippedRoot {

  private static final Path SHIPPED = Path.of("..");

  private ShippedRoot() {}

  public static void copyInto(Path root) throws IOException {
    try (var dists = Files.newDirectoryStream(SHIPPED, "*.dist")) {
      for (Path file : dists) {
        copy(file, root.resolve(file.getFileName()));
      }
    }
    Path roles = SHIPPED.resolve("roles");
    List<Path> files;
    try (var walk = Files.walk(roles)) {
      files =
          walk.filter(Files::isRegularFile)
              .filter(file -> shipped(roles.relativize(file)))
              .toList();
    }
    for (Path file : files) {
      copy(file, root.resolve("roles").resolve(roles.relativize(file).toString()));
    }
  }

  private static boolean shipped(Path inRoles) {
    return inRoles.getFileName().toString().endsWith(".dist")
        || inRoles.getNameCount() > 2 && "eval".equals(inRoles.getName(1).toString());
  }

  private static void copy(Path file, Path target) throws IOException {
    Files.createDirectories(target.getParent());
    Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
  }
}
