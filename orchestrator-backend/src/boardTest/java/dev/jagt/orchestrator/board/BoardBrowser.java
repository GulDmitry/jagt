package dev.jagt.orchestrator.board;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.CDPSession;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

final class BoardBrowser implements ParameterResolver, AfterEachCallback {

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

  private static final class Covered implements AutoCloseable {

    private static final String DIRECTORY_PROPERTY = "board.coverage.dir";

    private final Map<Page, CDPSession> sessions = new ConcurrentHashMap<>();
    private final Path boardRoot = Path.of("..", "board").toAbsolutePath().normalize();
    private final BrowserContext context;

    Covered(Browser browser) {
      this.context = browser.newContext();
      context.onPage(this::start);
    }

    private void start(Page page) {
      CDPSession cdp = page.context().newCDPSession(page);
      cdp.send("Profiler.enable");
      JsonObject options = new JsonObject();
      options.addProperty("callCount", true);
      options.addProperty("detailed", true);
      cdp.send("Profiler.startPreciseCoverage", options);
      sessions.put(page, cdp);
    }

    @Override
    public void close() {
      String directory = System.getProperty(DIRECTORY_PROPERTY);
      if (directory != null) {
        write(Path.of(directory));
      }
      context.close();
    }

    private void write(Path folder) {
      JsonArray scripts = new JsonArray();
      sessions.forEach((page, cdp) -> {
        if (!page.isClosed()) {
          for (JsonElement script :
              cdp.send("Profiler.takePreciseCoverage").getAsJsonArray("result")) {
            keepBoardScript(script.getAsJsonObject(), scripts);
          }
        }
      });
      if (scripts.isEmpty()) {
        return;
      }
      JsonObject dump = new JsonObject();
      dump.add("result", scripts);
      try {
        Files.createDirectories(folder);
        Files.writeString(
            folder.resolve("coverage-" + UUID.randomUUID() + ".json"), dump.toString());
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    private void keepBoardScript(JsonObject script, JsonArray into) {
      String raw = script.get("url").getAsString();
      URI url = URI.create(raw);
      if ("localhost".equals(url.getHost())
          && raw.endsWith(".js")
          && !url.getPath().startsWith("/vendor/")) {
        script.addProperty("url", boardRoot.resolve(url.getPath().substring(1)).toUri().toString());
        into.add(script);
      }
    }
  }

  @Override
  public void afterEach(ExtensionContext context) {
    Covered covered = context.getStore(BROWSERS).remove(Covered.class, Covered.class);
    if (covered != null) {
      covered.close();
    }
  }

  @Override
  public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
    Class<?> type = parameter.getParameter().getType();
    return type == Browser.class || type == BrowserContext.class;
  }

  @Override
  public Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
    Browser browser =
        context
            .getRoot()
            .getStore(BROWSERS)
            .computeIfAbsent(Launched.class, key -> new Launched(), Launched.class)
            .browser;
    if (parameter.getParameter().getType() == Browser.class) {
      return browser;
    }
    return context
        .getStore(BROWSERS)
        .computeIfAbsent(Covered.class, key -> new Covered(browser), Covered.class)
        .context;
  }
}
