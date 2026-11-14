package dev.jagt.orchestrator;

import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class NamedClasses {

  private static final Pattern NAME =
      Pattern.compile("\"(dev\\.jagt\\.orchestrator\\.[\\w.$]*[\\w$])\"");

  private NamedClasses() {}

  static Set<String> in(Path source) {
    return NAME.matcher(DocFiles.read(source))
        .results()
        .map(match -> match.group(1))
        .collect(Collectors.toSet());
  }
}
