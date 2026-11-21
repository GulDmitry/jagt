package fitness.scan.measure;

import fitness.core.FitnessFunction;
import fitness.core.Project;
import fitness.scan.Sized;
import fitness.scan.Sources;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class MockedTypes {

  private static final Pattern A_TEST = Pattern.compile("@(Parameterized)?Test\\b");
  private static final Pattern A_MOCK =
      Pattern.compile(
          "mock\\(\\s*([A-Z]\\w*)\\.class|@Mock(?:itoBean)?\\b[^;]*?\\b([A-Z]\\w*)(?:<[^>]*>)?"
              + "\\s+\\w+\\s*[;=]");

  private MockedTypes() {}

  public static List<Sized> perTest(FitnessFunction function, Project project, List<Path> files) {
    return files.stream().flatMap(MockedTypes::ofFile).distinct().toList();
  }

  private static Stream<Sized> ofFile(Path file) {
    String[] parts = A_TEST.split(Sources.read(file));
    Set<String> shared = mocked(parts[0]);
    return Arrays.stream(parts)
        .skip(1)
        .map(body -> Stream.concat(shared.stream(), mocked(body).stream())
            .collect(Collectors.toCollection(TreeSet::new)))
        .map(mocks -> new Sized(file.getFileName() + " " + mocks, mocks.size()));
  }

  private static Set<String> mocked(String source) {
    return A_MOCK
        .matcher(source)
        .results()
        .map(match -> match.group(1) != null ? match.group(1) : match.group(2))
        .collect(Collectors.toSet());
  }
}
