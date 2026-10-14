package dev.jagt.orchestrator.port;

/** Keeps the machine from sleeping while held; holding twice or releasing what is not held changes nothing. */
public interface KeepAwake {

    void hold();

    void release();
}
