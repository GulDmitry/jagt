package dev.jagt.orchestrator.service.log;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BoardUrl {

  public static final Set<String> LOOPBACK = Set.of("127.0.0.1", "localhost", "[::1]", "::1");

  private final String root;
  private final String host;

  public BoardUrl(
      @Value("${server.port:8290}") String port, @Value("${server.publicUrl:}") String publicUrl) {
    String given = publicUrl.trim();
    this.root = given.isEmpty() ? "http://localhost:" + port.trim() : given.replaceAll("/+$", "");
    this.host = hostOf(root);
  }

  private static String hostOf(String url) {
    try {
      String host = URI.create(url).getHost();
      return host == null ? "" : host;
    } catch (IllegalArgumentException unparsable) {
      return "";
    }
  }

  public String root() {
    return root;
  }

  public String host() {
    return host;
  }

  public boolean reachedByName() {
    return !host.isEmpty() && !LOOPBACK.contains(host);
  }

  public String ofTask(String taskId) {
    return root + "/?task=" + URLEncoder.encode(taskId, StandardCharsets.UTF_8);
  }
}
