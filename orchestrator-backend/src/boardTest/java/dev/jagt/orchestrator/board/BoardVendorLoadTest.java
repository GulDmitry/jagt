package dev.jagt.orchestrator.board;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Page;
import dev.jagt.orchestrator.service.StateService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BoardVendorLoadTest extends BoardPageContext {

  @Autowired
  BoardVendorLoadTest(BreakableJob breakable, StateService state) {
    super(breakable, state);
  }

  @Test
  void firstPaintFetchesNoCodeEditorFile() {
    Page page = session.newPage();
    List<String> fetched = new ArrayList<>();
    page.onRequest(request -> fetched.add(request.url()));

    page.navigate("http://localhost:" + port + "/");
    page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);

    assertThat(fetched).isNotEmpty().noneMatch(url -> url.contains("/vendor/codemirror/"));
  }

  @Test
  void codeEditorAndGrammarLoadOnDemandWithoutMissingFile() {
    Page page = session.newPage();
    List<String> missing = new ArrayList<>();
    page.onResponse(response -> {
      if (response.status() >= 400) {
        missing.add(response.url());
      }
    });
    page.navigate("http://localhost:" + port + "/");

    Object loaded =
        page.evaluate(
            "async () => {"
                + " const map = document.createElement('script');"
                + " map.type = 'importmap';"
                + " map.textContent ="
                + "   await (await fetch('./vendor/codemirror/importmap.json')).text();"
                + " document.head.append(map);"
                + " const view = await import('@codemirror/view');"
                + " const java = await import('@codemirror/lang-java');"
                + " return typeof view.EditorView + ' ' + typeof java.java;"
                + "}");

    assertThat(loaded).isEqualTo("function function");
    assertThat(missing).noneMatch(url -> url.contains("/vendor/codemirror/"));
  }
}
