package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.port.ReadScope;
import java.util.concurrent.ScheduledFuture;
import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Every run it fires reads the windows once, whatever the job asks of them. */
public class TickScheduler extends ThreadPoolTaskScheduler {

  private final transient ReadScope reads;

  public TickScheduler(ReadScope reads) {
    this.reads = reads;
  }

  @Override
  public @Nullable ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
    return super.schedule(() -> reads.once(task), trigger);
  }
}
