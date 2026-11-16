package dev.jagt.orchestrator.surface.board.auth.attempts;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Failed password attempts per peer address, in memory; the least recently seen peer is dropped.
 */
@Component
public class LoginAttempts {

  private record Attempt(int failures, long lastAt, long lockedUntil) {}

  private final LoginBackoff backoff;
  private final Clock clock;
  private final Map<String, Attempt> peers;

  public LoginAttempts(LoginBackoff backoff, Clock clock) {
    this.backoff = backoff;
    this.clock = clock;
    this.peers =
        new LinkedHashMap<>(16, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<String, Attempt> eldest) {
            return size() > backoff.peers();
          }
        };
  }

  public synchronized Duration remaining(String peer) {
    Attempt attempt = peers.get(peer);
    long left = attempt == null ? 0 : attempt.lockedUntil() - clock.millis();
    return left > 0 ? Duration.ofMillis(left) : Duration.ZERO;
  }

  public synchronized void failed(String peer) {
    long now = clock.millis();
    Attempt before = peers.get(peer);
    int failures =
        before == null || now - before.lastAt() >= backoff.duration().toMillis()
            ? 1
            : before.failures() + 1;
    long lockedUntil = failures >= backoff.failures() ? now + backoff.duration().toMillis() : 0;
    peers.put(peer, new Attempt(failures, now, lockedUntil));
  }

  public synchronized void succeeded(String peer) {
    peers.remove(peer);
  }
}
