package dev.jagt.orchestrator.surface.board.auth.attempts;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** One line per login, whichever way it ended. */
@Slf4j
@Component
public class LoginAudit {

  public void succeeded(String peer, String mode) {
    log.atInfo()
        .setMessage("login succeeded")
        .addKeyValue("peer", peer)
        .addKeyValue("mode", mode)
        .log();
  }

  public void failed(String peer, String mode, String cause) {
    log.atWarn()
        .setMessage("login failed")
        .addKeyValue("peer", peer)
        .addKeyValue("mode", mode)
        .addKeyValue("cause", cause)
        .log();
  }
}
