package dev.jagt.orchestrator.stand;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.extension.DynamicTestInvocationContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;

/** Notes whether any dynamic test failed; a TestWatcher is never told of those. */
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
    } catch (Throwable thrown) { // records the failure, then rethrows it unchanged
      failed = true;
      throw thrown;
    }
  }
}
