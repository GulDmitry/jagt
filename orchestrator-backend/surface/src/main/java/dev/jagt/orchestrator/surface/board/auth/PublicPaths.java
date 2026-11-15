package dev.jagt.orchestrator.surface.board.auth;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

record PublicPaths(String loginPage, List<String> patterns) {

  private static final String CONTRACT = "contracts/openapi.yaml";

  static PublicPaths fromContract() {
    try (InputStream in = new ClassPathResource(CONTRACT).getInputStream()) {
      Map<String, Object> contract = new Yaml().load(in);
      List<?> paths = (List<?>) Objects.requireNonNull(contract.get("x-public-paths"));
      return new PublicPaths(
          (String) Objects.requireNonNull(contract.get("x-login-page")),
          paths.stream().map(String::valueOf).toList());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
