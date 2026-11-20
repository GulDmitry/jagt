package technical.maintainability.testability;

import dev.jagt.orchestrator.SourceRoots;
import fitness.core.Fitness;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;
import java.nio.file.Path;
import java.util.regex.Pattern;

public final class SuitesTapNobodyFitness implements Fitness {

  private static final Pattern BOOTS = Pattern.compile("(?sm).*^@SpringBootTest.*");
  private static final Pattern MOCKS_THE_NOTIFIER =
      Pattern.compile(
          "(?s).*(@MockitoBean\\s+(private\\s+)?UserNotifier\\b"
              + "|@MockitoBean\\(types\\s*=\\s*\\{[^}]*\\bUserNotifier\\.class).*");

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    return Measurement.of(
        SourceRoots.javaFiles()
            .filter(path -> BOOTS.matcher(SourceRoots.read(path)).matches())
            .filter(path -> !MOCKS_THE_NOTIFIER.matcher(SourceRoots.read(path)).matches())
            .map(Path::getFileName)
            .map(name -> "boots without mocking the desktop notifier: " + name)
            .toList());
  }
}
