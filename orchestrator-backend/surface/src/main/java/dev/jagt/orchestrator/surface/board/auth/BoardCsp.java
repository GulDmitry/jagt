package dev.jagt.orchestrator.surface.board.auth;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;

/**
 * The one origin the board loads from; vendored libraries inject style, an import map is script.
 */
final class BoardCsp {

  private static final Pattern IMPORT_MAP =
      Pattern.compile("<script type=\"importmap\">(.*?)</script>", Pattern.DOTALL);
  private static final List<String> PAGES = List.of("static/index.html", "static/styleguide.html");
  private static final String EDITOR_MAP = "static/vendor/codemirror/importmap.json";

  private BoardCsp() {}

  static void apply(HeadersConfigurer<HttpSecurity> headers) {
    headers.cacheControl(cache -> cache.disable());
    headers.contentSecurityPolicy(csp -> csp.policyDirectives(policy()));
  }

  static String policy() {
    Set<String> maps = new LinkedHashSet<>();
    PAGES.forEach(page -> maps.add(importMapOf(read(page))));
    maps.add(read(EDITOR_MAP));
    String hashes = maps.stream().map(BoardCsp::hash).collect(Collectors.joining(" "));
    return "default-src 'self'; script-src 'self' "
        + hashes
        + "; style-src 'self' 'unsafe-inline'; img-src 'self' data:; object-src 'none';"
        + " base-uri 'self'; form-action 'self'; frame-ancestors 'none'";
  }

  private static String importMapOf(String page) {
    Matcher found = IMPORT_MAP.matcher(page);
    if (!found.find()) {
      throw new IllegalStateException("no import map in a board page");
    }
    return found.group(1);
  }

  private static String hash(String script) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(script.getBytes(StandardCharsets.UTF_8));
      return "'sha256-" + Base64.getEncoder().encodeToString(digest) + "'";
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String read(String resource) {
    try (InputStream in = new ClassPathResource(resource).getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
