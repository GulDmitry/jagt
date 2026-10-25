package dev.jagt.orchestrator.stand;

import java.util.concurrent.Semaphore;

public final class StandSlots {

    static final StandSlots LIVE = new StandSlots(Integer.getInteger("stand.parallelism", 6));

    private final Semaphore permits;

    public StandSlots(int count) {
        this.permits = new Semaphore(count);
    }

    public void take() {
        permits.acquireUninterruptibly();
    }

    public void free() {
        permits.release();
    }
}
