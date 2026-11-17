package dev.jagt.orchestrator.stand;

public enum Restart {
  /** Jagt's JVM dies; the rest of its container, tmux included, lives. */
  JVM,
  /** The container or pod is replaced; its volume stays. */
  UNIT,
  /** A new image starts on the old volume. */
  UPGRADE
}
