package dev.jagt.gradle.pitest;

import java.util.Optional;

public final class DiffOwner {

  private static final String BACKEND = "orchestrator-backend/";
  private static final String INCLUDED_BUILD = "build-logic/";

  private DiffOwner() {}

  public static Optional<String> projectOf(String path) {
    String relative = path.startsWith(BACKEND) ? path.substring(BACKEND.length()) : path;
    if (relative.startsWith(INCLUDED_BUILD)) {
      return Optional.empty();
    }
    if (relative.startsWith("src/")) {
      return Optional.of(":");
    }
    return Optional.of(":" + relative.substring(0, relative.indexOf('/')));
  }
}
