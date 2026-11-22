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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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

  static final class Coverage {

    private static final String DIRECTORY_PROPERTY = "board.coverage.dir";

    private final Map<Page, CDPSession> sessions = new ConcurrentHashMap<>();
    private final String origin;
    private final Path boardRoot = Path.of("..", "board").toAbsolutePath().normalize();

    Coverage(BrowserContext context, int port) {
      this.origin = "http://localhost:" + port + "/";
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

    void write() {
      String directory = System.getProperty(DIRECTORY_PROPERTY);
      if (directory == null) {
        return;
      }
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
        Path folder = Files.createDirectories(Path.of(directory));
        Files.writeString(
            folder.resolve("coverage-" + UUID.randomUUID() + ".json"), dump.toString());
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    private void keepBoardScript(JsonObject script, JsonArray into) {
      String url = script.get("url").getAsString();
      if (url.startsWith(origin) && url.endsWith(".js") && !url.contains("/vendor/")) {
        String relative = url.substring(origin.length());
        script.addProperty("url", boardRoot.resolve(relative).toUri().toString());
        into.add(script);
      }
    }
  }
}
