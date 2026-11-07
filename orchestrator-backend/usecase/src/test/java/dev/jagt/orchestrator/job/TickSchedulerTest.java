package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.port.SessionHost;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TickSchedulerTest {

    @Test
    void runsEveryScheduledRunWithinOneWindowRead() throws InterruptedException {
        SessionHost sessions = mock(SessionHost.class);
        doAnswer(call -> {
            ((Runnable) call.getArgument(0)).run();
            return null;
        }).when(sessions).readingOnce(any());
        TickScheduler scheduler = new TickScheduler(sessions);
        scheduler.setPoolSize(1);
        scheduler.initialize();
        CountDownLatch ran = new CountDownLatch(1);

        scheduler.schedule(ran::countDown, context -> context.lastActualExecution() == null ? Instant.now() : null);

        assertThat(ran.await(5, TimeUnit.SECONDS)).isTrue();
        verify(sessions).readingOnce(any());
        scheduler.shutdown();
    }
}
