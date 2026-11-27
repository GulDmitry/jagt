package dev.jagt.orchestrator.job;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JobFaultBookTest {

  private final JobFaultBook book = new JobFaultBook();

  @Test
  void booksNothingOnThreadNoJobRunsOn() {
    JobFaultBook.Booking booking = new JobFaultBook.Booking();

    book.faulted("host down");

    assertThat(booking.cause()).isNull();
  }

  @Test
  void booksTheFirstCauseOfTheRunAndKeepsIt() {
    JobFaultBook.Booking booking = new JobFaultBook.Booking();

    book.during(
        booking,
        () -> {
          book.faulted("host down");
          book.faulted("disk full");
        });

    assertThat(booking.cause()).isEqualTo("host down");
  }

  @Test
  void booksFailureAfterNestedWorkEndedAgainstTheRunItInterrupted() {
    JobFaultBook.Booking outer = new JobFaultBook.Booking();
    JobFaultBook.Booking inner = new JobFaultBook.Booking();

    book.during(
        outer,
        () -> {
          book.during(inner, () -> {});
          book.faulted("host down");
        });

    assertThat(outer.cause()).isEqualTo("host down");
    assertThat(inner.cause()).isNull();
  }

  @Test
  void booksNothingOnceTheRunEnded() {
    JobFaultBook.Booking booking = new JobFaultBook.Booking();
    book.during(booking, () -> {});

    book.faulted("host down");

    assertThat(booking.cause()).isNull();
  }

  @Test
  void booksCarriedWorkAgainstTheRunThatCarriedItEvenOnceTheRunEnded() {
    JobFaultBook.Booking booking = new JobFaultBook.Booking();
    Runnable[] carried = new Runnable[1];
    book.during(booking, () -> carried[0] = book.carry(() -> book.faulted("sweep failed")));

    carried[0].run();

    assertThat(booking.cause()).isEqualTo("sweep failed");
  }
}
