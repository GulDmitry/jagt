package dev.jagt.orchestrator.board;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

final class BoardBrowser implements ParameterResolver {

  private static final ExtensionContext.Namespace BROWSERS =
      ExtensionContext.Namespace.create(BoardBrowser.class);

  private static final class Launched implements AutoCloseable {

    private final Playwright playwright = Playwright.create();
    private final Browser browser = playwright.chromium().launch();

    @Override
    public void close() {
      browser.close();
      playwright.close();
    }
  }

  @Override
  public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
    return parameter.getParameter().getType() == Browser.class;
  }

  @Override
  public Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
    return context
        .getRoot()
        .getStore(BROWSERS)
        .computeIfAbsent(Launched.class, key -> new Launched(), Launched.class)
        .browser;
  }
}
