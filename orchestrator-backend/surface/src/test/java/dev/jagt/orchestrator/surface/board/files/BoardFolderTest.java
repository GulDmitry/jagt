package dev.jagt.orchestrator.surface.board.files;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.config.annotation.DelegatingWebMvcConfiguration;

class BoardFolderTest {

  @TempDir Path root;

  @Test
  void servesTheFileFromTheFolderBoardDirNamesRelativeToTheRootAndShowsAnEditOnReload()
      throws Exception {
    Files.createDirectory(root.resolve("my-board"));
    Files.writeString(root.resolve("my-board").resolve("probe.html"), "<p>edited</p>");
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
    GenericWebApplicationContext context =
        new GenericWebApplicationContext(new MockServletContext());
    AnnotationConfigUtils.registerAnnotationConfigProcessors(context);
    context.registerBean(DelegatingWebMvcConfiguration.class);
    context.registerBean(
        BoardFolder.class, () -> new BoardFolder(new BoardProperties("my-board"), paths));
    context.refresh();
    MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();

    mvc.perform(get("/probe.html")).andExpect(content().string("<p>edited</p>"));
    Files.writeString(root.resolve("my-board").resolve("probe.html"), "<p>edited again</p>");
    mvc.perform(get("/probe.html")).andExpect(content().string("<p>edited again</p>"));
  }
}
