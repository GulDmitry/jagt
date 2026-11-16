package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ThreatModelTest {

  private static final String MODEL = DocFiles.read(Path.of("../docs/threat-model.md"));

  @Test
  void everyFilterOfTheWebSurfaceIsNamed() {
    List<String> unnamed =
        SourceRoots.mainJavaFiles()
            .filter(source -> SourceRoots.read(source).contains("extends OncePerRequestFilter"))
            .map(source -> source.getFileName().toString().replace(".java", ""))
            .filter(filter -> !MODEL.contains("`" + filter + "`"))
            .toList();

    assertThat(unnamed).isEmpty();
  }

  @Test
  void everyRoutePrefixOfTheWebSurfaceIsNamed() {
    Pattern classMapping = Pattern.compile("@RequestMapping\\(\\s*(?:value\\s*=\\s*)?\"([^\"]*)\"");
    Pattern methodMapping =
        Pattern.compile("@(?:Get|Post|Put|Delete)Mapping\\(\\s*(?:value\\s*=\\s*)?\"([^\"]*)\"");

    List<String> unnamed =
        SourceRoots.mainJavaFiles()
            .map(SourceRoots::read)
            .filter(source -> source.contains("@RestController"))
            .flatMap(source -> {
              String prefix =
                  classMapping
                      .matcher(source)
                      .results()
                      .map(m -> m.group(1))
                      .findFirst()
                      .orElse("");
              return Stream.concat(
                      Stream.of(""), methodMapping.matcher(source).results().map(m -> m.group(1)))
                  .map(path -> prefix + path);
            })
            .filter(route -> route.startsWith("/"))
            .map(route -> route.replaceFirst("^(/api/[^/{]+|/[^/{]+).*", "$1"))
            .distinct()
            .filter(route -> !MODEL.contains("`" + route))
            .toList();

    assertThat(unnamed).isEmpty();
  }

  @Test
  void everyTestItNamesExists() {
    List<String> existing =
        SourceRoots.javaFiles()
            .map(source -> source.getFileName().toString().replace(".java", ""))
            .toList();

    List<String> missing =
        Pattern.compile("`([A-Z][A-Za-z0-9]*Test)`")
            .matcher(MODEL)
            .results()
            .map(match -> match.group(1))
            .distinct()
            .filter(name -> !existing.contains(name))
            .toList();

    assertThat(missing).isEmpty();
  }
}
