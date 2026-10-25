package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class StandSlotsTest {

    @Test
    void theRunPastTheCapWaitsUntilOneIsFreed() throws Exception {
        StandSlots slots = new StandSlots(2);
        slots.take();
        slots.take();
        CountDownLatch started = new CountDownLatch(1);
        Thread third = Thread.ofVirtual().start(() -> {
            slots.take();
            started.countDown();
        });

        boolean startedWhileFull = started.await(300, TimeUnit.MILLISECONDS);
        slots.free();
        boolean startedAfterFree = started.await(5, TimeUnit.SECONDS);
        third.join();

        assertThat(startedWhileFull).isFalse();
        assertThat(startedAfterFree).isTrue();
    }
}
