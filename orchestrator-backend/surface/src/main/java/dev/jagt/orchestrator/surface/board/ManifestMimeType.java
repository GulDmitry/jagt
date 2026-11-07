package dev.jagt.orchestrator.surface.board;

import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory;
import org.springframework.stereotype.Component;

/**
 * Tomcat knows no `.webmanifest`, and a browser installs the board only from
 * `application/manifest+json`.
 */
@Component
public class ManifestMimeType
    implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> {

  @Override
  public void customize(ConfigurableServletWebServerFactory factory) {
    factory.getSettings().getMimeMappings().add("webmanifest", "application/manifest+json");
  }
}
