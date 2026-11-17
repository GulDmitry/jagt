package dev.jagt.orchestrator.surface.board.terminal;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Every origin is let through here because the filters before it have judged the Origin against the
 * board's own and its listed hosts, and Spring's same-origin test would refuse a name an ingress
 * forwards under.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class SessionSocketRoute implements WebSocketConfigurer {

  private final SessionSocket socket;

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(socket, SessionSocket.PATH).setAllowedOriginPatterns("*");
  }
}
