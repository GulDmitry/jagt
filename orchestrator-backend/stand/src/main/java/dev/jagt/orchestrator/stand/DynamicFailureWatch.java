package dev.jagt.orchestrator.stand;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.extension.DynamicTestInvocationContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.opentest4j.TestAbortedException;

/** Notes whether a dynamic test failed, a skipped one not; a TestWatcher is never told of those. */
public final class DynamicFailureWatch implements InvocationInterceptor {

  private boolean failed;

  public boolean failed() {
    return failed;
  }

  @Override
  public void interceptDynamicTest(
      Invocation<@Nullable Void> invocation,
      DynamicTestInvocationContext invocationContext,
      ExtensionContext extensionContext)
      throws Throwable {
    try {
      invocation.proceed();
    } catch (TestAbortedException skipped) {
      throw skipped;
    } catch (Throwable thrown) { // records the failure, then rethrows it unchanged
      failed = true;
      throw thrown;
    }
  }
}
