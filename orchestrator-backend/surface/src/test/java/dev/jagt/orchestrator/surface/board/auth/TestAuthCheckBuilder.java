package dev.jagt.orchestrator.surface.board.auth;

import dev.jagt.orchestrator.service.log.BoardUrl;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import lombok.With;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.mock.env.MockEnvironment;

@With
public record TestAuthCheckBuilder(
    AuthProperties.Mode mode,
    @Nullable String passwordHash,
    List<String> allowedHosts,
    String address,
    OidcProperties oidc,
    Map<String, String> environment) {

  public static TestAuthCheckBuilder authCheck() {
    return new TestAuthCheckBuilder(
        AuthProperties.Mode.NONE,
        null,
        List.of(),
        "127.0.0.1",
        new OidcProperties(null, null, null, null),
        Map.of());
  }

  public AuthCheck build(BoardUrl boardUrl) {
    ServerProperties server = new ServerProperties();
    server.setAddress(InetAddress.ofLiteral(address));
    MockEnvironment env = new MockEnvironment();
    environment.forEach(env::setProperty);
    return new AuthCheck(
        new AuthProperties(mode, passwordHash, allowedHosts), oidc, env, server, boardUrl);
  }
}
