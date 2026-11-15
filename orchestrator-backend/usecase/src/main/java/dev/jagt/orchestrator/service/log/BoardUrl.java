package dev.jagt.orchestrator.service.log;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BoardUrl {

  private final String root;

  public BoardUrl(
      @Value("${server.port:8290}") String port, @Value("${server.publicUrl:}") String publicUrl) {
    String given = publicUrl.trim();
    this.root = given.isEmpty() ? "http://localhost:" + port.trim() : given.replaceAll("/+$", "");
  }

  public String root() {
    return root;
  }

  public String ofTask(String taskId) {
    return root + "/?task=" + URLEncoder.encode(taskId, StandardCharsets.UTF_8);
  }
}
