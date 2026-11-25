package dev.jagt.orchestrator.config.home;

import dev.jagt.orchestrator.service.release.Versions;
import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

/**
 * A jar running from {@code <root>/versions/<version>/jagt.jar}, the layout the installers lay out.
 */
public record InstalledJar(String version, Path versions, Path root) {

  public static Optional<InstalledJar> at(Path jar) {
    Path folder = jar.getParent();
    Path versions = folder == null ? null : folder.getParent();
    Path root = versions == null ? null : versions.getParent();
    if (folder == null
        || versions == null
        || root == null
        || !Versions.JAR.equals(String.valueOf(jar.getFileName()))
        || !Versions.DIR.equals(String.valueOf(versions.getFileName()))) {
      return Optional.empty();
    }
    return Optional.of(new InstalledJar(String.valueOf(folder.getFileName()), versions, root));
  }

  public static Optional<InstalledJar> onClassPath(String classPath) {
    return classPath.isBlank() || classPath.contains(File.pathSeparator)
        ? Optional.empty()
        : at(Path.of(classPath.strip()).toAbsolutePath().normalize());
  }
}
