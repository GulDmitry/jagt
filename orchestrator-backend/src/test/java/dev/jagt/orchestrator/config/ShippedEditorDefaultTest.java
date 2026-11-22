package dev.jagt.orchestrator.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.adapter.terminal.CliEditorDriver;
import java.io.IOException;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

class ShippedEditorDefaultTest {

  @Test
  void launchesNoEditorWhenTheStockConfigIsUsed() throws IOException {
    var yaml =
        new YamlPropertySourceLoader()
            .load("application.yml", new ClassPathResource("application.yml"))
            .getFirst();
    var shipped =
        new Binder(
                Objects.requireNonNull(ConfigurationPropertySources.from(yaml).iterator().next()))
            .bind("orchestrator", ToolchainProperties.class)
            .get();
    ProcessRunner processRunner = mock(ProcessRunner.class);

    CliEditorDriver driver = new CliEditorDriver(processRunner, shipped);

    assertThat(driver.absent()).isTrue();
    assertThat(driver.problems()).isEmpty();
    verifyNoInteractions(processRunner);
  }
}
