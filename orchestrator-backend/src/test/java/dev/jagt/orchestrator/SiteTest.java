package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class SiteTest {

  private static final Path SITE = Path.of("../site");
  private static final String REPOSITORY = "https://github.com/GulDmitry/jagt";
  private static final String BLOB = REPOSITORY + "/blob/main/";
  private static final Path FAVICON = Path.of("../board/favicon.svg");
  private static final Pattern NOT_SOURCED_BY_KIND =
      Pattern.compile(
          "(?s)<head>.*?</head>|<nav>.*?</nav>|<h[12][^>]*>.*?</h[12]>"
              + "|<a class=\"button[^>]*>.*?</a>|<span class=\"prompt\">.*?</span>");
  private static final Pattern TARGET = Pattern.compile("(?:href|src)=\"([^\"]*)\"");
  private static final Pattern LOADED_FROM_OUTSIDE =
      Pattern.compile(
          "src=\"(?:https?:)?//|<link[^>]*href=\"(?:https?:)?//"
              + "|url\\(\\s*[\"']?(?:https?:)?//|@import");

  @Test
  void everyLinkOfEveryPageReachesFileOfRepositoryOrItsGithubUrl() {
    var pages = DocFiles.contentsIn(SITE, ".html");

    List<String> broken =
        pages.entrySet().stream()
            .flatMap(page ->
                TARGET
                    .matcher(page.getValue())
                    .results()
                    .map(link -> link.group(1).replaceFirst("#.*", ""))
                    .filter(target -> !target.isEmpty())
                    .filter(target -> !target.equals(REPOSITORY))
                    .filter(target ->
                        !Files.exists(
                            target.startsWith(BLOB)
                                ? Path.of("..", target.substring(BLOB.length()))
                                : SITE.resolve(target)))
                    .map(target -> page.getKey() + " -> " + target))
            .toList();

    assertThat(pages).isNotEmpty();
    assertThat(broken).isEmpty();
  }

  @Test
  void noPageLoadsAssetFromExternalHost() {
    var pages = DocFiles.contentsIn(SITE, ".html");
    var stylesheets = DocFiles.contentsIn(SITE, ".css");

    List<Path> external =
        Stream.concat(pages.entrySet().stream(), stylesheets.entrySet().stream())
            .filter(file -> LOADED_FROM_OUTSIDE.matcher(file.getValue()).find())
            .map(Map.Entry::getKey)
            .toList();

    assertThat(stylesheets).isNotEmpty();
    assertThat(external).isEmpty();
  }

  @Test
  void everyTextOfThePageIsWordedAsReadmeOrDocs() throws IOException {
    var pages = DocFiles.contentsIn(SITE, ".html");
    String sources =
        (Files.readString(Path.of("../README.md"))
                + String.join(" ", DocFiles.contentsIn(Path.of("../docs"), ".md").values()))
            .replaceAll("\\s+", " ");

    List<String> unsourced =
        pages.values().stream()
            .map(html -> NOT_SOURCED_BY_KIND.matcher(html).replaceAll("<br>"))
            .flatMap(html -> Arrays.stream(html.split("<[^>]*>")))
            .map(text ->
                text.replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replaceAll("\\s+", " ")
                    .trim())
            .filter(text -> !text.isEmpty())
            .filter(text -> !sources.contains(text))
            .toList();

    assertThat(unsourced).isEmpty();
  }

  @Test
  void faviconIsByteIdenticalToTheBoards() throws IOException {
    assertThat(Files.readAllBytes(SITE.resolve("favicon.svg")))
        .isEqualTo(Files.readAllBytes(FAVICON));
  }
}
