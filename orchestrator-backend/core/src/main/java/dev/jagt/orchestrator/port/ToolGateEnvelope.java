package dev.jagt.orchestrator.port;

import java.util.Map;

/** What a CLI's tool-gate hook calls its tools and how it wants a refusal worded. */
public interface ToolGateEnvelope {

    Kind kindOf(String toolName);

    /** The body that makes the session read {@code reason} instead of running the call. */
    Map<String, Object> denial(String reason);

    enum Kind {
        /** Runs a command line. */
        SHELL,
        /** Writes a file. */
        FILE_WRITE,
        OTHER
    }
}
