package fitness.core.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fitness.core.FitnessConfigException;
import fitness.core.Reason;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ServiceConfigurationError;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EvaluationProvidersTest {

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "fitness.core.Engine | absent.Engine | java.util.ServiceConfigurationError",
        "fitness.core.Fitness | fitness.core.BrokenFitness | java.lang.IllegalStateException"
      })
  void aProviderThatCannotBeLoadedFailsAsAProviderFailureNamingTheServiceAndCarryingTheCause(
      Class<?> service, String provider, Class<?> deepest, @TempDir Path directory) throws IOException {
    Path services = Files.createDirectories(directory.resolve("META-INF/services"));
    Files.writeString(services.resolve(service.getName()), provider + "\n");
    Path file = Files.writeString(directory.resolve("fitness.yml"), "schema: 1\n");
    Thread thread = Thread.currentThread();
    ClassLoader before = thread.getContextClassLoader();

    try (URLClassLoader loader = new URLClassLoader(new URL[] {directory.toUri().toURL()}, before)) {
      thread.setContextClassLoader(loader);
      assertThatThrownBy(() -> Evaluation.load(file))
          .isInstanceOfSatisfying(
              FitnessConfigException.class,
              thrown -> {
                assertThat(thrown.reason()).isEqualTo(Reason.PROVIDER_FAILED);
                assertThat(thrown.key()).isEqualTo(service.getName());
                assertThat(thrown)
                    .hasCauseInstanceOf(ServiceConfigurationError.class)
                    .hasStackTraceContaining(deepest.getName());
              });
    } finally {
      thread.setContextClassLoader(before);
    }
  }
}
