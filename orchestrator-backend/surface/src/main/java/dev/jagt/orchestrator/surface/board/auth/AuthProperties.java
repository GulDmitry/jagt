package dev.jagt.orchestrator.surface.board.auth;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "orchestrator.auth")
public record AuthProperties(
    @DefaultValue("none") Mode mode,
    @Nullable String passwordHash,
    @DefaultValue List<String> allowedHosts) {

  public enum Mode {
    NONE,
    PASSWORD,
    OIDC
  }

  @Override
  public String toString() {
    return "AuthProperties[mode=" + mode + "]";
  }
}
