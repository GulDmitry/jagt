package dev.jagt.orchestrator.surface.board;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The board's languages are the catalogs present: the ones in the jar, and `<root>/i18n/` over
 * them.
 */
@RestController
@RequestMapping("/api/languages")
@RequiredArgsConstructor
public class BoardLanguages {

  private static final Pattern CODE = Pattern.compile("[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*");
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final OrchestratorPaths paths;

  @GetMapping
  public List<String> codes() {
    TreeSet<String> codes = new TreeSet<>();
    try {
      Arrays.stream(
              new PathMatchingResourcePatternResolver()
                  .getResources("classpath:static/i18n/*.json"))
          .map(Resource::getFilename)
          .forEach(name -> codes.add(code(name)));
      Path own = paths.root().resolve("i18n");
      if (Files.isDirectory(own)) {
        try (Stream<Path> files = Files.list(own)) {
          files
              .map(file -> file.getFileName().toString())
              .filter(name -> name.endsWith(".json"))
              .map(BoardLanguages::code)
              .filter(code -> CODE.matcher(code).matches())
              .forEach(codes::add);
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return List.copyOf(codes);
  }

  @GetMapping("/{code}")
  public Map<String, String> catalog(@PathVariable("code") String code) {
    if (!CODE.matcher(code).matches()) {
      throw new ResponseStatusException(NOT_FOUND);
    }
    Map<String, String> words = new LinkedHashMap<>();
    try {
      ClassPathResource shipped = new ClassPathResource("static/i18n/" + code + ".json");
      if (shipped.exists()) {
        try (InputStream in = shipped.getInputStream()) {
          words.putAll(JSON.readValue(in, new TypeReference<Map<String, String>>() {}));
        }
      }
      Path own = paths.root().resolve("i18n").resolve(code + ".json");
      if (Files.isRegularFile(own)) {
        words.putAll(
            JSON.readValue(Files.readAllBytes(own), new TypeReference<Map<String, String>>() {}));
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    if (words.isEmpty()) {
      throw new ResponseStatusException(NOT_FOUND);
    }
    return words;
  }

  private static String code(String file) {
    return file.substring(0, file.length() - ".json".length());
  }
}
