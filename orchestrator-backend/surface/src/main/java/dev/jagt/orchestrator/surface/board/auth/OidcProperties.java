package dev.jagt.orchestrator.surface.board.auth;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orchestrator.auth.oidc")
public record OidcProperties(
    @Nullable String issuerUri,
    @Nullable String clientId,
    @Nullable String clientSecretEnv,
    @Nullable String allowed) {

  @Override
  public String toString() {
    return "OidcProperties[issuerUri=" + issuerUri + ", clientId=" + clientId + "]";
  }
}
