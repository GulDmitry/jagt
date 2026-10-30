package dev.jagt.orchestrator.port;

/** Whether a task's agent process runs, as a look at its window just found. */
public interface AgentPresence {

    /** @throws Unread where the look could not tell */
    boolean agentLive(String taskId);

    /** The window could not be read, so neither answer holds. */
    class Unread extends RuntimeException {

        public Unread(String reason) {
            super(reason);
        }
    }
}
