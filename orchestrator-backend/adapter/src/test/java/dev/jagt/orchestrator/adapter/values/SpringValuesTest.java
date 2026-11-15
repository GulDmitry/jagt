package dev.jagt.orchestrator.adapter.values;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.testing.FakeConfigInputs;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class SpringValuesTest {

  @Test
  void boundKeyReadsTheFileItNamesWithItsTrailingNewlineCut() {
    StandardEnvironment environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .addLast(
            new MapPropertySource(
                "Config resource 'file [jagt.yml]'",
                Map.of("orchestrator.assistant.token", "${file:/run/secrets/x}")));
    FakeConfigInputs inputs = new FakeConfigInputs().holding("/run/secrets/x", "from-file\n");

    new SpringValues(inputs).apply(environment);

    assertThat(environment.getProperty("orchestrator.assistant.token")).isEqualTo("from-file");
  }

  @Test
  void jagtVariableOutranksTheConfigFileForBoundKey() {
    StandardEnvironment environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .addLast(
            new MapPropertySource(
                "Config resource 'file [jagt.yml]'", Map.of("server.port", "8290")));
    FakeConfigInputs inputs = new FakeConfigInputs().setting("JAGT__server__port", "8391");

    new SpringValues(inputs).apply(environment);

    assertThat(environment.getProperty("server.port")).isEqualTo("8391");
  }

  @Test
  void leavesTheEnvironmentAloneWhenNothingIsWritten() {
    StandardEnvironment environment = new StandardEnvironment();
    int before = environment.getPropertySources().size();

    new SpringValues(new FakeConfigInputs()).apply(environment);

    assertThat(environment.getPropertySources()).hasSize(before);
  }
}
