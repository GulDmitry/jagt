package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.surface.board.auth.AuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SessionController {

  public record Session(boolean login) {}

  private final AuthProperties auth;

  @GetMapping("/session")
  public Session session() {
    return new Session(auth.mode() != AuthProperties.Mode.NONE);
  }
}
