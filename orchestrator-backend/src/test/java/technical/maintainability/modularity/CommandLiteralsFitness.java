package technical.maintainability.modularity;

import dev.jagt.orchestrator.flow.capability.TaskAction;
import fitness.core.Fitness;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class CommandLiteralsFitness implements Fitness {

  private static final Path COMMANDS =
      Path.of("usecase/src/main/java/dev/jagt/orchestrator/command");
  private static final Path SURFACE = Path.of("surface/src/main/java");

  /** Verbs that are also a request field or an HTML tag, which a protocol spells by itself. */
  private static final Set<String> WIRE_WORDS = Set.of("code", "project");

  private static final Pattern GLOBAL_ID =
      Pattern.compile("String id\\(\\)\\s*\\{\\s*return \"([^\"]+)\";");
  private static final Pattern COMMENT = Pattern.compile("/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);
  private static final Pattern LITERAL = Pattern.compile("\"([^\"\\\\\\n]*)\"");

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    Set<String> verbs = verbs(project.root().resolve(COMMANDS));
    return Measurement.of(
        javaFiles(project.root().resolve(SURFACE))
            .flatMap(file -> literalsIn(project.root(), file, verbs))
            .toList());
  }

  private static Set<String> verbs(Path commands) {
    Set<String> verbs = new HashSet<>();
    javaFiles(commands)
        .forEach(
            file -> GLOBAL_ID.matcher(read(file)).results().forEach(id -> verbs.add(id.group(1))));
    Arrays.stream(TaskAction.values())
        .forEach(action -> {
          verbs.add(action.id());
          verbs.addAll(action.retiredVerbs());
        });
    verbs.removeAll(WIRE_WORDS);
    return verbs;
  }

  private static Stream<String> literalsIn(Path root, Path file, Set<String> verbs) {
    String source = withoutComments(read(file));
    return LITERAL
        .matcher(source)
        .results()
        .filter(literal -> verbs.contains(literal.group(1)))
        .map(literal ->
            root.relativize(file)
                + ":"
                + (source.substring(0, literal.start()).chars().filter(c -> c == '\n').count() + 1)
                + " "
                + literal.group());
  }

  private static String withoutComments(String source) {
    return COMMENT
        .matcher(source)
        .replaceAll(comment -> Matcher.quoteReplacement(comment.group().replaceAll("[^\\n]", " ")));
  }

  private static Stream<Path> javaFiles(Path root) {
    try (Stream<Path> walked = Files.walk(root)) {
      return walked.filter(path -> path.toString().endsWith(".java")).sorted().toList().stream();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String read(Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
