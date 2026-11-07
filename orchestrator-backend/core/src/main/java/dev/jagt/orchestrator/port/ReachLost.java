package dev.jagt.orchestrator.port;

import java.util.List;

/**
 * A party jagt cannot reach at all: {@code driver} on {@code host}, what {@code said} so, and the
 * command that {@code fix}es it where one exists, empty otherwise. {@code retryAt} is epoch millis
 * of the next read that probes, zero where none is scheduled.
 */
public record ReachLost(String driver, String host, List<String> fix, String said, long retryAt) {

  public ReachLost(String driver, String host, List<String> fix, String said) {
    this(driver, host, fix, said, 0L);
  }

  public ReachLost {
    fix = fix == null ? List.of() : List.copyOf(fix);
  }

  public String key() {
    return driver + "@" + host;
  }
}
