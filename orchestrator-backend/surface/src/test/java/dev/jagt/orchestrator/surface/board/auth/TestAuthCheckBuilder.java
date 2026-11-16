package dev.jagt.orchestrator.surface.board.auth;

import dev.jagt.orchestrator.service.log.BoardUrl;
import java.net.InetAddress;
import java.util.List;
import lombok.With;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;

@With
public record TestAuthCheckBuilder(
    AuthProperties.Mode mode,
    @Nullable String passwordHash,
    List<String> allowedHosts,
    String address) {

  public static TestAuthCheckBuilder authCheck() {
    return new TestAuthCheckBuilder(AuthProperties.Mode.NONE, null, List.of(), "127.0.0.1");
  }

  public AuthCheck build(BoardUrl boardUrl) {
    ServerProperties server = new ServerProperties();
    server.setAddress(InetAddress.ofLiteral(address));
    return new AuthCheck(new AuthProperties(mode, passwordHash, allowedHosts), server, boardUrl);
  }
}
