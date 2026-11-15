package dev.jagt.orchestrator.config.source;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.testing.FakeConfigInputs;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigResolverTest {

  @Test
  void variableWinsOverTheKeyTheFileWritesAtTheDocumentRoot() {
    FakeConfigInputs inputs =
        new FakeConfigInputs().setting("JAGT__server__publicUrl", "https://jagt.example.org");
    Map<String, Object> document =
        Map.of("server", Map.of("port", 8290, "publicUrl", "http://localhost:8290"));

    Object resolved = new ConfigResolver(inputs).resolve(document).document();

    assertThat(resolved)
        .isEqualTo(Map.of("server", Map.of("port", 8290, "publicUrl", "https://jagt.example.org")));
  }

  @Test
  void variableCreatesTheKeyTheFileDoesNotWrite() {
    FakeConfigInputs inputs =
        new FakeConfigInputs().setting("JAGT__server__publicUrl", "https://jagt.example.org");

    Object resolved = new ConfigResolver(inputs).resolve(null).document();

    assertThat(resolved)
        .isEqualTo(Map.of("server", Map.of("publicUrl", "https://jagt.example.org")));
  }

  @Test
  void expandsPlaceholdersInsideListAndInsideLongerString() {
    FakeConfigInputs inputs = new FakeConfigInputs().setting("HOST", "example.org");
    Map<String, Object> document = Map.of("allowedHosts", List.of("a.${HOST}", "plain"));

    Object resolved = new ConfigResolver(inputs).resolve(document).document();

    assertThat(resolved).isEqualTo(Map.of("allowedHosts", List.of("a.example.org", "plain")));
  }

  @Test
  void ignoresJagtVariableWithEmptyLevel() {
    FakeConfigInputs inputs = new FakeConfigInputs().setting("JAGT____x", "1");

    Object resolved = new ConfigResolver(inputs).resolve(Map.of("a", "b")).document();

    assertThat(resolved).isEqualTo(Map.of("a", "b"));
  }
}
