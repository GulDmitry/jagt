package dev.jagt.orchestrator.surface.mcp;

/** What a call changes, as a client's permission mode reads it. */
public enum Effect {

    /** Reads, or reports to jagt; changes nothing a session works on. */
    REPORTS,
    CHANGES
}
