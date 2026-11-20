package fitness.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ServiceConfigurationError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvaluationProvidersTest {

  @Test
  void aProviderNamedInAServicesFileButNotFoundFailsAsAProviderFailureNamingTheService(@TempDir Path directory)
      throws IOException {
    Path services = Files.createDirectories(directory.resolve("META-INF/services"));
    Files.writeString(services.resolve(Engine.class.getName()), "absent.Engine\n");
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
                assertThat(thrown.key()).isEqualTo(Engine.class.getName());
                assertThat(thrown).hasCauseInstanceOf(ServiceConfigurationError.class);
              });
    } finally {
      thread.setContextClassLoader(before);
    }
  }

  @Test
  void aFitnessClassWhoseInitialisationThrowsFailsAsAProviderFailureCarryingTheCause(@TempDir Path directory)
      throws IOException {
    Path services = Files.createDirectories(directory.resolve("META-INF/services"));
    Files.writeString(services.resolve(Fitness.class.getName()), BrokenFitness.class.getName() + "\n");
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
                assertThat(thrown.key()).isEqualTo(Fitness.class.getName());
                assertThat(thrown).hasRootCauseInstanceOf(IllegalStateException.class);
              });
    } finally {
      thread.setContextClassLoader(before);
    }
  }
}
