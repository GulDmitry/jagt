package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.port.JobFaults;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Books each handled failure against the run it happened in, for {@link Jobs} to report. */
@Component
public class JobFaultBook implements JobFaults {

  /** The first handled failure of one run. */
  static final class Booking {
    private @Nullable String cause;

    synchronized void book(String failure) {
      if (cause == null) {
        cause = failure;
      }
    }

    synchronized @Nullable String cause() {
      return cause;
    }
  }

  // A singleton bean: its one instance holds the one ThreadLocal.
  @SuppressWarnings("ThreadLocalUsage")
  private final ThreadLocal<Booking> running = new ThreadLocal<>();

  void during(Booking booking, Runnable work) {
    Booking previous = running.get();
    running.set(booking);
    try {
      work.run();
    } finally {
      if (previous == null) {
        running.remove();
      } else {
        running.set(previous);
      }
    }
  }

  @Override
  public void faulted(String cause) {
    Booking booking = running.get();
    if (booking != null) {
      booking.book(cause);
    }
  }

  @Override
  public Runnable carry(Runnable work) {
    Booking booking = running.get();
    return booking == null ? work : () -> during(booking, work);
  }
}
