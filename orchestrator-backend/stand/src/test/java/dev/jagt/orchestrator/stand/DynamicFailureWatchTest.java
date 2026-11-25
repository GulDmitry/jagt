package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.DynamicTestInvocationContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor.Invocation;
import org.opentest4j.TestAbortedException;

class DynamicFailureWatchTest {

  private final DynamicTestInvocationContext dynamic = mock(DynamicTestInvocationContext.class);
  private final ExtensionContext context = mock(ExtensionContext.class);

  @Test
  void reportsFailedAndRethrowsWhenOneDynamicTestFails() {
    DynamicFailureWatch watch = new DynamicFailureWatch();
    Invocation<@Nullable Void> failing =
        () -> {
          throw new AssertionError("boom");
        };

    assertThatThrownBy(() -> watch.interceptDynamicTest(failing, dynamic, context))
        .isInstanceOf(AssertionError.class);
    assertThat(watch.failed()).isTrue();
  }

  @Test
  void reportsNotFailedWhenOneDynamicTestPasses() throws Throwable {
    DynamicFailureWatch watch = new DynamicFailureWatch();
    Invocation<@Nullable Void> passing = () -> null;

    watch.interceptDynamicTest(passing, dynamic, context);

    assertThat(watch.failed()).isFalse();
  }

  @Test
  void reportsNotFailedAndRethrowsWhenOneDynamicTestIsSkipped() {
    DynamicFailureWatch watch = new DynamicFailureWatch();
    Invocation<@Nullable Void> skipped =
        () -> {
          throw new TestAbortedException("budgets are off");
        };

    assertThatThrownBy(() -> watch.interceptDynamicTest(skipped, dynamic, context))
        .isInstanceOf(TestAbortedException.class);
    assertThat(watch.failed()).isFalse();
  }
}
