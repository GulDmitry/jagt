package dev.jagt.orchestrator.port;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Where jagt's own reach of a third party stands, held per driver and host and only until a read
 * succeeds there.
 */
public interface Reach {

  void lost(ReachLost lost);

  /**
   * As {@link #lost(ReachLost)}, ending on the first read of {@link #standing()} where {@code
   * stands} is false.
   */
  void lost(ReachLost lost, BooleanSupplier stands);

  /** A read through {@code driver} on {@code host} succeeded: whatever stood for that pair ends. */
  void reached(String driver, String host);

  List<ReachLost> standing();
}
