package dev.jagt.orchestrator.surface.board;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class BoardAssetsTest {

  private static final Path STATIC = Path.of("build/resources/main/static");
  private static final Pattern LOADED_FROM_OUTSIDE =
      Pattern.compile(
          "(src|href)=[\"'](https?:)?//|import\\s*\\(?\\s*[\"'](https?:)?//|from\\s+[\"'](https?:"
              + ")?//"
              + "|@import\\s+(url\\()?[\"']?(https?:)?//|url\\(\\s*[\"']?(https?:)?//|\"(https?:)"
              + "?//[^\"]*\\.m?js\"");

  @ParameterizedTest
  @CsvSource({
    "preact.mjs, bdcbd9abdf2c77c5c45326eab42191e949222d479d0d5f69467387a77040cb55",
    "hooks.mjs, e862ab2973aa6455eaa0f77c5d832d2157d6be59a17c8ba546550a575b58c6b4",
    "htm.mjs, ab33dd3f38059b9be4d5f5350128eefb2356639c4e0bbe9d9e8b3ba75847e9e4",
    "mermaid.min.js, 6484afc32872a3aa16cac9a76ba1816a1ed4cc870a6593cc2e17757750f518b2"
  })
  void vendoredLibraryIsTheExactFileTheRegistryServed(String file, String sha256)
      throws IOException, NoSuchAlgorithmException {
    byte[] bytes = Files.readAllBytes(STATIC.resolve("vendor").resolve(file));

    String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));

    assertThat(digest).isEqualTo(sha256);
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = ' ',
      textBlock =
          """
          codemirror-autocomplete.js 0bc143dbdc725b9cd2a550a552b4f3a8cfd057948b274eee9f0689b3eb9ad798
          codemirror-lang-css.js 0a7e271ad65f99f43f34a7169acf67ec3ad016f4c7818904b3e34a5c89f40916
          codemirror-lang-html.js 6f608aaeb92179d873e487060d006a5859fa382de6ceb827935bb9a1f724191f
          codemirror-lang-java.js e0cb6e93fc1a41a016b7634bfcec9bd27d12eb8694f5c394e37dad50dbd68e4c
          codemirror-lang-javascript.js 3f6f84c3139a3e2601e2c81c9b26f80e5bcc4ba2da4e493303c74d948a327400
          codemirror-lang-json.js 5472a597498460db724d830a6852ed643eec267bfac3aab1498e8fd83cfd4e60
          codemirror-lang-markdown.js 55d8cfa46628ca5cc907d1a991ed81c6872b392a17673c0b8cde396100b216b7
          codemirror-lang-python.js c60274624c703cb07b471a2de503b319b222b87e140445c09ce57415130b5eca
          codemirror-lang-yaml.js 6279053de28ed4b4335cc3067638d97807f5dfc8b0fb1cee8de62f7e8efec1d9
          codemirror-language.js 3ee82597686d97904b1c0df409661c2e6386e206b9122ac309622e225a0075ff
          codemirror-merge.js bd0c5387c8fbbf7689b520a44bf34e2a59eac3f3c96ffc5ad0586bea7228ce44
          codemirror-search.js 1155a69fb8d6f56c1d644e3e93222348481d6b2df78d6bebcf3293fe209f3fd7
          codemirror-state.js cf4ffe7d177bf2c7c5da66c49be0811cc79a29e12cb658238876bbdcef12939c
          codemirror-streamparser.js 03b1109bc091c1f70e34abbd6161cd1675280a10ac5d26fffdede446076e4bb6
          codemirror-view.js e1ee658a4f903317aa6a573b993b78c5552307cba4c4b838335e9ef079d82451
          crelt.js 924800defa2bf95a0bfd16dde589ecebbcd9e36b03635274da84968c77a3721e
          lezer-common.js 5afcf283f7ab19b5c1ffaf60efb4984d694c0b62077c8f8897545a98d01ef571
          lezer-css.js 9c2426962e5f4553310e31bc02ff045625dd4dfcb5f9f0bd93c433817871b6f1
          lezer-highlight.js 8984bb2887a6354d3e5ac79bdd7e522948f1cee1dda16aac77d9473f10696d7c
          lezer-html.js fd4c7f475d40e7cb86346c7c18bbaf0902492a995b03cf372adb8b2a33cc8829
          lezer-java.js d940a41bc64d2bca07734af0ae7b0b3374526d1607610194963acdd58fddbe8c
          lezer-javascript.js 7ff6ade8c607be16afb95da5bebf531d14a03e7ba112fd84003bc6329d664b77
          lezer-json.js 3310e7216620dc17c6bdfb1665f774e7b0b29918e761ccc74a7e2a83f039a735
          lezer-lr.js 3c649203d840e7175277239ef43baa3c98aec6c183a770e7d3867006927a3238
          lezer-markdown.js 38706e0e3a27bcc959f8c811aeb2e9d24289565494f35ca6c5742f0425b23733
          lezer-python.js 47135244f8db0253e8cc7c01115f6cb86049a094936209091e6ca32c01258224
          lezer-yaml.js 761bb6fa2f88915f916b33429b8d0f06639efe288b695c8abc6f32c0d116c9c5
          marijn-find-cluster-break.js 4e9e441fac9db54e09e4e3b1cb0b0393da207ebe943f2ff9efc577821fc03751
          style-mod.js 4bd6b34edda52f362553d33df7e3a10f31fbdc0c95125aabec89c4de6be80b0a
          w3c-keyname.js b649dec4f01c7c5eb05be4641b5b698c6938231ee50b38e17a2f0f822ca3df2e
          """)
  void vendoredEditorFileIsTheExactFileTheRegistryServed(String file, String sha256)
      throws IOException, NoSuchAlgorithmException {
    byte[] bytes = Files.readAllBytes(STATIC.resolve("vendor/codemirror").resolve(file));

    String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));

    assertThat(digest).isEqualTo(sha256);
  }

  @ParameterizedTest
  @CsvSource({
    "xterm.mjs, b336ec65a086c056d4804b3d4c2347da5663d3f23c3f25be866467bd8857ad59",
    "addon-fit.mjs, 2d87e1bddc73be9111de8beee5370c3bb7aac9c94e18e6f245f02ca741ef1769",
    "xterm.css, 854a7c0fb70e8b1a083c16797ab827299fb18744f5ad34f227b48337e33293c6"
  })
  void vendoredTerminalFileIsTheExactFileTheRegistryServed(String file, String sha256)
      throws IOException, NoSuchAlgorithmException {
    byte[] bytes = Files.readAllBytes(STATIC.resolve("vendor/xterm").resolve(file));

    String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));

    assertThat(digest).isEqualTo(sha256);
  }

  @Test
  void theBoardInstallsAsStandaloneAppStartingAtItsOwnRoot() throws IOException {
    JsonNode manifest =
        JsonMapper.builder().build().readTree(STATIC.resolve("manifest.webmanifest").toFile());

    assertThat(manifest.path("name").asString()).isEqualTo("jagt");
    assertThat(manifest.path("start_url").asString()).isEqualTo("/");
    assertThat(manifest.path("display").asString()).isEqualTo("standalone");
    assertThat(manifest.path("background_color").asString()).isEqualTo("#f6f7f9");
    assertThat(manifest.path("theme_color").asString()).isEqualTo("#1d4ed8");
  }

  @ParameterizedTest
  @CsvSource({"icon-192.png, 192", "icon-512.png, 512"})
  void theManifestNamesAnIconOfTheSizeItDeclares(String file, int side) throws IOException {
    JsonNode manifest =
        JsonMapper.builder().build().readTree(STATIC.resolve("manifest.webmanifest").toFile());
    BufferedImage icon = ImageIO.read(STATIC.resolve(file).toFile());

    assertThat(manifest.path("icons").valueStream().map(entry -> entry.path("src").asString()))
        .contains(file);
    assertThat(
            manifest
                .path("icons")
                .valueStream()
                .filter(entry -> file.equals(entry.path("src").asString()))
                .map(entry -> entry.path("sizes").asString()))
        .containsExactly(side + "x" + side);
    assertThat(icon.getWidth()).isEqualTo(side);
    assertThat(icon.getHeight()).isEqualTo(side);
  }

  @Test
  void theBoardsPageLinksItsManifest() throws IOException {
    String index = Files.readString(STATIC.resolve("index.html"));

    assertThat(index).contains("<link rel=\"manifest\" href=\"manifest.webmanifest\" />");
  }

  @Test
  void theBoardsCodeAndStylesWeighUnderTwoHundredTwentyFourKilobytes() throws IOException {
    long bytes;
    try (Stream<Path> files = Files.walk(STATIC)) {
      bytes =
          files
              .filter(Files::isRegularFile)
              .filter(path -> path.toString().matches(".*\\.(js|mjs|css|html|svg)$"))
              .filter(path -> !path.endsWith("mermaid.min.js"))
              .filter(path -> !path.endsWith("hooks.mjs"))
              .filter(path -> !path.startsWith(STATIC.resolve("vendor/codemirror")))
              .filter(path -> !path.startsWith(STATIC.resolve("viewer")))
              .filter(path -> !path.startsWith(STATIC.resolve("terminal")))
              .filter(path -> !path.startsWith(STATIC.resolve("vendor/xterm")))
              .mapToLong(path -> path.toFile().length())
              .sum();
    }

    assertThat(bytes).isLessThan(232 * 1024);
  }

  @Test
  void noPageScriptOrStyleLoadsAnythingFromOutsideTheJar() throws IOException {
    List<Path> sources;
    try (Stream<Path> files = Files.walk(STATIC)) {
      sources =
          files
              .filter(Files::isRegularFile)
              .filter(path -> path.toString().matches(".*\\.(js|mjs|css|html)$"))
              .toList();
    }

    assertThat(sources)
        .isNotEmpty()
        .allSatisfy(
            source -> assertThat(source).content().doesNotContainPattern(LOADED_FROM_OUTSIDE));
  }

  @Test
  void noBoardPageRunsAnInlineScriptButItsImportMap() throws IOException {
    List<Path> pages;
    try (Stream<Path> files = Files.walk(STATIC)) {
      pages = files.filter(path -> path.toString().endsWith(".html")).toList();
    }

    assertThat(pages)
        .isNotEmpty()
        .allSatisfy(page ->
            assertThat(page)
                .content()
                .doesNotContainPattern("<script(?![^>]*(src=|type=\"importmap\"))[^>]*>"));
  }

  @Test
  void theJarServesNoTestOfTheBoard() throws IOException {
    List<Path> tests;
    try (Stream<Path> files = Files.walk(STATIC)) {
      tests = files.filter(path -> path.toString().endsWith(".test.js")).toList();
    }

    assertThat(tests).isEmpty();
  }

  @Test
  void noBoardScriptFormatsTimeInTheBrowsersLocaleOrZone() throws IOException {
    List<Path> scripts;
    try (Stream<Path> files = Files.walk(STATIC)) {
      scripts =
          files
              .filter(Files::isRegularFile)
              .filter(path -> path.toString().matches(".*\\.m?js$"))
              .filter(path -> !path.startsWith(STATIC.resolve("vendor")))
              .toList();
    }

    assertThat(scripts)
        .isNotEmpty()
        .allSatisfy(script -> assertThat(script).content().doesNotContain("toLocale"))
        .allSatisfy(script ->
            assertThat(script)
                .content()
                .satisfiesAnyOf(
                    text -> assertThat(text).doesNotContain("Intl.DateTimeFormat"),
                    text -> assertThat(text).contains("timeZone: \"UTC\"")));
  }
}
