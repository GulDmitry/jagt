package dev.jagt.orchestrator.port;

import java.util.List;

/** Where jagt's own reach of a third party stands, held per driver and host and only until a read succeeds there. */
public interface Reach {

    void lost(ReachLost lost);

    /** A read through {@code driver} on {@code host} succeeded: whatever stood for that pair ends. */
    void reached(String driver, String host);

    List<ReachLost> standing();
}
